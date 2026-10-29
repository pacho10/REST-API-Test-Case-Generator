package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OpenApiParsingExceptionHandler {

    @ExceptionHandler(InvalidOpenApiSpecificationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSpecification(
            InvalidOpenApiSpecificationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body must contain a valid OpenAPI JSON or YAML specification.");
        problem.setTitle("Invalid OpenAPI specification");
        problem.setProperty("errors", exception.getErrors());
        return ResponseEntity.badRequest().body(problem);
    }
}
