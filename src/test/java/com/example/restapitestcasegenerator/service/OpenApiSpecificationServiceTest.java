package com.example.restapitestcasegenerator.service;

import com.example.restapitestcasegenerator.dto.InputLocation;
import com.example.restapitestcasegenerator.dto.ParsedInputFieldResponse;
import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.dto.ParsedOperationResponse;
import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiSpecificationServiceTest {

    private final OpenApiSpecificationService service = new OpenApiSpecificationService();

    @Test
    void parsesJsonMetadataParametersAndRequestBodyConstraints() {
        ParsedOpenApiResponse parsed = service.parseSpecification("""
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "User API",
                    "version": "2.0",
                    "description": "Manage users"
                  },
                  "paths": {
                    "/users": {
                      "post": {
                        "operationId": "createUser",
                        "summary": "Create a user",
                        "parameters": [
                          {
                            "name": "X-Trace-Id",
                            "in": "header",
                            "required": true,
                            "schema": { "type": "string", "minLength": 3 }
                          }
                        ],
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["name"],
                                "properties": {
                                  "name": {
                                    "type": "string",
                                    "minLength": 2,
                                    "maxLength": 40
                                  },
                                  "age": {
                                    "type": "integer",
                                    "minimum": 0,
                                    "maximum": 120
                                  }
                                }
                              }
                            }
                          }
                        },
                        "responses": { "201": { "description": "Created" } }
                      }
                    }
                  }
                }
                """);

        assertThat(parsed.title()).isEqualTo("User API");
        assertThat(parsed.version()).isEqualTo("2.0");
        assertThat(parsed.description()).isEqualTo("Manage users");
        assertThat(parsed.operations()).hasSize(1);

        ParsedOperationResponse operation = parsed.operations().getFirst();
        assertThat(operation.method()).isEqualTo("POST");
        assertThat(operation.path()).isEqualTo("/users");
        assertThat(operation.summary()).isEqualTo("Create a user");
        assertThat(operation.operationId()).isEqualTo("createUser");
        assertThat(operation.requestBodyRequired()).isTrue();

        ParsedInputFieldResponse traceHeader = input(operation, "X-Trace-Id");
        assertThat(traceHeader.location()).isEqualTo(InputLocation.HEADER);
        assertThat(traceHeader.required()).isTrue();
        assertThat(traceHeader.minLength()).isEqualTo(3);

        ParsedInputFieldResponse name = input(operation, "name");
        assertThat(name.location()).isEqualTo(InputLocation.REQUEST_BODY);
        assertThat(name.required()).isTrue();
        assertThat(name.type()).isEqualTo("string");
        assertThat(name.minLength()).isEqualTo(2);
        assertThat(name.maxLength()).isEqualTo(40);

        ParsedInputFieldResponse age = input(operation, "age");
        assertThat(age.type()).isEqualTo("integer");
        assertThat(age.minimum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(age.maximum()).isEqualByComparingTo(new BigDecimal("120"));
    }

    @Test
    void parsesYamlAndResolvesLocalRequestBodyAndSchemaReferences() {
        ParsedOpenApiResponse parsed = service.parseSpecification("""
                openapi: 3.1.0
                info:
                  title: Order API
                  version: 1.0.0
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      requestBody:
                        $ref: '#/components/requestBodies/OrderPayload'
                      responses:
                        '201':
                          description: Created
                components:
                  requestBodies:
                    OrderPayload:
                      required: true
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Order'
                  schemas:
                    Order:
                      type: object
                      required:
                        - code
                      properties:
                        code:
                          type: string
                          enum: [ORD-001, ORD-002]
                        amount:
                          type: number
                          minimum: 0.01
                          maximum: 500
                """);

        ParsedOperationResponse operation = parsed.operations().getFirst();
        assertThat(operation.requestBodyRequired()).isTrue();
        assertThat(operation.inputs()).hasSize(2);

        ParsedInputFieldResponse code = input(operation, "code");
        assertThat(code.required()).isTrue();
        assertThat(code.allowedValues()).containsExactly("ORD-001", "ORD-002");

        ParsedInputFieldResponse amount = input(operation, "amount");
        assertThat(amount.type()).isEqualTo("number");
        assertThat(amount.minimum()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(amount.maximum()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void parsesBundledSampleSpecification() {
        ParsedOpenApiResponse parsed = service.parseSampleSpecification();

        assertThat(parsed.title()).isEqualTo("Sample Pet API");
        assertThat(parsed.operations()).hasSize(3);

        ParsedOperationResponse createPet = parsed.operations().stream()
                .filter(operation -> "createPet".equals(operation.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(createPet.requestBodyRequired()).isTrue();
        assertThat(input(createPet, "name").required()).isTrue();
        assertThat(input(createPet, "name").minLength()).isEqualTo(1);
        assertThat(input(createPet, "id").minimum()).isEqualByComparingTo(BigDecimal.ONE);

        ParsedOperationResponse getPet = parsed.operations().stream()
                .filter(operation -> "getPet".equals(operation.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(input(getPet, "petId").location()).isEqualTo(InputLocation.PATH);
        assertThat(input(getPet, "petId").required()).isTrue();
    }

    @Test
    void rejectsEmptySpecifications() {
        InvalidOpenApiSpecificationException blank = assertThrows(
                InvalidOpenApiSpecificationException.class,
                () -> service.parseSpecification("  \n  "));
        InvalidOpenApiSpecificationException missing = assertThrows(
                InvalidOpenApiSpecificationException.class,
                () -> service.parseSpecification(null));

        assertThat(blank.getErrors()).containsExactly("The specification must not be empty.");
        assertThat(missing.getErrors()).containsExactly("The specification must not be empty.");
    }

    @Test
    void rejectsMalformedSpecificationsWithParserDetails() {
        InvalidOpenApiSpecificationException exception = assertThrows(
                InvalidOpenApiSpecificationException.class,
                () -> service.parseSpecification("{ this is not an OpenAPI document }"));

        assertThat(exception.getErrors()).isNotEmpty();
    }

    private ParsedInputFieldResponse input(ParsedOperationResponse operation, String name) {
        return operation.inputs().stream()
                .filter(input -> name.equals(input.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No parsed input named " + name));
    }
}
