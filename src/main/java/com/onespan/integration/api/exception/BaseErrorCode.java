package com.onespan.integration.api.exception;

public interface BaseErrorCode {
    String getMessageKey();

    int getCode();

    String getName();

    String getResourceName();

}
