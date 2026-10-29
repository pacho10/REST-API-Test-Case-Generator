package com.example.restapitestcasegenerator.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParsedInputFieldResponse(
        String name,
        InputLocation location,
        boolean required,
        String type,
        String format,
        BigDecimal minimum,
        BigDecimal maximum,
        Integer minLength,
        Integer maxLength,
        Integer minItems,
        Integer maxItems,
        String itemsType,
        List<Object> allowedValues) {

    public ParsedInputFieldResponse {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
