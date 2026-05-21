package com.poc.dynamicnotificationbuilder.transformer;

import com.poc.dynamicnotificationbuilder.model.template.ADLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableCellPayloadParserTest {

    @Test
    void parse_oneLinkPerCell_splitsOnDoubleDash() {
        var links = List.of(
                link("IAP", "https://iap/customer/{{id}}")
        );

        var parsed = TableCellPayloadParser.parse("11414194--11414194", links);

        assertThat(parsed.displayValue()).isEqualTo("11414194");
        assertThat(parsed.linkPlaceholderValues()).containsExactly("11414194");
    }

    @Test
    void parse_multipleLinksWithSinglePlaceholder_splitsOnDoubleDash() {
        var links = List.of(
                link("MPM URL", "https://openBet/bets/{{betId}}"),
                link("IAP URL", "https://mpm/bets/{{betId}}")
        );

        var parsed = TableCellPayloadParser.parse("987654321--987654321--987654321", links);

        assertThat(parsed.displayValue()).isEqualTo("987654321");
        assertThat(parsed.linkPlaceholderValues()).containsExactly("987654321", "987654321");
    }

    @Test
    void parse_linkWithMultiplePlaceholders_splitsOnTripleDash() {
        var links = List.of(
                link("MPM", "https://mpm/selections/{{selectionId}}"),
                link("GFB", "https://iap.prf.internal/gfbui#/fieldbook/events/{{eventId}}/markets/{{marketId}}/selections/{{selectionId}}")
        );

        var parsed = TableCellPayloadParser.parse("a selection name--789--123---456---789", links);

        assertThat(parsed.displayValue()).isEqualTo("a selection name");
        assertThat(parsed.linkPlaceholderValues()).containsExactly("789", "123", "456", "789");
    }

    @Test
    void parse_noLinks_returnsRawCell() {
        var parsed = TableCellPayloadParser.parse("USD", List.of());

        assertThat(parsed.displayValue()).isEqualTo("USD");
        assertThat(parsed.linkPlaceholderValues()).isEmpty();
    }

    private static ADLink link(String name, String url) {
        return new ADLink(name, url);
    }
}
