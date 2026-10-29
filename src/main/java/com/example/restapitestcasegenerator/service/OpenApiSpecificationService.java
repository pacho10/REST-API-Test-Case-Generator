package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenApiSpecificationService {

    private static final String SAMPLE_SPECIFICATION = "openapi/sample-pet-api.yaml";

    public ParsedOpenApiResponse parseSampleSpecification() {
        String specification = readSampleSpecification();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        SwaggerParseResult parseResult = new OpenAPIV3Parser()
                .readContents(specification, null, options);
        OpenAPI openApi = parseResult.getOpenAPI();
        if (openApi == null) {
            throw new IllegalStateException("The bundled OpenAPI sample could not be parsed: "
                    + String.join("; ", parseResult.getMessages()));
        }

        List<ParsedOperationResponse> operations = extractOperations(openApi);
        return new ParsedOpenApiResponse(
                openApi.getInfo().getTitle(),
                openApi.getInfo().getVersion(),
                openApi.getInfo().getDescription(),
                operations,
                parseResult.getMessages());
    }

    private String readSampleSpecification() {
        try (var inputStream = new ClassPathResource(SAMPLE_SPECIFICATION).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the bundled OpenAPI sample", exception);
        }
    }

    private List<ParsedOperationResponse> extractOperations(OpenAPI openApi) {
        List<ParsedOperationResponse> operations = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry
                    : pathEntry.getValue().readOperationsMap().entrySet()) {
                Operation operation = operationEntry.getValue();
                operations.add(new ParsedOperationResponse(
                        operationEntry.getKey().name(),
                        pathEntry.getKey(),
                        operation.getSummary(),
                        operation.getOperationId()));
            }
        }
        return List.copyOf(operations);
    }
}
