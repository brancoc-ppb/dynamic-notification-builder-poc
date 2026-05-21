package com.poc.dynamicnotificationbuilder.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationColumn;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationRow;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationItem;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationLink;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.slack.SlackNotification;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps a resolved {@link Notification} to Slack Block Kit JSON.
 * <p>
 * Blocks are built as minimal JSON objects (no null fields) so incoming webhooks accept the payload.
 * Blocks are emitted in the same order as {@link Notification#items()}.
 */
@Component
public class SlackCustomSchemaFactory {

    private final ObjectMapper objectMapper;

    public SlackCustomSchemaFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SlackNotification build(Notification notification) {
        ArrayNode blocks = objectMapper.createArrayNode();
        appendDefaultFields(blocks, notification);

        for (NotificationItem item : notification.items()) {
            appendItem(blocks, item);
        }

        return new SlackNotification(
                notification.patternId(),
                notification.templateId(),
                notification.brand(),
                notification.slackChannels(),
                blocks
        );
    }

    public String toJson(Notification notification) {
        SlackNotification payload = build(notification);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("blocks", payload.blocks());
        return root.toPrettyString();
    }

    private void appendDefaultFields(ArrayNode blocks, Notification notification) {
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

    //appends a field or table item
    private void appendItem(ArrayNode blocks, NotificationItem item) {
        switch (item) {
            case NotificationField field -> blocks.add(sectionBlock(formatField(field)));
            case NotificationTable table -> blocks.add(tableBlock(table));
            default -> { /* ignore unknown item types */ }
        }
    }

    //transforms field object to sections (per Block Kit Builder schema)
    private ObjectNode sectionBlock(String mrkdwnText) {
        ObjectNode text = objectMapper.createObjectNode();
        text.put("type", "mrkdwn");
        text.put("text", mrkdwnText);

        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "section");
        block.set("text", text);
        return block;
    }

    //formats a field's label and value as mrkdwn, appending links if present (per Block Kit Builder schema)
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

    //formats field links as mrkdwn
    private static String formatFieldLinks(String url, String label) {
        return "<" + url + "|" + label + ">";
    }

    //formats table to a table block (per Block Kit Builder schema)
    private ObjectNode tableBlock(NotificationTable table) {
        ArrayNode rows = objectMapper.createArrayNode();

        if (!table.rows().isEmpty()) {
            ArrayNode headerRow = objectMapper.createArrayNode();
            for (NotificationColumn column : table.rows().getFirst().columns()) {
                headerRow.add(formatCellToRawText(column.label()));
            }
            rows.add(headerRow);
        }

        for (NotificationRow dataRow : table.rows()) {
            ArrayNode row = objectMapper.createArrayNode();
            for (NotificationColumn column : dataRow.columns()) {
                row.add(formatCellToRichText(column));
            }
            rows.add(row);
        }

        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "table");
        block.set("rows", rows);
        return block;
    }

    //columns headers and cells without links are rendered as type raw_text
    private ObjectNode formatCellToRawText(String text) {
        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("type", "raw_text");
        cell.put("text", text == null ? "" : text);
        return cell;
    }

    //transforms cells with links as type rich_text
    private ObjectNode formatCellToRichText(NotificationColumn column) {
        List<NotificationLink> links = column.links() == null ? List.of() : column.links();
        if (links.isEmpty()) {
            return formatCellToRawText(column.value());
        }
        if (links.size() == 1) {
            NotificationLink link = links.getFirst();
            return richTextSingleLinkCell(column.value(), link.url());
        }
        return richTextMultiLinkCell(column.value(), links);
    }

    //single link: display value is the link label (existing behaviour)
    private ObjectNode richTextSingleLinkCell(String displayText, String url) {
        return richTextSectionCell(objectMapper.createArrayNode()
                .add(linkElement(displayText, url)));
    }

    //multiple links: cell value as text, then one link section per link with (name, name) punctuation
    private ObjectNode richTextMultiLinkCell(String displayText, List<NotificationLink> links) {
        ArrayNode sections = objectMapper.createArrayNode();

        ObjectNode textElement = objectMapper.createObjectNode();
        textElement.put("type", "text");
        textElement.put("text", displayText == null ? "" : displayText);

        ObjectNode textSection = objectMapper.createObjectNode();
        textSection.put("type", "rich_text_section");
        textSection.set("elements", objectMapper.createArrayNode().add(textElement));
        sections.add(textSection);

        int linkCount = links.size();
        for (int i = 0; i < linkCount; i++) {
            NotificationLink link = links.get(i);
            String linkLabel = multiLinkLabel(i, linkCount, link.name());
            ObjectNode linkSection = objectMapper.createObjectNode();
            linkSection.put("type", "rich_text_section");
            linkSection.set("elements", objectMapper.createArrayNode().add(linkElement(linkLabel, link.url())));
            sections.add(linkSection);
        }

        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("type", "rich_text");
        cell.set("elements", sections);
        return cell;
    }

    //formatting multis links the same as fields (link name, link name)
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

    //add elements for links (per Block Kit Builder schema)
    private ObjectNode linkElement(String text, String url) {
        ObjectNode linkElement = objectMapper.createObjectNode();
        linkElement.put("type", "link");
        linkElement.put("text", text == null ? "" : text);
        linkElement.put("url", url == null ? "" : url);
        return linkElement;
    }

    private ObjectNode richTextSectionCell(ArrayNode sectionElements) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("type", "rich_text_section");
        section.set("elements", sectionElements);

        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("type", "rich_text");
        cell.set("elements", objectMapper.createArrayNode().add(section));
        return cell;
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
