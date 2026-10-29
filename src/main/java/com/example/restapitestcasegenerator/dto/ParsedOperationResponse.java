package com.example.restapitestcasegenerator.dto;

import java.util.List;

public record ParsedOperationResponse(
        String method,
        String path,
        String summary,
        String operationId,
        boolean requestBodyRequired,
        List<ParsedInputFieldResponse> inputs) {

    public ParsedOperationResponse {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
