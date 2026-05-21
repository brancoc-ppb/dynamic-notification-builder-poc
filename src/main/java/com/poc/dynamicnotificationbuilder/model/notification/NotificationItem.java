package com.poc.dynamicnotificationbuilder.model.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One resolved unit of a notification, in the same order as {@link com.poc.dynamicnotificationbuilder.model.template.ADTemplate#blocks()}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NotificationField.class, name = "FIELD"),
        @JsonSubTypes.Type(value = NotificationTable.class, name = "TABLE")
})
public sealed interface NotificationItem permits NotificationField, NotificationTable {
}
