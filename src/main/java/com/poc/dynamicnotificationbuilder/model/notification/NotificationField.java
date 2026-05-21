package com.poc.dynamicnotificationbuilder.model.notification;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record NotificationField(
        ItemType type,
        String label,
        String value,
        List<NotificationLink> links
) implements NotificationItem {
}
