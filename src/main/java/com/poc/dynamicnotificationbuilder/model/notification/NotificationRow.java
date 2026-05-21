package com.poc.dynamicnotificationbuilder.model.notification;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record NotificationRow(
        List<NotificationColumn> columns
) {
}
