package com.example.restapitestcasegenerator.exception;

import java.util.List;

public class InvalidOpenApiSpecificationException extends RuntimeException {

    private final List<String> errors;

    public InvalidOpenApiSpecificationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
