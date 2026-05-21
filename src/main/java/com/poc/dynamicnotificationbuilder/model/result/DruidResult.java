package com.poc.dynamicnotificationbuilder.model.result;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record DruidResult(
    UUID patternId,
    UUID templateId,
    String patternName,
    String brand,
    String patternAlertText,
    String alertDate,
    List<String> destinations,
    String gamDesk,
    List<String> slackChannels,
    List<Results> results
)
{
}
