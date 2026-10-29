package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import com.example.restapitestcasegenerator.model.GeneratedTestCase;
import com.example.restapitestcasegenerator.service.OpenApiSpecificationService;
import com.example.restapitestcasegenerator.service.TestCaseGeneratorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class OpenApiController {

    private final OpenApiSpecificationService specificationService;
    private final TestCaseGeneratorService testCaseGeneratorService;

    public OpenApiController(
            OpenApiSpecificationService specificationService,
            TestCaseGeneratorService testCaseGeneratorService) {
        this.specificationService = specificationService;
        this.testCaseGeneratorService = testCaseGeneratorService;
    }

    @GetMapping("/api/openapi/sample")
    public ParsedOpenApiResponse parseSampleSpecification() {
        return specificationService.parseSampleSpecification();
    }

    @PostMapping(
            path = "/api/openapi/parse",
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
            path = "/api/openapi/test-cases",
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

    @PostMapping(
            path = "/api/test-cases/generate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GeneratedTestCase> generateTestCasesFromFile(
            @RequestPart("file") MultipartFile file) {
        ParsedOpenApiResponse parsedApi = specificationService.parseSpecification(
                readUploadedSpecification(file));
        return testCaseGeneratorService.generateTestCases(parsedApi);
    }

    private String readUploadedSpecification(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidOpenApiSpecificationException(
                    List.of("The uploaded specification file must not be empty."));
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InvalidOpenApiSpecificationException(
                    List.of("The uploaded specification file could not be read."));
        }
    }
}
