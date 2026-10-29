package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.dto.InputLocation;
import com.example.restapitestcasegenerator.dto.ParsedInputFieldResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import com.example.restapitestcasegenerator.exception.UnsupportedOpenApiSpecificationException;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OpenApiSpecificationService {

    private static final String SAMPLE_SPECIFICATION = "openapi/sample-pet-api.yaml";
    private static final Pattern OPENAPI_VERSION = Pattern.compile(
            "(?:\\\"openapi\\\"\\s*:\\s*\\\"|^\\s*openapi\\s*:\\s*['\\\"]?)([0-9]+(?:\\.[0-9]+){1,2})",
            Pattern.MULTILINE);
    private static final Pattern SWAGGER_VERSION = Pattern.compile(
            "(?:\\\"swagger\\\"\\s*:\\s*\\\"|^\\s*swagger\\s*:\\s*['\\\"]?)([0-9]+(?:\\.[0-9]+){1,2})",
            Pattern.MULTILINE);

    public ParsedOpenApiResponse parseSampleSpecification() {
        return parseSpecification(readSampleSpecification());
    }

    public ParsedOpenApiResponse parseSpecification(String specification) {
        if (specification == null || specification.isBlank()) {
            throw new InvalidOpenApiSpecificationException(
                    List.of("The specification must not be empty."));
        }

        String swaggerVersion = findVersion(SWAGGER_VERSION, specification);
        if (swaggerVersion != null) {
            throw new UnsupportedOpenApiSpecificationException(
                    "Swagger " + swaggerVersion + " is not supported. Submit an OpenAPI 3.0 or 3.1 specification.");
        }
        String declaredOpenApiVersion = findVersion(OPENAPI_VERSION, specification);
        if (declaredOpenApiVersion != null && !isSupportedOpenApiVersion(declaredOpenApiVersion)) {
            throw unsupportedVersion(declaredOpenApiVersion);
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);

        SwaggerParseResult parseResult;
        try {
            parseResult = new OpenAPIV3Parser().readContents(specification, null, options);
        } catch (RuntimeException exception) {
            String error = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "The document could not be parsed as OpenAPI JSON or YAML."
                    : exception.getMessage();
            throw new InvalidOpenApiSpecificationException(List.of(error));
        }
        OpenAPI openApi = parseResult.getOpenAPI();
        if (openApi == null) {
            List<String> errors = parseResult.getMessages();
            if (errors == null || errors.isEmpty()) {
                errors = List.of("The document could not be parsed as an OpenAPI specification.");
            }
            throw new InvalidOpenApiSpecificationException(errors);
        }
        if (!isSupportedOpenApiVersion(openApi.getOpenapi())) {
            throw unsupportedVersion(openApi.getOpenapi());
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

    private String findVersion(Pattern pattern, String specification) {
        Matcher matcher = pattern.matcher(specification);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isSupportedOpenApiVersion(String version) {
        return version != null && (version.startsWith("3.0.") || version.startsWith("3.1."));
    }

    private UnsupportedOpenApiSpecificationException unsupportedVersion(String version) {
        return new UnsupportedOpenApiSpecificationException(
                "OpenAPI version " + version + " is not supported. Submit an OpenAPI 3.0 or 3.1 specification.");
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
                        operation.getOperationId(),
                        hasRequiredRequestBody(openApi, operation),
                        extractInputs(openApi, pathEntry.getValue(), operation)));
            }
        }
        return List.copyOf(operations);
    }

    private List<ParsedInputFieldResponse> extractInputs(
            OpenAPI openApi,
            PathItem pathItem,
            Operation operation) {
        List<ParsedInputFieldResponse> inputs = new ArrayList<>();
        Map<String, Parameter> parameters = new LinkedHashMap<>();
        addParameters(parameters, pathItem.getParameters());
        addParameters(parameters, operation.getParameters());

        for (Parameter parameter : parameters.values()) {
            String parameterLocation = parameter.getIn();
            InputLocation location = parameterLocation == null ? null : switch (parameterLocation) {
                case "path" -> InputLocation.PATH;
                case "query" -> InputLocation.QUERY;
                case "header" -> InputLocation.HEADER;
                case "cookie" -> InputLocation.COOKIE;
                default -> null;
            };
            if (location != null) {
                inputs.add(toInputField(openApi, parameter.getName(), location,
                        Boolean.TRUE.equals(parameter.getRequired()), parameter.getSchema()));
            }
        }

        RequestBody requestBody = resolveRequestBody(openApi, operation.getRequestBody());
        Schema<?> bodySchema = getRequestBodySchema(requestBody);
        bodySchema = resolveLocalSchema(openApi, bodySchema);
        if (bodySchema != null && bodySchema.getProperties() instanceof Map<?, ?> properties
                && !properties.isEmpty()) {
            Set<String> requiredFields = bodySchema.getRequired() == null
                    ? Set.of()
                    : new LinkedHashSet<>(bodySchema.getRequired());
            for (Map.Entry<?, ?> property : properties.entrySet()) {
                if (property.getValue() instanceof Schema<?> propertySchema) {
                    String propertyName = String.valueOf(property.getKey());
                    addBodyField(openApi, propertyName, requiredFields.contains(propertyName),
                            propertySchema, inputs, 0);
                }
            }
        } else if (bodySchema != null) {
            inputs.add(toInputField(openApi, "$", InputLocation.REQUEST_BODY,
                    false, bodySchema));
        }
        return List.copyOf(inputs);
    }

    private void addBodyField(
            OpenAPI openApi,
            String name,
            boolean required,
            Schema<?> unresolvedSchema,
            List<ParsedInputFieldResponse> inputs,
            int depth) {
        Schema<?> schema = resolveLocalSchema(openApi, unresolvedSchema);
        inputs.add(toInputField(openApi, name, InputLocation.REQUEST_BODY, required, schema));
        if (depth >= 8 || schema == null
                || !(schema.getProperties() instanceof Map<?, ?> properties)) {
            return;
        }
        Set<String> requiredChildren = schema.getRequired() == null
                ? Set.of()
                : new LinkedHashSet<>(schema.getRequired());
        for (Map.Entry<?, ?> property : properties.entrySet()) {
            if (property.getValue() instanceof Schema<?> propertySchema) {
                String childName = String.valueOf(property.getKey());
                addBodyField(openApi, name + "." + childName,
                        requiredChildren.contains(childName), propertySchema, inputs, depth + 1);
            }
        }
    }

    private void addParameters(Map<String, Parameter> target, List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            target.put(parameter.getIn() + ":" + parameter.getName(), parameter);
        }
    }

    private ParsedInputFieldResponse toInputField(
            OpenAPI openApi,
            String name,
            InputLocation location,
            boolean required,
            Schema<?> unresolvedSchema) {
        Schema<?> schema = resolveLocalSchema(openApi, unresolvedSchema);
        if (schema == null) {
            return new ParsedInputFieldResponse(name, location, required, null, null,
                    null, null, null, null, null, null, null, List.of());
        }

        Schema<?> items = schema.getItems();
        List<Object> allowedValues = schema.getEnum() == null
                ? List.of()
                : new ArrayList<>(schema.getEnum());
        return new ParsedInputFieldResponse(
                name,
                location,
                required,
                getSchemaType(schema),
                schema.getFormat(),
                schema.getMinimum(),
                schema.getMaximum(),
                schema.getMinLength(),
                schema.getMaxLength(),
                schema.getMinItems(),
                schema.getMaxItems(),
                items == null ? null : getSchemaType(items),
                allowedValues);
    }

    private String getSchemaType(Schema<?> schema) {
        if (schema.getType() != null) {
            return schema.getType();
        }
        if (schema.getTypes() == null) {
            return null;
        }
        List<String> nonNullTypes = schema.getTypes().stream()
                .filter(type -> !"null".equals(type))
                .toList();
        return nonNullTypes.size() == 1 ? nonNullTypes.getFirst() : null;
    }

    private boolean hasRequiredRequestBody(OpenAPI openApi, Operation operation) {
        RequestBody requestBody = resolveRequestBody(openApi, operation.getRequestBody());
        return requestBody != null && Boolean.TRUE.equals(requestBody.getRequired());
    }

    private RequestBody resolveRequestBody(OpenAPI openApi, RequestBody requestBody) {
        if (requestBody == null || requestBody.get$ref() == null
                || openApi.getComponents() == null
                || openApi.getComponents().getRequestBodies() == null) {
            return requestBody;
        }
        String prefix = "#/components/requestBodies/";
        if (!requestBody.get$ref().startsWith(prefix)) {
            return requestBody;
        }
        String name = decodePointerToken(requestBody.get$ref().substring(prefix.length()));
        return openApi.getComponents().getRequestBodies().getOrDefault(name, requestBody);
    }

    private Schema<?> getRequestBodySchema(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null
                || requestBody.getContent().isEmpty()) {
            return null;
        }
        MediaType json = requestBody.getContent().get("application/json");
        if (json != null) {
            return json.getSchema();
        }
        return requestBody.getContent().values().stream()
                .filter(mediaType -> mediaType != null && mediaType.getSchema() != null)
                .map(MediaType::getSchema)
                .findFirst()
                .orElse(null);
    }

    private Schema<?> resolveLocalSchema(OpenAPI openApi, Schema<?> schema) {
        if (schema == null || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        Schema<?> resolved = schema;
        for (int depth = 0; depth < 8 && resolved.get$ref() != null
                && resolved.get$ref().startsWith(prefix); depth++) {
            String name = decodePointerToken(resolved.get$ref().substring(prefix.length()));
            Schema<?> target = openApi.getComponents().getSchemas().get(name);
            if (target == null || target == resolved) {
                break;
            }
            resolved = target;
        }
        return resolved;
    }

    private String decodePointerToken(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }
}
