package com.poc.dynamicnotificationbuilder.model.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ADColumn(
        String label,
        String expression,
        List<ADLink> links
) {
}
