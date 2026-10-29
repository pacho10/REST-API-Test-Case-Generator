package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.InputLocation;
import com.example.restapitestcasegenerator.dto.ParsedInputFieldResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import com.example.restapitestcasegenerator.model.GeneratedTestCase;
import com.example.restapitestcasegenerator.model.TestScenarioType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestCaseGeneratorServiceTest {

    private final TestCaseGeneratorService generator = new TestCaseGeneratorService();

    @Test
    void generatesRepeatableValidInput() {
        ParsedOperationResponse operation = operation(List.of(
                field("name", InputLocation.REQUEST_BODY, true, "string"),
                field("quantity", InputLocation.REQUEST_BODY, false, "integer")));

        GeneratedTestCase first = scenario(generator.generateTestCases(operation),
                TestScenarioType.VALID_INPUT, 0);
        GeneratedTestCase second = scenario(generator.generateTestCases(operation),
                TestScenarioType.VALID_INPUT, 0);

        assertThat(first.requestBody()).isEqualTo(Map.of("name", "xxxxxxxx", "quantity", 1));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void createsAnIndependentCaseForEachMissingRequiredField() {
        ParsedOperationResponse operation = operation(List.of(
                field("name", InputLocation.REQUEST_BODY, true, "string"),
                field("note", InputLocation.REQUEST_BODY, false, "string")));

        List<GeneratedTestCase> missingCases = generator.generateTestCases(operation).stream()
                .filter(testCase -> testCase.scenarioType() == TestScenarioType.MISSING_REQUIRED_FIELD)
                .toList();

        assertThat(missingCases).hasSize(1);
        assertThat(missingCases.getFirst().requestBody())
                .isEqualTo(Map.of("note", "xxxxxxxx"));
    }

    @Test
    void generatesInvalidTypeCasesForSupportedSchemaTypes() {
        ParsedOperationResponse operation = operation(List.of(
                field("name", InputLocation.REQUEST_BODY, false, "string"),
                field("count", InputLocation.REQUEST_BODY, false, "integer"),
                field("enabled", InputLocation.REQUEST_BODY, false, "boolean")));

        List<GeneratedTestCase> invalidCases = generator.generateTestCases(operation).stream()
                .filter(testCase -> testCase.scenarioType() == TestScenarioType.INVALID_TYPE)
                .toList();

        assertThat(invalidCases).hasSize(3);
        assertThat(invalidCases.get(0).requestBody()).isEqualTo(Map.of(
                "name", 123, "count", 1, "enabled", true));
        assertThat(invalidCases.get(1).requestBody()).isEqualTo(Map.of(
                "name", "xxxxxxxx", "count", "not-a-number", "enabled", true));
        assertThat(invalidCases.get(2).requestBody()).isEqualTo(Map.of(
                "name", "xxxxxxxx", "count", 1, "enabled", "not-a-boolean"));
    }

    @Test
    void generatesNumericAndStringBoundaryCases() {
        ParsedOperationResponse operation = operation(List.of(
                field("quantity", InputLocation.REQUEST_BODY, false, "integer",
                        new BigDecimal("1"), new BigDecimal("5"), null, null),
                field("code", InputLocation.REQUEST_BODY, false, "string",
                        null, null, 2, 4)));

        List<GeneratedTestCase> boundaryCases = generator.generateTestCases(operation).stream()
                .filter(testCase -> testCase.scenarioType() == TestScenarioType.BOUNDARY_VALUE)
                .toList();

        assertThat(boundaryCases).hasSize(4);
        assertThat(bodyValue(boundaryCases.get(0), "quantity")).isEqualTo(1);
        assertThat(bodyValue(boundaryCases.get(1), "quantity")).isEqualTo(5);
        assertThat(bodyValue(boundaryCases.get(2), "code")).isEqualTo("xx");
        assertThat(bodyValue(boundaryCases.get(3), "code")).isEqualTo("xxxx");
    }

    private ParsedOperationResponse operation(List<ParsedInputFieldResponse> inputs) {
        return new ParsedOperationResponse("POST", "/items", "Create item",
                "createItem", false, inputs);
    }

    private ParsedInputFieldResponse field(
            String name,
            InputLocation location,
            boolean required,
            String type) {
        return field(name, location, required, type, null, null, null, null);
    }

    private ParsedInputFieldResponse field(
            String name,
            InputLocation location,
            boolean required,
            String type,
            BigDecimal minimum,
            BigDecimal maximum,
            Integer minLength,
            Integer maxLength) {
        return new ParsedInputFieldResponse(name, location, required, type, null,
                minimum, maximum, minLength, maxLength, null, null, null, List.of());
    }

    private GeneratedTestCase scenario(
            List<GeneratedTestCase> cases,
            TestScenarioType type,
            int index) {
        return cases.stream().filter(testCase -> testCase.scenarioType() == type)
                .toList().get(index);
    }

    private Object bodyValue(GeneratedTestCase testCase, String name) {
        return ((Map<?, ?>) testCase.requestBody()).get(name);
    }
}
