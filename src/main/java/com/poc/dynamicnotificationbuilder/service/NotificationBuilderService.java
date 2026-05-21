package com.poc.dynamicnotificationbuilder.service;

import com.poc.dynamicnotificationbuilder.model.notification.Notification;
import com.poc.dynamicnotificationbuilder.model.result.DruidResult;
import com.poc.dynamicnotificationbuilder.model.slack.SlackNotification;
import com.poc.dynamicnotificationbuilder.model.template.ADTemplate;
import com.poc.dynamicnotificationbuilder.slack.SlackCustomSchemaFactory;
import com.poc.dynamicnotificationbuilder.transformer.NotificationTransformer;
import org.springframework.stereotype.Service;

@Service
public class NotificationBuilderService {

    private final NotificationTransformer notificationTransformer;
    private final SlackCustomSchemaFactory slackCustomSchemaFactory;

    public NotificationBuilderService(
            NotificationTransformer notificationTransformer,
            SlackCustomSchemaFactory slackCustomSchemaFactory
    ) {
        this.notificationTransformer = notificationTransformer;
        this.slackCustomSchemaFactory = slackCustomSchemaFactory;
    }

    public Notification build(ADTemplate template, DruidResult result) {
        return notificationTransformer.transform(template, result);
    }

    public SlackNotification buildSlackPayload(Notification notification) {
        return slackCustomSchemaFactory.build(notification);
    }

    //method created only for testing that the "blocks" from the SlackNotification comply with the Block Kit Builder schema
    public String toSlackJson(Notification notification) {
        return slackCustomSchemaFactory.toJson(notification);
    }
}
