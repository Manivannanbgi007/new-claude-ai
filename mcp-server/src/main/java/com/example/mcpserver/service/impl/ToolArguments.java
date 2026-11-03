package com.example.mcpserver.service.impl;

import com.example.mcpserver.exception.InvalidToolArgumentException;

/** Small shared checks for tool arguments sent by the LLM. */
final class ToolArguments {

    private ToolArguments() {
    }

    /** Missing quantity means 1; the chatbot may order at most maxQuantity units per product. */
    static int quantity(Integer quantity, int maxQuantity) {
        int value = quantity == null || quantity == 0 ? 1 : quantity;
        if (value < 1 || value > maxQuantity) {
            throw new InvalidToolArgumentException("quantity must be between 1 and " + maxQuantity + ".");
        }
        return value;
    }
}
