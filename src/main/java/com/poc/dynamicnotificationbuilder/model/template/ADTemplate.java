package com.poc.dynamicnotificationbuilder.model.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ADTemplate(
        UUID id,
        String name,
        String brand,
        String datasource,
        List<String> aggregators,
        List<ADBlock> blocks
) {
}
