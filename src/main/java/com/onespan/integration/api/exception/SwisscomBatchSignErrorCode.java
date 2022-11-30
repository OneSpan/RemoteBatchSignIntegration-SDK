package com.onespan.integration.api.exception;

public enum SwisscomBatchSignErrorCode implements BaseErrorCode{

    /**
     * TODO Define unused range of error
     * (600-699): swisscomdirect errors
     **/


    SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE(404, Constants.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE),
    SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT(400, Constants.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT),
    SWISSCOM_BATCH_SIGN_SIGNED_HASH_UNAVAILABLE(404, Constants.SWISSCOM_BATCH_SIGN_SIGNED_HASH_UNAVAILABLE),
    SWISSCOM_BATCH_SIGN_INJECT_SIGNED_HASH_FAILED(400, Constants.SWISSCOM_BATCH_SIGN_INJECT_SIGNED_HASH_FAILED),
    SWISSCOM_BATCH_SIGN_DOC_FAILED_EXTRACT_HASH(400, Constants.SWISSCOM_BATCH_SIGN_DOC_FAILED_EXTRACT_HASH);

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
        public static final String SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT = "Number of documents exceed the limit";
        public static final String SWISSCOM_BATCH_SIGN_DOC_FAILED_EXTRACT_HASH = "Document hash extraction failed";
        public static final String SWISSCOM_BATCH_SIGN_SIGNED_HASH_UNAVAILABLE = "Signed hash is not available for the master transaction";
        public static final String SWISSCOM_BATCH_SIGN_INJECT_SIGNED_HASH_FAILED= "Inject signed hash failed";

    }

}
