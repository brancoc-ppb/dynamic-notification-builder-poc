package com.poc.dynamicnotificationbuilder.model.notification;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record NotificationTable(
        ItemType type,
        String name,
        String alias,
        List<NotificationRow> rows
) implements NotificationItem {
}
