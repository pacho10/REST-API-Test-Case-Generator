package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.model.GeneratedTestCase;
import com.example.restapitestcasegenerator.service.OpenApiSpecificationService;
import com.example.restapitestcasegenerator.service.TestCaseGeneratorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiSampleController {

    private final OpenApiSpecificationService specificationService;
    private final TestCaseGeneratorService testCaseGeneratorService;

    public OpenApiSampleController(
            OpenApiSpecificationService specificationService,
            TestCaseGeneratorService testCaseGeneratorService) {
        this.specificationService = specificationService;
        this.testCaseGeneratorService = testCaseGeneratorService;
    }

    @GetMapping("/sample")
    public ParsedOpenApiResponse parseSampleSpecification() {
        return specificationService.parseSampleSpecification();
    }

    @PostMapping(
            path = "/parse",
            consumes = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "application/yaml",
                    "application/x-yaml",
                    "text/yaml",
                    MediaType.TEXT_PLAIN_VALUE
            })
    public ParsedOpenApiResponse parseSpecification(@RequestBody byte[] specification) {
        return specificationService.parseSpecification(
                new String(specification, StandardCharsets.UTF_8));
    }

    @PostMapping(
            path = "/test-cases",
            consumes = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "application/yaml",
                    "application/x-yaml",
                    "text/yaml",
                    MediaType.TEXT_PLAIN_VALUE
            })
    public List<GeneratedTestCase> generateTestCases(@RequestBody byte[] specification) {
        ParsedOpenApiResponse parsedApi = specificationService.parseSpecification(
                new String(specification, StandardCharsets.UTF_8));
        return testCaseGeneratorService.generateTestCases(parsedApi);
    }
}
