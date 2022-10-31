package com.onespan.integration.api.exception;

public enum SwisscomBatchSignErrorCode implements BaseErrorCode{

    /**
     * TODO Define unused range of error
     * (600-699): swisscomdirect errors
     **/


    SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE(201, Constants.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE),
    SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT(200, Constants.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);
    //    SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT(200, Constants.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);

    private final String messageKey;
    private final int code;

    SwisscomBatchSignErrorCode(int code, String messageKey){
        this.code=code;
        this.messageKey=messageKey;
    }

    @Override
    public String getMessageKey(){
        return messageKey;
    }

    @Override
    public int getCode(){
        return code;
    }

    @Override
    public String getName(){
        return toString();
    }

    @Override
    public String getResourceName(){
        return"swisscomBatchSignMessages";
    }

    private static class Constants {
        public static final String SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE = "Response result not found";
        public static final String SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT = "Document exceed 250 limit";

    }

}
