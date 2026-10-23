package com.pisey.elearning.exception;

/** Authenticated caller is not allowed to act on this resource, e.g. an instructor editing another instructor's course. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
