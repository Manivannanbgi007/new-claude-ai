package com.example.mcpclient.exception;

/** The LLM call finished, but produced no usable answer. */
public class ChatProcessingException extends RuntimeException {

    public ChatProcessingException(String message) {
        super(message);
    }
}
