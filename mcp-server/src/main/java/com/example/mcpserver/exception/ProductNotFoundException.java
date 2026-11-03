package com.example.mcpserver.exception;

public class ProductNotFoundException extends BackendApiException {

    public ProductNotFoundException(String id) {
        super(404, "No product exists with id '" + id + "'. Use searchProducts to find valid product ids.");
    }
}
