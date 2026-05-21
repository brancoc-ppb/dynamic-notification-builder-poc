package com.poc.dynamicnotificationbuilder.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.ItemType;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationColumn;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationItem;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationRow;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationType;
import com.poc.dynamicnotificationbuilder.transformer.NotificationTransformer;
import com.poc.dynamicnotificationbuilder.utils.TestUtils;
import com.poc.dynamicnotificationbuilder.utils.TestUtils.Fixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SlackCustomSchemaFactoryTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationTransformer transformer;

    @Autowired
    private SlackCustomSchemaFactory slackCustomSchemaFactory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void build_slackPayloadIncludesMetadataFromNotification(Fixture fixture) {
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        var payload = slackCustomSchemaFactory.build(notification);

        assertThat(payload.slackChannels()).containsExactly("slack webhook");
        assertThat(payload.brand()).isEqualTo("Fanduel");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void build_includesAlertDateAsFirstSection(Fixture fixture) {
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        JsonNode blocks = slackCustomSchemaFactory.build(notification).blocks();

        assertThat(blocks.get(0).path("text").path("text").asText())
                .isEqualTo(TestUtils.ALERT_DATE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void toJson_isValidForPostman(Fixture fixture) throws Exception {
        Notification notification = transformer.transform(fixture.template(), fixture.result());
        String json = slackCustomSchemaFactory.toJson(notification);
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("blocks").isArray()).isTrue();
        assertThat(root.get("blocks").size()).isGreaterThan(0);
    }

    @Test
    void with1Table_fieldsThenTable_matchesTemplateOrder() throws IOException {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification notification = transformer.transform(fixture.template(), fixture.result());
        JsonNode blocks = slackCustomSchemaFactory.build(notification).blocks();

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(1).path("text").path("text").asText()).isEqualTo("A Pattern Name");
        assertThat(blocks.get(2).path("text").path("text").asText())
                .isEqualTo(fixture.result().patternAlertText());
        assertThat(blocks.get(3).path("text").path("text").asText()).contains("Selection ID:");

        assertThat(blocks.get(blocks.size() - 1).get("type").asText()).isEqualTo("table");
        assertThat(countBlocksOfType(blocks, "divider")).isZero();
        assertThat(countBlocksOfType(blocks, "section")).isEqualTo(9);
    }

    @Test
    void with2Tables_tableFieldTable_matchesTemplateOrder() throws IOException {
        var fixture = TestUtils.loadWith2Tables(objectMapper);
        Notification notification = transformer.transform(fixture.template(), fixture.result());
        JsonNode blocks = slackCustomSchemaFactory.build(notification).blocks();

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(3).get("type").asText()).isEqualTo("table");
        assertThat(blocks.get(4).path("text").path("text").asText()).contains("Event ID:");
        assertThat(blocks.get(5).path("text").path("text").asText()).contains("Event Name:");
        assertThat(blocks.get(6).get("type").asText()).isEqualTo("table");
        assertThat(blocks.get(7).path("text").path("text").asText()).contains("Liability Amount:");

        assertThat(countBlocksOfType(blocks, "divider")).isZero();
        assertThat(countBlocksOfType(blocks, "table")).isEqualTo(2);
    }

    @Test
    void interleavedTableFieldTable_preservesItemOrder() {
        NotificationField fieldA = NotificationField.builder()
                .type(ItemType.FIELD)
                .label("Field A")
                .value("value A")
                .links(List.of())
                .build();
        NotificationField fieldB = NotificationField.builder()
                .type(ItemType.FIELD)
                .label("Field B")
                .value("value B")
                .links(List.of())
                .build();
        NotificationTable table1 = NotificationTable.builder()
                .type(ItemType.TABLE)
                .name("Table 1")
                .alias("t1")
                .rows(List.of(NotificationRow.builder().columns(List.of()).build()))
                .build();
        NotificationTable table2 = NotificationTable.builder()
                .type(ItemType.TABLE)
                .name("Table 2")
                .alias("t2")
                .rows(List.of(NotificationRow.builder().columns(List.of()).build()))
                .build();

        List<NotificationItem> items = List.of(table1, fieldA, fieldB, table2);
        Notification notification = Notification.builder()
                .alertDate(TestUtils.ALERT_DATE)
                .patternName("Mixed")
                .patternAlertText("Alert")
                .notificationType(NotificationType.SLACK)
                .slackChannels(List.of("channel-a"))
                .items(items)
                .build();

        JsonNode blocks = slackCustomSchemaFactory.build(notification).blocks();

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(3).get("type").asText()).isEqualTo("table");
        assertThat(blocks.get(4).path("text").path("text").asText()).contains("Field A:");
        assertThat(blocks.get(5).path("text").path("text").asText()).contains("Field B:");
        assertThat(blocks.get(blocks.size() - 1).get("type").asText()).isEqualTo("table");
    }

    @Test
    void withMultipleLinksInTable_betIdCell_usesRichTextWithDisplayTextAndNamedLinks() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        Notification notification = transformer.transform(fixture.template(), fixture.result());
        JsonNode tableBlock = tableBlockAt(notification, 0);

        NotificationColumn betIdColumn = notification.items().stream()
                .filter(NotificationTable.class::isInstance)
                .map(NotificationTable.class::cast)
                .filter(t -> "betsTableInfo".equals(t.alias()))
                .findFirst()
                .orElseThrow()
                .rows().getFirst().columns().getFirst();

        JsonNode betIdCell = tableBlock.get("rows").get(1).get(0);
        assertThat(betIdCell.get("type").asText()).isEqualTo("rich_text");

        JsonNode elements = betIdCell.get("elements");
        assertThat(elements).hasSize(3);
        assertThat(elements.get(0).get("elements").get(0).get("type").asText()).isEqualTo("text");
        assertThat(elements.get(0).get("elements").get(0).get("text").asText()).isEqualTo("987654321");
        assertThat(elements.get(1).get("elements").get(0).get("type").asText()).isEqualTo("link");
        assertThat(elements.get(1).get("elements").get(0).get("text").asText())
                .isEqualTo(" (" + betIdColumn.links().get(0).name() + ",");
        assertThat(elements.get(1).get("elements").get(0).get("url").asText())
                .isEqualTo(betIdColumn.links().get(0).url());
        assertThat(elements.get(2).get("elements").get(0).get("text").asText())
                .isEqualTo(" " + betIdColumn.links().get(1).name() + ")");
        assertThat(elements.get(2).get("elements").get(0).get("url").asText())
                .isEqualTo(betIdColumn.links().get(1).url());
    }

    @Test
    void withMultipleLinksInTable_customerIdCell_usesSingleLinkRichText() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        Notification notification = transformer.transform(fixture.template(), fixture.result());
        JsonNode tableBlock = tableBlockAt(notification, 1);

        JsonNode customerCell = tableBlock.get("rows").get(1).get(0);
        assertThat(customerCell.get("type").asText()).isEqualTo("rich_text");
        assertThat(customerCell.get("elements")).hasSize(1);
        assertThat(customerCell.get("elements").get(0).get("elements").get(0).get("type").asText())
                .isEqualTo("link");
        assertThat(customerCell.get("elements").get(0).get("elements").get(0).get("text").asText())
                .isEqualTo("11414194");
    }

    private JsonNode tableBlockAt(Notification notification, int tableIndex) {
        JsonNode blocks = slackCustomSchemaFactory.build(notification).blocks();
        int seen = 0;
        for (JsonNode block : blocks) {
            if ("table".equals(block.path("type").asText())) {
                if (seen == tableIndex) {
                    return block;
                }
                seen++;
            }
        }
        throw new AssertionError("No table block at index " + tableIndex);
    }

    private static long countBlocksOfType(JsonNode blocks, String type) {
        long count = 0;
        for (JsonNode block : blocks) {
            if (type.equals(block.path("type").asText())) {
                count++;
            }
        }
        return count;
    }
}
