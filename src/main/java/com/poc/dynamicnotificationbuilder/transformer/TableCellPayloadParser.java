package com.poc.dynamicnotificationbuilder.transformer;

import com.poc.dynamicnotificationbuilder.model.template.ADLink;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses pipe-delimited table cell strings that encode a display value and link placeholder values.
 * <p>
 * {@code display--link0Value--link1Value1---link1Value2---link1Value3}: {@code --} separates the display
 * value from the link payload and separates single-placeholder link values; {@code ---} separates
 * multiple placeholders within the same link.
 */
final class TableCellPayloadParser {

    private static final Pattern URL_PLACEHOLDER = Pattern.compile("\\{\\{[^}]*}}");

    private TableCellPayloadParser() {
    }

    record ParsedCell(String displayValue, List<String> linkPlaceholderValues) {
    }

    static ParsedCell parse(String rawCell, List<ADLink> links) {
        if (rawCell == null) {
            return new ParsedCell("", List.of());
        }
        if (links == null || links.isEmpty()) {
            return new ParsedCell(rawCell, List.of());
        }
        if (!rawCell.contains("--")) {
            return new ParsedCell(rawCell, List.of());
        }

        int separator = rawCell.indexOf("--");
        String display = rawCell.substring(0, separator);
        String payload = rawCell.substring(separator + 2);
        if (payload.isEmpty()) {
            return new ParsedCell(display, List.of());
        }
        return new ParsedCell(display, parseLinkPayload(payload, links));
    }

    private static List<String> parseLinkPayload(String payload, List<ADLink> links) {
        List<String> values = new ArrayList<>();
        int position = 0;

        for (ADLink link : links) {
            int placeholderCount = countUrlPlaceholders(link.url());
            if (placeholderCount <= 1) {
                int nextDoubleDash = payload.indexOf("--", position);
                String segment = nextDoubleDash < 0
                        ? payload.substring(position)
                        : payload.substring(position, nextDoubleDash);
                values.add(segment.trim());
                position = nextDoubleDash < 0 ? payload.length() : nextDoubleDash + 2;
            } else {
                for (int placeholderIndex = 0; placeholderIndex < placeholderCount; placeholderIndex++) {
                    int nextTripleDash = payload.indexOf("---", position);
                    String segment = nextTripleDash < 0
                            ? payload.substring(position)
                            : payload.substring(position, nextTripleDash);
                    values.add(segment.trim());
                    position = nextTripleDash < 0 ? payload.length() : nextTripleDash + 3;
                }
            }
        }

        return List.copyOf(values);
    }

    public static int countUrlPlaceholders(String urlTemplate) {
        if (urlTemplate == null) {
            return 0;
        }
        int count = 0;
        Matcher matcher = URL_PLACEHOLDER.matcher(urlTemplate);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
