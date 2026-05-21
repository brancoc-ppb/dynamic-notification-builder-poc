package com.poc.dynamicnotificationbuilder.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationType;
import com.poc.dynamicnotificationbuilder.model.notification.ItemType;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationItem;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationLink;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationColumn;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationRow;
import com.poc.dynamicnotificationbuilder.model.result.DruidResult;
import com.poc.dynamicnotificationbuilder.model.result.Results;
import com.poc.dynamicnotificationbuilder.model.template.ADBlock;
import com.poc.dynamicnotificationbuilder.model.template.ADColumn;
import com.poc.dynamicnotificationbuilder.model.template.ADFieldBlock;
import com.poc.dynamicnotificationbuilder.model.template.ADLink;
import com.poc.dynamicnotificationbuilder.model.template.ADTableBlock;
import com.poc.dynamicnotificationbuilder.model.template.ADTemplate;
import com.poc.dynamicnotificationbuilder.model.template.BlockType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NotificationTransformer {

    private static final Pattern URL_PLACEHOLDER = Pattern.compile("\\{\\{[^}]*}}");

    public Notification transform(ADTemplate template, DruidResult result) {
        Map<String, String> index = buildScalarIndex(result.results());
        List<NotificationItem> items = new ArrayList<>();

        for (ADBlock block : template.blocks()) {
            if (block.getType() == BlockType.FIELD && block instanceof ADFieldBlock field) {
                items.add(resolveField(field, index));
            } else if (block.getType() == BlockType.TABLE && block instanceof ADTableBlock table) {
                items.add(resolveTable(table, result.results()));
            }
        }

        return Notification.builder()
                .alertDate(result.alertDate())
                .patternId(result.patternId().toString())
                .templateId(result.templateId().toString())
                .brand(result.brand())
                .patternName(result.patternName())
                .patternAlertText(result.patternAlertText())
                .notificationType(resolveNotificationType(result.destinations()))
                .slackChannels(result.slackChannels())
                .items(Collections.unmodifiableList(items))
                .build();
    }

    private static NotificationType resolveNotificationType(List<String> destinations) {
        if (destinations == null) {
            return null;
        }
        for (String destination : destinations) {
            if (destination == null || destination.isBlank()) {
                continue;
            }
            try {
                return NotificationType.valueOf(destination.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // try next destination value
            }
        }
        return null;
    }

    //transform List<Results> into Map<String, String>
    private Map<String, String> buildScalarIndex(List<Results> results) {
        Map<String, String> index = new HashMap<>();
        if (results == null) {
            return index;
        }
        for (Results row : results) {
            if (row == null || row.alias() == null || row.value() == null || row.value().isNull()) {
                continue;
            }
            if (row.value().isArray()) {
                continue;
            }
            index.put(row.alias(), scalarText(row.value()));
        }
        return index;
    }

    //convert a scalar JsonNode to its string representation for indexing
    private String scalarText(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    //resolve a field block to a NotificationField, using the scalar index for value and link URL resolution
    private NotificationField resolveField(ADFieldBlock field, Map<String, String> index) {
        String alias = field.getAlias();
        String value = index.getOrDefault(alias, "");
        List<NotificationLink> links = resolveLinks(alias, field.getLinks(), index);
        return NotificationField.builder()
                .type(ItemType.FIELD)
                .label(field.getLabel())
                .value(value)
                .links(links)
                .build();
    }

    //resolve links for a field, using the scalar index to replace placeholders in each link URL template
    private List<NotificationLink> resolveLinks(String alias, List<ADLink> links, Map<String, String> index) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        List<NotificationLink> out = new ArrayList<>();
        for (int linkIdx = 0; linkIdx < links.size(); linkIdx++) {
            ADLink link = links.get(linkIdx);
            String url = resolveFieldUrlTemplate(link.url(), alias, linkIdx, index);
            out.add(NotificationLink.builder().name(link.name()).url(url).build());
        }
        return Collections.unmodifiableList(out);
    }

    //replace each {{...}} placeholder in the URL template with the corresponding value from the index using keys of the form alias_linkIndex_placeholderIndex
    private String resolveFieldUrlTemplate(String urlTemplate, String fieldAlias, int linkIndex, Map<String, String> index) {
        if (urlTemplate == null) {
            return "";
        }
        Matcher matcher = URL_PLACEHOLDER.matcher(urlTemplate);
        StringBuilder sb = new StringBuilder();
        int placeholderIndex = 0;
        int last = 0;
        while (matcher.find()) {
            sb.append(urlTemplate, last, matcher.start());
            String key = fieldAlias + "_" + linkIndex + "_" + placeholderIndex;
            sb.append(index.getOrDefault(key, ""));
            last = matcher.end();
            placeholderIndex++;
        }
        sb.append(urlTemplate.substring(last));
        return sb.toString();
    }

    //resolve a table block to a NotificationTable, reading row strings from results and using column definitions for label and link resolution
    private NotificationTable resolveTable(ADTableBlock table, List<Results> results) {
        String alias = table.getAlias();
        List<String> rowStrings = readTableRowStrings(alias, results); //row strings from results
        List<NotificationRow> rows = new ArrayList<>();

        List<ADColumn> columns = table.getColumns() == null ? List.of() : table.getColumns(); //columns label, expression and links from template

        for (int rowIdx = 0; rowIdx < rowStrings.size(); rowIdx++) {
            String[] cells = rowStrings.get(rowIdx).split("\\|", -1); //splits each cell into a string array
            List<NotificationColumn> rowColumns = new ArrayList<>();
            for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                ADColumn column = columns.get(colIdx);
                String rawCell = colIdx < cells.length ? cells[colIdx] : "";
                List<ADLink> columnLinks = column.links() == null ? List.of() : column.links();
                TableCellPayloadParser.ParsedCell parts = TableCellPayloadParser.parse(rawCell, columnLinks);
                List<NotificationLink> links = resolveTableColumnLinks(column, parts.linkPlaceholderValues());
                rowColumns.add(NotificationColumn.builder()
                        .label(column.label())
                        .value(parts.displayValue())
                        .links(links)
                        .build());
            }
            rows.add(NotificationRow.builder()
                    .columns(Collections.unmodifiableList(rowColumns))
                    .build());
        }

        return NotificationTable.builder()
                .type(ItemType.TABLE)
                .name(table.getName())
                .alias(alias)
                .rows(Collections.unmodifiableList(rows))
                .build();
    }

    //resolve links for a table column, using the cell placeholder values from the same row to replace placeholders in each link URL template in order
    private List<NotificationLink> resolveTableColumnLinks(ADColumn column, List<String> cellPlaceholderValues) {
        if (column.links() == null || column.links().isEmpty()) {
            return List.of();
        }
        List<String> values = cellPlaceholderValues == null ? List.of() : cellPlaceholderValues;
        int cursor = 0;
        List<NotificationLink> out = new ArrayList<>();
        for (ADLink link : column.links()) {
            int placeholderCount = TableCellPayloadParser.countUrlPlaceholders(link.url());
            List<String> slice = new ArrayList<>(placeholderCount);
            for (int i = 0; i < placeholderCount; i++) {
                int idx = cursor + i;
                slice.add(idx < values.size() ? values.get(idx) : "");
            }
            cursor += placeholderCount;
            String url = resolveTableUrlTemplate(link.url(), slice);
            out.add(NotificationLink.builder()
                    .name(link.name())
                    .url(url)
                    .build());
        }
        return Collections.unmodifiableList(out);
    }

    //replace each {{...}} placeholder in the URL template with the corresponding value from the cellPlaceholderValues list in order
    private String resolveTableUrlTemplate(String urlTemplate, List<String> cellPlaceholderValues) {
        if (urlTemplate == null) {
            return "";
        }
        Matcher matcher = URL_PLACEHOLDER.matcher(urlTemplate);
        StringBuilder sb = new StringBuilder();
        int ph = 0;
        int last = 0;
        while (matcher.find()) {
            sb.append(urlTemplate, last, matcher.start());
            String repl = ph < cellPlaceholderValues.size() ? cellPlaceholderValues.get(ph) : "";
            sb.append(repl);
            last = matcher.end();
            ph++;
        }
        sb.append(urlTemplate.substring(last));
        return sb.toString();
    }

    //reads the string values for each row of a table from results using the table alias,
    // expecting an array of strings where each string is the raw cell value for
    // that row with link placeholder values appended if links are present for that column
    private List<String> readTableRowStrings(String tableAlias, List<Results> results) {
        if (results == null || tableAlias == null) {
            return List.of();
        }
        for (Results r : results) {
            if (r == null || !tableAlias.equals(r.alias()) || r.value() == null || !r.value().isArray()) {
                continue;
            }
            List<String> list = new ArrayList<>();
            for (JsonNode n : r.value()) {
                if (n != null && !n.isNull()) {
                    list.add(scalarText(n));
                }
            }
            return list;
        }
        return List.of();
    }
}
