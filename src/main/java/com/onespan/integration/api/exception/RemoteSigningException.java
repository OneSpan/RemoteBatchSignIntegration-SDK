package com.onespan.integration.api.exception;

/**
 * Custom exception to manage error code
 */
public class RemoteSigningException extends RuntimeException {

    //Ignore serialization warning
    @SuppressWarnings("squid:S1948")
    private final BaseErrorCode errorCode;

    public RemoteSigningException(String message, BaseErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseErrorCode getErrorCode() {
        return errorCode;
    }

}
