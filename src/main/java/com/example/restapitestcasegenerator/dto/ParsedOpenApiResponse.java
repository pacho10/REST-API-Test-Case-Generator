package com.example.restapitestcasegenerator.dto;

import java.util.List;

public record ParsedOpenApiResponse(
        String title,
        String version,
        String description,
        List<ParsedOperationResponse> operations,
        List<String> warnings) {
}
