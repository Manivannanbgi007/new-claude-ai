package com.example.stock.messaging;

/** A message that can never be processed (not valid Avro, unexpected type). Goes straight to the DLT. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
