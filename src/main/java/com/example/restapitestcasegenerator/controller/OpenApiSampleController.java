package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.dto.ParsedOpenApiResponse;
import com.example.restapitestcasegenerator.service.OpenApiSpecificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
