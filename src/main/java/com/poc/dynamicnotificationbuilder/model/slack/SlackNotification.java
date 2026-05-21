package com.poc.dynamicnotificationbuilder.model.slack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.poc.dynamicnotificationbuilder.slack.SlackCustomSchemaFactory;

import java.util.List;

/**
 * Resolved Slack delivery payload: routing metadata plus Block Kit {@code blocks}.
 * Use {@link SlackCustomSchemaFactory#toJson} for the webhook body ({@code text} + {@code blocks}).
 */
public record SlackNotification(
        String patternId,
        String templateId,
        String brand,
        List<String> slackChannels,
        ArrayNode blocks
) {
}
