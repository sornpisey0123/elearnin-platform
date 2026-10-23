package com.pisey.elearning.exception;

/** A call to an external system (Keycloak Admin API) failed for a reason the client cannot fix. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
