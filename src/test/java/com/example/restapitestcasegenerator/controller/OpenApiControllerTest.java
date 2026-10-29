package com.example.restapitestcasegenerator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiControllerTest {

    private static final String ENDPOINT = "/api/test-cases/generate";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsYamlUploadAndReturnsGeneratedScenariosAsJson() throws Exception {
        MockMultipartFile specification = yamlFile("valid-api.yaml", """
                openapi: 3.1.0
                info:
                  title: Accounts API
                  version: 1.0.0
                paths:
                  /accounts:
                    post:
                      operationId: createAccount
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [name]
                              properties:
                                name:
                                  type: string
                                  minLength: 2
                                  maxLength: 4
                      responses:
                        '201':
                          description: Created
                """);

        mockMvc.perform(multipart(ENDPOINT).file(specification))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].operationId").value("createAccount"))
                .andExpect(jsonPath("$[0].scenarioType").value("VALID_INPUT"))
                .andExpect(jsonPath("$[0].requestBody.name").value("xxxx"))
                .andExpect(jsonPath("$[1].scenarioType").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$[3].scenarioType").value("INVALID_TYPE"))
                .andExpect(jsonPath("$[3].requestBody.name").value(123))
                .andExpect(jsonPath("$[4].scenarioType").value("BOUNDARY_VALUE"))
                .andExpect(jsonPath("$[4].requestBody.name").value("xx"));
    }

    @Test
    void returnsConsistentProblemForMalformedFile() throws Exception {
        MockMultipartFile specification = new MockMultipartFile(
                "file", "broken.json", MediaType.APPLICATION_JSON_VALUE,
                "{ not valid JSON".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart(ENDPOINT).file(specification))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid OpenAPI specification"))
                .andExpect(jsonPath("$.code").value("INVALID_OPENAPI_SPECIFICATION"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsConsistentProblemForUnsupportedSwaggerTwoFile() throws Exception {
        MockMultipartFile specification = new MockMultipartFile(
                "file", "legacy-api.yaml", "application/yaml", """
                swagger: "2.0"
                info:
                  title: Legacy API
                  version: 1.0.0
                paths: {}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart(ENDPOINT).file(specification))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unsupported OpenAPI specification"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_OPENAPI_SPECIFICATION"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsConsistentProblemForEmptyUpload() throws Exception {
        MockMultipartFile specification = new MockMultipartFile(
                "file", "empty.yaml", "application/yaml", new byte[0]);

        mockMvc.perform(multipart(ENDPOINT).file(specification))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPENAPI_SPECIFICATION"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsConsistentProblemWhenFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SPECIFICATION_FILE"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    private MockMultipartFile yamlFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/yaml",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
