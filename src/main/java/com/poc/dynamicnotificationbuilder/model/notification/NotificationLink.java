package com.poc.dynamicnotificationbuilder.model.notification;

import lombok.Builder;

@Builder(toBuilder = true)
public record NotificationLink(String name, String url) {
}
