package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
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
        return parseSpecification(readSampleSpecification());
    }

    public ParsedOpenApiResponse parseSpecification(String specification) {
        if (specification == null || specification.isBlank()) {
            throw new InvalidOpenApiSpecificationException(
                    List.of("The specification must not be empty."));
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);

        SwaggerParseResult parseResult = new OpenAPIV3Parser()
                .readContents(specification, null, options);
        OpenAPI openApi = parseResult.getOpenAPI();
        if (openApi == null) {
            List<String> errors = parseResult.getMessages();
            if (errors == null || errors.isEmpty()) {
                errors = List.of("The document could not be parsed as an OpenAPI specification.");
            }
            throw new InvalidOpenApiSpecificationException(errors);
        }

        List<ParsedOperationResponse> operations = extractOperations(openApi);
        List<String> warnings = parseResult.getMessages() == null
                ? List.of()
                : List.copyOf(parseResult.getMessages());
        return new ParsedOpenApiResponse(
                openApi.getInfo() == null ? null : openApi.getInfo().getTitle(),
                openApi.getInfo() == null ? null : openApi.getInfo().getVersion(),
                openApi.getInfo() == null ? null : openApi.getInfo().getDescription(),
                operations,
                warnings);
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
        if (openApi.getPaths() == null) {
            return List.of();
        }
        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            if (pathEntry.getValue() == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry
                    : pathEntry.getValue().readOperationsMap().entrySet()) {
                Operation operation = operationEntry.getValue();
                if (operation == null) {
                    continue;
                }
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
