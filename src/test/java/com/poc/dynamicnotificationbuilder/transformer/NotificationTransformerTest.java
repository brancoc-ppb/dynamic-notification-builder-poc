package com.poc.dynamicnotificationbuilder.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.ItemType;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationColumn;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationItem;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationType;
import com.poc.dynamicnotificationbuilder.model.template.ADFieldBlock;
import com.poc.dynamicnotificationbuilder.utils.TestUtils;
import com.poc.dynamicnotificationbuilder.utils.TestUtils.Fixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationTransformerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationTransformer transformer;

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void mapsDestinationsAndSlackChannelsFromDruidResult(Fixture fixture) {
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        assertThat(notification.notificationType()).isEqualTo(NotificationType.SLACK);
        assertThat(notification.slackChannels()).containsExactly("slack webhook");
        assertThat(notification.patternId()).isEqualTo(fixture.result().patternId().toString());
        assertThat(notification.templateId()).isEqualTo(fixture.result().templateId().toString());
        assertThat(notification.brand()).isEqualTo("Fanduel");
        assertThat(notification.alertDate()).isEqualTo(fixture.result().alertDate());
        assertThat(notification.alertDate()).isEqualTo(TestUtils.ALERT_DATE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void transform_itemCountMatchesTemplateBlocks(Fixture fixture) {
        Notification built = transformer.transform(fixture.template(), fixture.result());

        assertThat(built.items()).hasSize(fixture.template().blocks().size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void transform_eachItemHasVisibleType(Fixture fixture) {
        Notification built = transformer.transform(fixture.template(), fixture.result());

        assertThat(built.items()).allSatisfy(item -> {
            if (item instanceof NotificationField field) {
                assertThat(field.type()).isEqualTo(ItemType.FIELD);
            } else if (item instanceof NotificationTable table) {
                assertThat(table.type()).isEqualTo(ItemType.TABLE);
            }
        });
    }

    @Test
    void with1Table_deserializesTemplateWithFieldAndTableBlocks() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);

        assertThat(fixture.template().blocks()).hasSize(7);
        assertThat(fixture.template().blocks().get(1)).isInstanceOf(ADFieldBlock.class);
        assertThat(fixture.template().name()).isEqualTo("A Template Name");
    }

    @Test
    void with1Table_resolvesSelectionNameLinkUsingAliasConvention() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        NotificationField selectionName = built.items().stream()
                .filter(NotificationField.class::isInstance)
                .map(NotificationField.class::cast)
                .filter(f -> "Selection Name".equals(f.label()))
                .findFirst()
                .orElseThrow();

        assertThat(selectionName.value()).isEqualTo("a selection name");
        assertThat(selectionName.links()).hasSize(1);
        assertThat(selectionName.links().getFirst().url())
                .isEqualTo("https://mpm/eventselections/123456789");
    }

    @Test
    void with1Table_resolvesMarketNameSecondLinkWithTwoPlaceholders() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        NotificationField marketName = built.items().stream()
                .filter(NotificationField.class::isInstance)
                .map(NotificationField.class::cast)
                .filter(f -> "Market Name".equals(f.label()))
                .findFirst()
                .orElseThrow();

        assertThat(marketName.links()).hasSize(2);
        assertThat(marketName.links().get(1).url())
                .isEqualTo("https://mpm/events/123/markets/123456");
    }

    @Test
    void with1Table_preservesTemplateBlockOrderInItems() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        assertThat(built.items().getFirst()).isInstanceOf(NotificationField.class);
        assertThat(((NotificationField) built.items().getFirst()).label()).isEqualTo("Selection ID");
        assertThat(built.items().get(5)).isInstanceOf(NotificationField.class);
        assertThat(((NotificationField) built.items().get(5)).label()).isEqualTo("Market Name");
        assertThat(built.items().get(6)).isInstanceOf(NotificationTable.class);
        assertThat(((NotificationTable) built.items().get(6)).alias()).isEqualTo("betsTableInfo");
    }

    @Test
    void with1Table_parsesTableRowsFromPipeDelimitedStrings() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        NotificationTable bets = built.items().stream()
                .filter(NotificationTable.class::isInstance)
                .map(NotificationTable.class::cast)
                .filter(t -> "betsTableInfo".equals(t.alias()))
                .findFirst()
                .orElseThrow();

        assertThat(bets.rows()).hasSize(2);
        assertThat(bets.rows().getFirst().columns().getFirst().value()).isEqualTo("FANDUEL");
        assertThat(bets.rows().getFirst().columns().get(1).value()).isEqualTo("987654321");
        assertThat(bets.rows().getFirst().columns().get(1).links().getFirst().url())
                .isEqualTo("https://openBet/bets/987654321");
        assertThat(bets.rows().getFirst().columns().get(2).value()).isEqualTo("11414194");
        assertThat(bets.rows().getFirst().columns().get(2).links().getFirst().url())
                .isEqualTo("https://iap/customer/11414194");
    }

    @Test
    void with2Tables_preservesTableFieldTableOrder() throws IOException {
        var fixture = TestUtils.loadWith2Tables(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        assertThat(built.items()).hasSize(5);
        assertThat(built.items().get(0)).isInstanceOf(NotificationTable.class);
        assertThat(((NotificationTable) built.items().get(0)).alias()).isEqualTo("betsTableInfo");
        assertThat(built.items().get(1)).isInstanceOf(NotificationField.class);
        assertThat(((NotificationField) built.items().get(1)).label()).isEqualTo("Event ID");
        assertThat(built.items().get(3)).isInstanceOf(NotificationTable.class);
        assertThat(((NotificationTable) built.items().get(3)).alias()).isEqualTo("customerTableInfo");
        assertThat(built.items().get(4)).isInstanceOf(NotificationField.class);
        assertThat(((NotificationField) built.items().get(4)).label()).isEqualTo("Liability Amount");
    }

    @Test
    void with2Tables_resolvesEventNameLink() throws IOException {
        var fixture = TestUtils.loadWith2Tables(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        NotificationField eventName = built.items().stream()
                .filter(NotificationField.class::isInstance)
                .map(NotificationField.class::cast)
                .filter(f -> "Event Name".equals(f.label()))
                .findFirst()
                .orElseThrow();

        assertThat(eventName.value()).isEqualTo("an event name");
        assertThat(eventName.links()).hasSize(1);
        assertThat(eventName.links().getFirst().url())
                .isEqualTo("https://mpm/eventselections/123");
    }

    @Test
    void with2Tables_parsesBothTables() throws IOException {
        var fixture = TestUtils.loadWith2Tables(objectMapper);
        Notification built = transformer.transform(fixture.template(), fixture.result());

        NotificationTable bets = built.items().stream()
                .filter(NotificationTable.class::isInstance)
                .map(NotificationTable.class::cast)
                .filter(t -> "betsTableInfo".equals(t.alias()))
                .findFirst()
                .orElseThrow();

        assertThat(bets.rows()).hasSize(2);
        assertThat(bets.rows().getFirst().columns().get(0).value()).isEqualTo("987654321");
        assertThat(bets.rows().getFirst().columns().get(0).links().getFirst().url())
                .isEqualTo("https://openBet/bets/987654321");
        assertThat(bets.rows().getFirst().columns().get(1).value()).isEqualTo("1.0");

        NotificationTable customers = built.items().stream()
                .filter(NotificationTable.class::isInstance)
                .map(NotificationTable.class::cast)
                .filter(t -> "customerTableInfo".equals(t.alias()))
                .findFirst()
                .orElseThrow();

        assertThat(customers.rows()).hasSize(2);
        assertThat(customers.rows().getFirst().columns().get(0).value()).isEqualTo("FANDUEL");
        assertThat(customers.rows().getFirst().columns().get(1).value()).isEqualTo("11414194");
        assertThat(customers.rows().getFirst().columns().get(1).links().getFirst().url())
                .isEqualTo("https://iap/customer/11414194");
        assertThat(customers.rows().getFirst().columns().get(2).value()).isEqualTo("USD");
    }

    @Test
    void withMultipleLinksInTable_resolvesBetIdCellWithTwoLinks() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        var built = transformer.transform(fixture.template(), fixture.result());

        NotificationTable betsTable = tableByAlias(built, "betsTableInfo");
        NotificationColumn betId = betsTable.rows().getFirst().columns().getFirst();

        assertThat(betId.value()).isEqualTo("987654321");
        assertThat(betId.links()).hasSize(2);
        assertThat(betId.links().get(0).url()).isEqualTo("https://openBet/bets/987654321");
        assertThat(betId.links().get(1).url()).isEqualTo("https://mpm/bets/987654321");
    }

    @Test
    void withMultipleLinksInTable_resolvesSelectionCellWithMultiPlaceholderSecondLink() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        var built = transformer.transform(fixture.template(), fixture.result());

        NotificationColumn selection = tableByAlias(built, "betsTableInfo")
                .rows().getFirst().columns().get(1);

        assertThat(selection.value()).isEqualTo("a selection name");
        assertThat(selection.links()).hasSize(2);
        assertThat(selection.links().get(0).url()).isEqualTo("https://mpm/selections/789");
        assertThat(selection.links().get(1).url())
                .isEqualTo("https://iap.prf.internal/gfbui#/fieldbook/events/123/markets/456/selections/789");
    }

    @Test
    void withMultipleLinksInTable_resolvesCustomerCellWithSingleLink() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        var built = transformer.transform(fixture.template(), fixture.result());

        NotificationColumn customerId = tableByAlias(built, "customerTableInfo")
                .rows().getFirst().columns().getFirst();

        assertThat(customerId.value()).isEqualTo("11414194");
        assertThat(customerId.links()).hasSize(1);
        assertThat(customerId.links().getFirst().url()).isEqualTo("https://iap/customer/11414194");
    }

    private static NotificationTable tableByAlias(Notification built, String alias) {
        return built.items().stream()
                .filter(NotificationTable.class::isInstance)
                .map(NotificationTable.class::cast)
                .filter(t -> alias.equals(t.alias()))
                .findFirst()
                .orElseThrow();
    }
}
