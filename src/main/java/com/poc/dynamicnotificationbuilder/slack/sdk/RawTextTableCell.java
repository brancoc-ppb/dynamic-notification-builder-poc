package com.poc.dynamicnotificationbuilder.slack.sdk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Table cell with plain text ({@code raw_text}). Not yet modeled in slack-api-model 1.48.x.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawTextTableCell {

    public static final String TYPE = "raw_text";

    @Builder.Default
    private final String type = TYPE;

    private String text;
}
