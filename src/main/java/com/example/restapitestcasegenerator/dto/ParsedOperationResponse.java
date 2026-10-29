package com.example.restapitestcasegenerator.dto;

public record ParsedOperationResponse(
        String method,
        String path,
        String summary,
        String operationId) {
}
