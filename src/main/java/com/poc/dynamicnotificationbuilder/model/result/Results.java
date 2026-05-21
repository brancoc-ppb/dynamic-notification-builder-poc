package com.poc.dynamicnotificationbuilder.model.result;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder(toBuilder = true)
public record Results(
        String alias,
        JsonNode value
) {
}
