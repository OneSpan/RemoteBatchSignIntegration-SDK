package com.onespan.integration.api;

import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

public class TestBatchSignIntegrationMock {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(BatchSignIntegration.class);

    String signerEmail = "directornumber1@mail.com";
    String packageName = "Batch Signing Master Package";
    String batchSignConsentPath = "src/test/resources/batchSignConsent.txt";
    String signingMethod = "swisscomdirect:eidas";    //option: swisscomdirect:zertes


    BatchSignIntegration mockIntegration;

    @Before
    public void setUp() throws Exception {
        mockIntegration = Mockito.mock(BatchSignIntegration.class);
    }

    @Test
    public void IntegrationGetAnyUnsignedTransactonWithFilterMock() {
        //api call
        when(mockIntegration.getAllUnsignedTransactionWithFilter(2)).thenReturn("txsJsonString");
        mockIntegration.getAllUnsignedTransactionWithFilter(2);

        Mockito.verify(mockIntegration, Mockito.times(1)).getAllUnsignedTransactionWithFilter(2);

    }

    @Test
    public void IntegrationGetAllUnsignedTransactonWithFilterMock() {
        //api call
        when(mockIntegration.getAllUnsignedTransactionWithFilter()).thenReturn("txsJsonString");
        mockIntegration.getAllUnsignedTransactionWithFilter();

        Mockito.verify(mockIntegration, Mockito.times(1)).getAllUnsignedTransactionWithFilter();

    }

    @Test
    public void IntegrationExtractHashesFromFilteredTransactonMock() throws Exception {
        //api call
        List<JSONObject> listObj = new ArrayList<>();
        Map<String, List<JSONObject>> extractedHashMap = new HashMap<>();

        when(mockIntegration.extractHashes(listObj)).thenReturn(extractedHashMap);
        mockIntegration.extractHashes(listObj);

        Mockito.verify(mockIntegration, Mockito.times(1)).extractHashes(listObj);

    }

    @Test
    public void IntegrationCreateMasterTransactonMock() throws Exception {
        //api call
        List<JSONObject> selectedTx = new ArrayList<>();
        when(mockIntegration.createBatchSignTransaction(batchSignConsentPath, selectedTx, signingMethod, signerEmail, packageName)).thenReturn("guid");
        mockIntegration.createBatchSignTransaction(batchSignConsentPath, selectedTx, signingMethod, signerEmail, packageName);

        Mockito.verify(mockIntegration, Mockito.times(1)).createBatchSignTransaction(batchSignConsentPath, selectedTx, signingMethod, signerEmail, packageName);

    }

    @Test
    public void IntegrationGetSignedHashesFromTransactionMock() throws Exception {
        //api call
        when(mockIntegration.getSignedHashesFromTransaction("guid")).thenReturn("signedHashes");
        mockIntegration.getSignedHashesFromTransaction("guid");

        Mockito.verify(mockIntegration, Mockito.times(1)).getSignedHashesFromTransaction("guid");

    }

    @Test
    public void IntegrationInjectSignedHashMock() throws Exception {
        //api call
        JSONArray jsonArray = new JSONArray("[]");
        Map<String, String> respMap = new HashMap<>();

        when(mockIntegration.injectSignedHash(jsonArray, "guid")).thenReturn(respMap);
        mockIntegration.injectSignedHash(jsonArray, "guid");

        Mockito.verify(mockIntegration, Mockito.times(1)).injectSignedHash(jsonArray, "guid");

    }

}
