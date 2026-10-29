package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.InputLocation;
import com.example.restapitestcasegenerator.dto.ParsedInputFieldResponse;
import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import com.example.restapitestcasegenerator.model.GeneratedTestCase;
import com.example.restapitestcasegenerator.model.TestScenarioType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TestCaseGeneratorService {

    public List<GeneratedTestCase> generateTestCases(ParsedOpenApiResponse parsedApi) {
        if (parsedApi == null) {
            throw new IllegalArgumentException("A parsed OpenAPI result is required.");
        }
        if (parsedApi.operations() == null) {
            return List.of();
        }
        return parsedApi.operations().stream()
                .flatMap(operation -> generateTestCases(operation).stream())
                .toList();
    }

    public List<GeneratedTestCase> generateTestCases(ParsedOperationResponse operation) {
        if (operation == null) {
            throw new IllegalArgumentException("A parsed operation is required.");
        }

        List<ParsedInputFieldResponse> inputs = operation.inputs() == null
                ? List.of()
                : operation.inputs();
        InputValues validInputs = createValidInputs(inputs);
        if (operation.requestBodyRequired() && validInputs.requestBody == null) {
            validInputs.requestBody = new LinkedHashMap<String, Object>();
        }
        List<GeneratedTestCase> generated = new ArrayList<>();
        generated.add(createCase(operation, TestScenarioType.VALID_INPUT,
                "Valid deterministic input for " + operation.operationId(), validInputs));

        if (operation.requestBodyRequired()) {
            InputValues missingBody = validInputs.copy();
            missingBody.requestBody = null;
            generated.add(createCase(operation, TestScenarioType.MISSING_REQUIRED_FIELD,
                    "Omit the required request body", missingBody));
        }

        for (ParsedInputFieldResponse input : inputs) {
            if (input.required()) {
                InputValues missing = validInputs.copy();
                removeInput(missing, input);
                generated.add(createCase(operation, TestScenarioType.MISSING_REQUIRED_FIELD,
                        "Omit required " + input.location().name().toLowerCase(Locale.ROOT)
                                + " field '" + input.name() + "'", missing));
            }
        }

        for (ParsedInputFieldResponse input : inputs) {
            Object invalidValue = invalidValue(input.type());
            if (invalidValue != null) {
                InputValues invalid = validInputs.copy();
                setInput(invalid, input, invalidValue);
                generated.add(createCase(operation, TestScenarioType.INVALID_TYPE,
                        "Use an invalid type for '" + input.name() + "'", invalid));
            }
        }

        for (ParsedInputFieldResponse input : inputs) {
            addBoundaryCases(generated, operation, validInputs, input);
        }

        return List.copyOf(generated);
    }

    private InputValues createValidInputs(List<ParsedInputFieldResponse> inputs) {
        InputValues values = new InputValues();
        for (ParsedInputFieldResponse input : inputs) {
            setInput(values, input, sampleValue(input));
        }
        return values;
    }

    private Object sampleValue(ParsedInputFieldResponse input) {
        if (!input.allowedValues().isEmpty()) {
            return input.allowedValues().get(0);
        }
        String type = normalizedType(input.type());
        return switch (type) {
            case "string" -> sampleString(input);
            case "integer" -> numericSample(input, true);
            case "number" -> numericSample(input, false);
            case "boolean" -> true;
            case "array" -> sampleArray(input, itemCount(input));
            case "object" -> new LinkedHashMap<String, Object>();
            default -> "sample";
        };
    }

    private String sampleString(ParsedInputFieldResponse input) {
        int length = Math.max(8, input.minLength() == null ? 0 : input.minLength());
        if (input.maxLength() != null && length > input.maxLength()) {
            length = Math.max(0, input.maxLength());
        }
        String value = switch (input.format() == null ? "" : input.format()) {
            case "date" -> "2025-01-01";
            case "date-time" -> "2025-01-01T00:00:00Z";
            case "email" -> "sample@example.test";
            case "uuid" -> "00000000-0000-0000-0000-000000000001";
            default -> "x".repeat(length);
        };
        return value;
    }

    private Object numericSample(ParsedInputFieldResponse input, boolean integer) {
        BigDecimal value = integer ? BigDecimal.ONE : new BigDecimal("1.5");
        BigDecimal minimum = integerValue(input.minimum(), true, integer);
        BigDecimal maximum = integerValue(input.maximum(), false, integer);
        if (minimum != null && value.compareTo(minimum) < 0) {
            value = minimum;
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            value = maximum;
        }
        return toNumber(value, integer);
    }

    private Object invalidValue(String rawType) {
        return switch (normalizedType(rawType)) {
            case "string" -> 123;
            case "integer", "number" -> "not-a-number";
            case "boolean" -> "not-a-boolean";
            case "array" -> "not-an-array";
            case "object" -> "not-an-object";
            default -> null;
        };
    }

    private void addBoundaryCases(
            List<GeneratedTestCase> generated,
            ParsedOperationResponse operation,
            InputValues validInputs,
            ParsedInputFieldResponse input) {
        String type = normalizedType(input.type());
        if ((type.equals("integer") || type.equals("number")) && input.minimum() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "minimum", toNumber(integerValue(input.minimum(), true,
                            type.equals("integer")), type.equals("integer")));
        }
        if ((type.equals("integer") || type.equals("number")) && input.maximum() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "maximum", toNumber(integerValue(input.maximum(), false,
                            type.equals("integer")), type.equals("integer")));
        }
        if (type.equals("string") && input.minLength() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "minimum length", "x".repeat(Math.max(0, input.minLength())));
        }
        if (type.equals("string") && input.maxLength() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "maximum length", "x".repeat(Math.max(0, input.maxLength())));
        }
        if (type.equals("array") && input.minItems() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "minimum item count", sampleArray(input, Math.max(0, input.minItems())));
        }
        if (type.equals("array") && input.maxItems() != null) {
            addBoundaryCase(generated, operation, validInputs, input,
                    "maximum item count", sampleArray(input, Math.max(0, input.maxItems())));
        }
    }

    private void addBoundaryCase(
            List<GeneratedTestCase> generated,
            ParsedOperationResponse operation,
            InputValues validInputs,
            ParsedInputFieldResponse input,
            String boundary,
            Object value) {
        InputValues boundaryInputs = validInputs.copy();
        setInput(boundaryInputs, input, value);
        generated.add(createCase(operation, TestScenarioType.BOUNDARY_VALUE,
                "Set '" + input.name() + "' to its " + boundary, boundaryInputs));
    }

    private int itemCount(ParsedInputFieldResponse input) {
        int count = Math.max(1, input.minItems() == null ? 0 : input.minItems());
        if (input.maxItems() != null && count > input.maxItems()) {
            count = Math.max(0, input.maxItems());
        }
        return count;
    }

    private List<Object> sampleArray(ParsedInputFieldResponse input, int count) {
        List<Object> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            items.add(scalarSample(input.itemsType()));
        }
        return items;
    }

    private Object scalarSample(String type) {
        return switch (normalizedType(type)) {
            case "integer" -> 1;
            case "number" -> new BigDecimal("1.5");
            case "boolean" -> true;
            case "object" -> new LinkedHashMap<String, Object>();
            default -> "sample";
        };
    }

    private BigDecimal integerValue(BigDecimal value, boolean minimum, boolean integer) {
        if (value == null) {
            return null;
        }
        if (!integer) {
            return value;
        }
        return value.setScale(0, minimum ? RoundingMode.CEILING : RoundingMode.FLOOR);
    }

    private Object toNumber(BigDecimal value, boolean integer) {
        if (value == null) {
            return null;
        }
        if (!integer) {
            return value.stripTrailingZeros();
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException ignored) {
            try {
                return value.longValueExact();
            } catch (ArithmeticException tooLargeForLong) {
                return value;
            }
        }
    }

    private String normalizedType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    private void setInput(InputValues values, ParsedInputFieldResponse input, Object value) {
        switch (input.location()) {
            case PATH -> values.pathParameters.put(input.name(), value);
            case QUERY -> values.queryParameters.put(input.name(), value);
            case HEADER -> values.headers.put(input.name(), value);
            case COOKIE -> values.cookies.put(input.name(), value);
            case REQUEST_BODY -> {
                if ("$".equals(input.name())) {
                    values.requestBody = value;
                } else {
                    Map<String, Object> body = ensureBodyMap(values);
                    putNested(body, input.name(), value);
                }
            }
        }
    }

    private void removeInput(InputValues values, ParsedInputFieldResponse input) {
        switch (input.location()) {
            case PATH -> values.pathParameters.remove(input.name());
            case QUERY -> values.queryParameters.remove(input.name());
            case HEADER -> values.headers.remove(input.name());
            case COOKIE -> values.cookies.remove(input.name());
            case REQUEST_BODY -> {
                if ("$".equals(input.name())) {
                    values.requestBody = null;
                } else if (values.requestBody instanceof Map<?, ?> body) {
                    removeNested(castBodyMap(body), input.name());
                }
            }
        }
    }

    private Map<String, Object> ensureBodyMap(InputValues values) {
        if (!(values.requestBody instanceof Map<?, ?>)) {
            values.requestBody = new LinkedHashMap<String, Object>();
        }
        return castBodyMap((Map<?, ?>) values.requestBody);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castBodyMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private void putNested(Map<String, Object> target, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = current.get(segments[index]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(segments[index], child);
            }
            current = castBodyMap((Map<?, ?>) child);
        }
        current.put(segments[segments.length - 1], value);
    }

    private void removeNested(Map<String, Object> target, String path) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = current.get(segments[index]);
            if (!(child instanceof Map<?, ?>)) {
                return;
            }
            current = castBodyMap((Map<?, ?>) child);
        }
        current.remove(segments[segments.length - 1]);
    }

    private GeneratedTestCase createCase(
            ParsedOperationResponse operation,
            TestScenarioType type,
            String description,
            InputValues values) {
        return new GeneratedTestCase(
                operation.operationId(),
                operation.method(),
                operation.path(),
                type,
                description,
                values.pathParameters,
                values.queryParameters,
                values.headers,
                values.cookies,
                values.requestBody);
    }

    private static final class InputValues {
        private final Map<String, Object> pathParameters = new LinkedHashMap<>();
        private final Map<String, Object> queryParameters = new LinkedHashMap<>();
        private final Map<String, Object> headers = new LinkedHashMap<>();
        private final Map<String, Object> cookies = new LinkedHashMap<>();
        private Object requestBody;

        private InputValues copy() {
            InputValues copy = new InputValues();
            copy.pathParameters.putAll(pathParameters);
            copy.queryParameters.putAll(queryParameters);
            copy.headers.putAll(headers);
            copy.cookies.putAll(cookies);
            copy.requestBody = deepCopy(requestBody);
            return copy;
        }

        private static Object deepCopy(Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), deepCopy(nestedValue)));
                return copy;
            }
            if (value instanceof List<?> list) {
                return new ArrayList<>(list.stream().map(InputValues::deepCopy).toList());
            }
            return value;
        }
    }
}
