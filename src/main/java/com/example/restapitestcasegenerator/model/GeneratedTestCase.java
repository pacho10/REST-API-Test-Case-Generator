package com.example.restapitestcasegenerator.model;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeneratedTestCase(
        String operationId,
        String method,
        String path,
        TestScenarioType scenarioType,
        String description,
        Map<String, Object> pathParameters,
        Map<String, Object> queryParameters,
        Map<String, Object> headers,
        Map<String, Object> cookies,
        Object requestBody) {

    public GeneratedTestCase {
        pathParameters = immutableCopy(pathParameters);
        queryParameters = immutableCopy(queryParameters);
        headers = immutableCopy(headers);
        cookies = immutableCopy(cookies);
        requestBody = immutableBodyCopy(requestBody);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableBodyCopy(Object body) {
        return immutableValue(body);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) ->
                    copy.put(String.valueOf(key), immutableValue(nestedValue)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream().map(GeneratedTestCase::immutableValue)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
        }
        return value;
    }
}
