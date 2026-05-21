package com.poc.dynamicnotificationbuilder.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.transformer.NotificationTransformer;
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
class SlackBlockKitSdkFactoryTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationTransformer transformer;

    @Autowired
    private SlackCustomSchemaFactory manualFactory;

    @Autowired
    private SlackBlockKitSdkFactory sdkFactory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void build_producesSameBlocksAsManualFactory(Fixture fixture) {
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        JsonNode manualBlocks = manualFactory.build(notification).blocks();
        JsonNode sdkBlocks = sdkFactory.build(notification).blocks();

        assertThat(sdkBlocks).isEqualTo(manualBlocks);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.poc.dynamicnotificationbuilder.utils.TestUtils#allFixtures")
    void toJson_producesSamePayloadAsManualFactory(Fixture fixture) throws IOException {
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        JsonNode manualRoot = objectMapper.readTree(manualFactory.toJson(notification));
        JsonNode sdkRoot = objectMapper.readTree(sdkFactory.toJson(notification));

        assertThat(sdkRoot).isEqualTo(manualRoot);
    }

    @Test
    void withMultipleLinksInTable_betIdCell_matchesManualFactory() throws IOException {
        var fixture = TestUtils.loadWithMultipleLinksInTable(objectMapper);
        Notification notification = transformer.transform(fixture.template(), fixture.result());

        JsonNode manualTable = tableBlockAt(manualFactory, notification, 0);
        JsonNode sdkTable = tableBlockAt(sdkFactory, notification, 0);

        assertThat(sdkTable).isEqualTo(manualTable);
    }

    private JsonNode tableBlockAt(SlackCustomSchemaFactory factory, Notification notification, int tableIndex) {
        JsonNode blocks = factory.build(notification).blocks();
        return tableBlockAt(blocks, tableIndex);
    }

    private JsonNode tableBlockAt(SlackBlockKitSdkFactory factory, Notification notification, int tableIndex) {
        JsonNode blocks = factory.build(notification).blocks();
        return tableBlockAt(blocks, tableIndex);
    }

    private static JsonNode tableBlockAt(JsonNode blocks, int tableIndex) {
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
}
