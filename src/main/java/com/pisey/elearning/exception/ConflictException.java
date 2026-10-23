package com.pisey.elearning.exception;

/** Business rule conflict, e.g. a duplicate unique value or deleting a resource that is still in use. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
