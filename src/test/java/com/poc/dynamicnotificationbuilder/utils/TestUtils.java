package com.poc.dynamicnotificationbuilder.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.dynamicnotificationbuilder.model.result.DruidResult;
import com.poc.dynamicnotificationbuilder.model.template.ADTemplate;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classpath paths for test JSON under {@code src/test/resources}.
 */
public final class TestUtils {

    public static final String TEMPLATE_WITH_1_TABLE = "/TemplateWith1Table.json";
    public static final String RESULT_WITH_1_TABLE = "/ResultWith1Table.json";
    public static final String TEMPLATE_WITH_2_TABLES = "/TemplateWith2Tables.json";
    public static final String RESULT_WITH_2_TABLES = "/ResultWith2Tables.json";
    public static final String TEMPLATE_WITH_MULTIPLE_LINKS_IN_TABLE = "/TemplateWithMultipleLinksInTable.json";
    public static final String RESULT_WITH_MULTIPLE_LINKS_IN_TABLE = "/ResultWithMultipleLinksInTable.json";
    public static final String ALERT_DATE = "2026-02-25T12:34:56.000Z";

    private TestUtils() {
    }

    public record Fixture(String name, ADTemplate template, DruidResult result) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static Fixture loadWith1Table(ObjectMapper objectMapper) throws IOException {
        return load(objectMapper, "with1Table", TEMPLATE_WITH_1_TABLE, RESULT_WITH_1_TABLE);
    }

    public static Fixture loadWith2Tables(ObjectMapper objectMapper) throws IOException {
        return load(objectMapper, "with2Tables", TEMPLATE_WITH_2_TABLES, RESULT_WITH_2_TABLES);
    }

    public static Fixture loadWithMultipleLinksInTable(ObjectMapper objectMapper) throws IOException {
        return load(
                objectMapper,
                "withMultipleLinksInTable",
                TEMPLATE_WITH_MULTIPLE_LINKS_IN_TABLE,
                RESULT_WITH_MULTIPLE_LINKS_IN_TABLE
        );
    }

    public static Stream<Arguments> allFixtures(ObjectMapper objectMapper) throws IOException {
        return Stream.of(
                Arguments.of(loadWith1Table(objectMapper)),
                Arguments.of(loadWith2Tables(objectMapper)),
                Arguments.of(loadWithMultipleLinksInTable(objectMapper))
        );
    }

    /** For {@code @MethodSource} on test classes (static factory required by JUnit). */
    public static Stream<Arguments> allFixtures() throws IOException {
        return allFixtures(new ObjectMapper());
    }

    private static Fixture load(
            ObjectMapper objectMapper,
            String name,
            String templatePath,
            String resultPath
    ) throws IOException {
        try (InputStream templateStream = TestUtils.class.getResourceAsStream(templatePath);
             InputStream resultStream = TestUtils.class.getResourceAsStream(resultPath)) {
            assertThat(templateStream).as("template util %s", templatePath).isNotNull();
            assertThat(resultStream).as("result util %s", resultPath).isNotNull();
            return new Fixture(
                    name,
                    objectMapper.readValue(templateStream, ADTemplate.class),
                    objectMapper.readValue(resultStream, DruidResult.class)
            );
        }
    }
}
