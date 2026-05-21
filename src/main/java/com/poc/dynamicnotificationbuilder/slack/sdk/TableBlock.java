package com.poc.dynamicnotificationbuilder.slack.sdk;

import com.slack.api.model.block.LayoutBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Kit table block. The official Java SDK (1.48.x) does not yet ship this type;
 * see <a href="https://github.com/slackapi/java-slack-sdk/issues/1499">java-slack-sdk#1499</a>.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableBlock implements LayoutBlock {

    public static final String TYPE = "table";

    @Builder.Default
    private final String type = TYPE;

    private String blockId;

    @Builder.Default
    private List<List<Object>> rows = new ArrayList<>();
}
