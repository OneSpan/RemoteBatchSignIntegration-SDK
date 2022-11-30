package com.onespan.integration.api.exception;

public class SwisscomBatchSignException extends RemoteSigningException{

    public SwisscomBatchSignException(String message, BaseErrorCode errorCode){
        super(message,errorCode);
    }
}
