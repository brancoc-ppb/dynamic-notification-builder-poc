package com.poc.dynamicnotificationbuilder.model.template;

import lombok.Builder;

@Builder(toBuilder = true)
public record ADLink(
        String name,
        String url
) {
}
