package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.service.OpenApiSpecificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiSampleController {

    private final OpenApiSpecificationService specificationService;

    public OpenApiSampleController(OpenApiSpecificationService specificationService) {
        this.specificationService = specificationService;
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
}
