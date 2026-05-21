package com.poc.dynamicnotificationbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationField;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationTable;
import com.poc.dynamicnotificationbuilder.model.notification.NotificationType;
import com.poc.dynamicnotificationbuilder.model.slack.SlackNotification;
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
class NotificationBuilderServiceTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationBuilderService notificationBuilderService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void build_returnsResolvedNotification(Fixture fixture) {
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        assertThat(notification.alertDate()).isEqualTo(fixture.result().alertDate());
        assertThat(notification.alertDate()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(notification.patternName()).isEqualTo("A Pattern Name");
        assertThat(notification.patternAlertText()).isEqualTo(fixture.result().patternAlertText());
        assertThat(notification.notificationType()).isEqualTo(NotificationType.SLACK);
        assertThat(notification.slackChannels()).containsExactly("slack webhook");
        assertThat(notification.items()).hasSize(fixture.template().blocks().size());
        assertThat(notification.items().stream().anyMatch(NotificationTable.class::isInstance)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void buildSlackPayload_carriesRoutingMetadataFromNotification(Fixture fixture) {
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        SlackNotification payload = notificationBuilderService.buildSlackPayload(notification);

        assertThat(payload.patternId()).isEqualTo(notification.patternId());
        assertThat(payload.templateId()).isEqualTo(notification.templateId());
        assertThat(payload.brand()).isEqualTo("Fanduel");
        assertThat(payload.slackChannels()).containsExactly("slack webhook");
        assertThat(payload.blocks()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void toSlackJson_returnsWebhookReadyBody(Fixture fixture) throws Exception {
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        String json = notificationBuilderService.toSlackJson(notification);
        var root = objectMapper.readTree(json);

        assertThat(root.get("blocks").isArray()).isTrue();
        assertThat(root.get("blocks").get(0).get("type").asText()).isEqualTo("section");
        assertThat(root.get("blocks").get(0).path("text").path("text").asText())
                .isEqualTo(TestUtils.ALERT_DATE);
        assertThat(root.get("blocks").get(1).path("text").path("text").asText())
                .isEqualTo("A Pattern Name");
        assertThat(root.get("blocks"))
                .anyMatch(block -> "table".equals(block.get("type").asText()));
    }

    @Test
    void with1Table_toSlackJson_preservesTemplateItemOrderInBlocks() throws Exception {
        var fixture = TestUtils.loadWith1Table(objectMapper);
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        var blocks = objectMapper.readTree(notificationBuilderService.toSlackJson(notification))
                .get("blocks");

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(3).path("text").path("text").asText()).contains("Selection ID:");
    }

    @Test
    void with2Tables_toSlackJson_startsWithTableThenFields() throws Exception {
        var fixture = TestUtils.loadWith2Tables(objectMapper);
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        var blocks = objectMapper.readTree(notificationBuilderService.toSlackJson(notification))
                .get("blocks");

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(3).get("type").asText()).isEqualTo("table");
        assertThat(blocks.get(4).path("text").path("text").asText()).contains("Event ID:");
        assertThat(notification.items().get(4)).isInstanceOf(NotificationField.class);
    }

    @Test
    void withMultipleLinksInTable_toSlackJson_startsWithTableThenField() throws Exception {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        Notification notification = notificationBuilderService.build(fixture.template(), fixture.result());

        var blocks = objectMapper.readTree(notificationBuilderService.toSlackJson(notification))
                .get("blocks");

        assertThat(blocks.get(0).path("text").path("text").asText()).isEqualTo(TestUtils.ALERT_DATE);
        assertThat(blocks.get(2).path("text").path("text").asText())
                .isEqualTo(fixture.result().patternAlertText());
        assertThat(blocks.get(3).get("type").asText()).isEqualTo("table");
        assertThat(blocks.get(4).path("text").path("text").asText()).contains("Liability Amount:");
        assertThat(notification.items().getFirst()).isInstanceOf(NotificationTable.class);
    }
}
