package com.poc.dynamicnotificationbuilder.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.Gson;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationColumn;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationItem;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationLink;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationRow;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.slack.SlackNotification;
import com.poc.dynamicnotificationbuilder.slack.sdk.RawTextTableCell;
import com.poc.dynamicnotificationbuilder.slack.sdk.TableBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.RichTextElement;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.util.json.GsonFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SDK-backed duplicate of {@link SlackCustomSchemaFactory}: same Block Kit output, built with
 * {@link SectionBlock}, {@link RichTextBlock}, and {@link MarkdownTextObject} from slack-api-model.
 * Table blocks use {@link TableBlock} until the Java SDK adds native support.
 */
@Component
public class SlackBlockKitSdkFactory {

    private final ObjectMapper objectMapper;
    private final Gson slackGson;

    public SlackBlockKitSdkFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.slackGson = GsonFactory.createSnakeCase();
    }

    public SlackNotification build(Notification notification) {
        List<LayoutBlock> blocks = new ArrayList<>();
        appendDefaultFields(blocks, notification);

        for (NotificationItem item : notification.items()) {
            appendItem(blocks, item);
        }

        return new SlackNotification(
                notification.patternId(),
                notification.templateId(),
                notification.brand(),
                notification.slackChannels(),
                toArrayNode(blocks)
        );
    }

    public String toJson(Notification notification) {
        SlackNotification payload = build(notification);
        var root = objectMapper.createObjectNode();
        root.set("blocks", payload.blocks());
        return root.toPrettyString();
    }

    private void appendDefaultFields(List<LayoutBlock> blocks, Notification notification) {
        String alertDate = notification.alertDate();
        String patternName = notification.patternName();
        String alertText = notification.patternAlertText();

        if (alertDate != null && !alertDate.isBlank()) {
            blocks.add(sectionBlock(escapeMrkdwn(alertDate)));
        }
        if (patternName != null && !patternName.isBlank()) {
            blocks.add(sectionBlock(escapeMrkdwn(patternName)));
        }
        if (alertText != null && !alertText.isBlank()) {
            blocks.add(sectionBlock(escapeMrkdwn(alertText)));
        }
    }

    private void appendItem(List<LayoutBlock> blocks, NotificationItem item) {
        switch (item) {
            case NotificationField field -> blocks.add(sectionBlock(formatField(field)));
            case NotificationTable table -> blocks.add(tableBlock(table));
            default -> { /* ignore unknown item types */ }
        }
    }

    private SectionBlock sectionBlock(String mrkdwnText) {
        return SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(mrkdwnText).build())
                .build();
    }

    private String formatField(NotificationField field) {
        String label = escapeMrkdwn(field.label());
        String value = escapeMrkdwn(field.value());
        List<NotificationLink> links = field.links() == null ? List.of() : field.links();

        if (links.isEmpty()) {
            return label + ": " + value;
        }
        if (links.size() == 1) {
            NotificationLink link = links.getFirst();
            return label + ": " + formatFieldLinks(link.url(), value);
        }
        StringBuilder sb = new StringBuilder(label).append(": ").append(value);
        for (int i = 0; i < links.size(); i++) {
            NotificationLink link = links.get(i);
            String linkLabel = escapeMrkdwn(link.name());
            if (i == 0) {
                sb.append(" (").append(formatFieldLinks(link.url(), linkLabel));
            } else {
                sb.append(", ").append(formatFieldLinks(link.url(), linkLabel));
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private static String formatFieldLinks(String url, String label) {
        return "<" + url + "|" + label + ">";
    }

    private TableBlock tableBlock(NotificationTable table) {
        List<List<Object>> rows = new ArrayList<>();

        if (!table.rows().isEmpty()) {
            List<Object> headerRow = new ArrayList<>();
            for (NotificationColumn column : table.rows().getFirst().columns()) {
                headerRow.add(rawTextCell(column.label()));
            }
            rows.add(headerRow);
        }

        for (NotificationRow dataRow : table.rows()) {
            List<Object> row = new ArrayList<>();
            for (NotificationColumn column : dataRow.columns()) {
                row.add(formatCellToRichText(column));
            }
            rows.add(row);
        }

        return TableBlock.builder().rows(rows).build();
    }

    private static RawTextTableCell rawTextCell(String text) {
        return RawTextTableCell.builder()
                .text(text == null ? "" : text)
                .build();
    }

    private Object formatCellToRichText(NotificationColumn column) {
        List<NotificationLink> links = column.links() == null ? List.of() : column.links();
        if (links.isEmpty()) {
            return rawTextCell(column.value());
        }
        if (links.size() == 1) {
            NotificationLink link = links.getFirst();
            return richTextSingleLinkCell(column.value(), link.url());
        }
        return richTextMultiLinkCell(column.value(), links);
    }

    private RichTextBlock richTextSingleLinkCell(String displayText, String url) {
        return richTextBlock(List.of(
                richTextSection(List.of(linkElement(displayText, url)))
        ));
    }

    private RichTextBlock richTextMultiLinkCell(String displayText, List<NotificationLink> links) {
        List<BlockElement> sections = new ArrayList<>();

        sections.add(richTextSection(List.of(
                RichTextSectionElement.Text.builder()
                        .text(displayText == null ? "" : displayText)
                        .build()
        )));

        int linkCount = links.size();
        for (int i = 0; i < linkCount; i++) {
            NotificationLink link = links.get(i);
            String linkLabel = multiLinkLabel(i, linkCount, link.name());
            sections.add(richTextSection(List.of(linkElement(linkLabel, link.url()))));
        }

        return richTextBlock(sections);
    }

    private static String multiLinkLabel(int index, int total, String linkName) {
        String name = linkName == null ? "" : linkName;
        if (index == 0) {
            return " (" + name + ",";
        }
        if (index == total - 1) {
            return " " + name + ")";
        }
        return " " + name + ",";
    }

    private static RichTextSectionElement.Link linkElement(String text, String url) {
        return RichTextSectionElement.Link.builder()
                .text(text == null ? "" : text)
                .url(url == null ? "" : url)
                .build();
    }

    private static RichTextSectionElement richTextSection(List<RichTextElement> elements) {
        return RichTextSectionElement.builder().elements(elements).build();
    }

    private static RichTextBlock richTextBlock(List<BlockElement> sections) {
        return RichTextBlock.builder().elements(sections).build();
    }

    private ArrayNode toArrayNode(List<LayoutBlock> blocks) {
        ArrayNode array = objectMapper.createArrayNode();
        for (LayoutBlock block : blocks) {
            try {
                JsonNode node = objectMapper.readTree(slackGson.toJson(block));
                array.add(node);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize Slack block: " + block.getType(), e);
            }
        }
        return array;
    }

    private static String escapeMrkdwn(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
