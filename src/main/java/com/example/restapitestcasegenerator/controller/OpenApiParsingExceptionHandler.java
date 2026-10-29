package com.example.restapitestcasegenerator.controller;

import com.example.restapitestcasegenerator.exception.InvalidOpenApiSpecificationException;
import com.example.restapitestcasegenerator.exception.UnsupportedOpenApiSpecificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice
public class OpenApiParsingExceptionHandler {

    @ExceptionHandler(InvalidOpenApiSpecificationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSpecification(
            InvalidOpenApiSpecificationException exception) {
        return problem(HttpStatus.BAD_REQUEST,
                "Invalid OpenAPI specification",
                "INVALID_OPENAPI_SPECIFICATION",
                "The uploaded file must contain a valid OpenAPI JSON or YAML specification.",
                exception.getErrors());
    }

    @ExceptionHandler(UnsupportedOpenApiSpecificationException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedSpecification(
            UnsupportedOpenApiSpecificationException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT,
                "Unsupported OpenAPI specification",
                "UNSUPPORTED_OPENAPI_SPECIFICATION",
                exception.getMessage(),
                List.of(exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingFile(MissingServletRequestPartException exception) {
        String message = "Multipart part 'file' is required.";
        return problem(HttpStatus.BAD_REQUEST,
                "Missing specification file",
                "MISSING_SPECIFICATION_FILE",
                message,
                List.of(message));
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            List<String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).body(problem);
    }
}
