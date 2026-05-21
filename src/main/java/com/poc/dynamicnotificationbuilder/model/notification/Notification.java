package com.poc.dynamicnotificationbuilder.model.notification;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record Notification(
        String alertDate,
        String patternId,
        String templateId,
        String brand,
        String patternName,
        String patternAlertText,
        NotificationType notificationType,
        List<String> slackChannels,
        List<NotificationItem> items
) {
}
