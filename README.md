# REST API Test Case Generator

A Java and Spring Boot application for generating test cases from OpenAPI specifications.

## Requirements

- Java 25
- Gradle Wrapper (included)

## Run

```bash
./gradlew bootRun
```

On Windows, run `gradlew.bat bootRun`.

The application includes a sample OpenAPI specification and a parsing endpoint:

```text
GET /api/openapi/sample
```

The response contains the specification title, version, description, operations, and parser warnings. This initial sample is read from the classpath and is not stored in a database.

Submit a raw OpenAPI JSON or YAML document to parse it:

```bash
curl -X POST http://localhost:8080/api/openapi/parse \
  -H "Content-Type: application/json" \
  --data-binary @openapi.json
```

For YAML, use `Content-Type: application/yaml` and pass the YAML file as the request body. Invalid or empty specifications return HTTP 400 with parser details.

Generate deterministic test scenarios directly from a valid specification:

```bash
curl -X POST http://localhost:8080/api/openapi/test-cases \
  -H "Content-Type: application/yaml" \
  --data-binary @openapi.yaml
```

The generator produces valid-input, missing-required-field, invalid-type, and supported boundary-value scenarios for each parsed operation.
Boundary scenarios are generated for numeric `minimum`/`maximum`, string `minLength`/`maxLength`, and array `minItems`/`maxItems` constraints. Sample values are stable across runs.
