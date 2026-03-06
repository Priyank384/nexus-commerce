package com.example.nexusCommerce.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.nexusCommerce.exceptions.ProductNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public ResponseEntity<String> handleProductNotFound(ProductNotFoundException ex){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }
}
