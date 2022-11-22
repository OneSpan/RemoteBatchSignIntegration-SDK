package com.onespan.integration.api;

import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TestBatchSignIntegrationDemo {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(BatchSignIntegration.class);

    //q2
    String senderApiKey   = "{senderApiKey}";
    String signerApiKey	= "{singerApiKey}";
    String server         = "ossq2.rnd.esignlive.com";


    String signerEmail = "{signerEmail}";
    String packageName = "{Batch Signing Master Package}";
    String batchSignConsentPath = "src/test/resources/batchSignConsent.txt";
    String signingMethod = "swisscomdirect:eidas";    // option: swisscomdirect:zertes

    String protocol = "https";





    //test case
    /**
     * @ provide a int variable in api call will enable pagination search
     * default search range is the first 100 transactions
     *  */
    @Test
    public void IntegrationGetUnsignedTransactonWithFilter() throws Exception {
        //api call
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String unsignedTx = integration.getAllUnsignedTransactionWithFilter(2);

        JSONObject selectedTxInfo = new JSONObject(unsignedTx);
        logger.info(">>>unsigned.swcm.transactions: {}" ,selectedTxInfo.getJSONArray("scTransactions").length());

        //save result to file
        save2file(selectedTxInfo, "transaction-with-filter.json");

        //extract tx info as pdf content
        saveAsPdfContent(selectedTxInfo);

    }  //select txs


    @Ignore
    @Test
    public void IntegrationExtractHashesFromFilteredTransacton() throws Exception {

        URL url = Resources.getResource("transaction-with-filter.json");
        String jsonInput = Resources.toString(url, StandardCharsets.UTF_8);
        JSONObject transactionPage = new JSONObject(jsonInput);

        //collect filtered tx
        List<JSONObject> selectedTx = new ArrayList<>();
        selectedTx.addAll(toStream(transactionPage.getJSONArray("scTransactions"))
                .collect(Collectors.toList()));

        //call api
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        Map<String, List<JSONObject>> batchsignHashList = integration.extractHashes(selectedTx);
        logger.info(">>>extract success txs: {}", batchsignHashList.get("extractSuccess"));
        logger.info(">>>extract failure txs: {}", batchsignHashList.get("extractFail").get(0).getJSONArray("failedTxs"));

        JSONObject baJsonOb = new JSONObject();
        baJsonOb.put("batchsignhashes", batchsignHashList.get("extractSuccess"));
        save2file(baJsonOb, "extractedDocHashes.json");


        if (batchsignHashList.get("extractSuccess").size() != selectedTx.size()){
            Assert.fail("extract document hash failed: " + batchsignHashList.get("extractFail").get(0).getJSONArray("failedTxs"));
        }

    }  //end extract hash

    @Ignore
    @Test
    public void IntegrationCreateMasterTransacton() throws IOException{

        //load hashes from file
        URL url = Resources.getResource("extractedDocHashes.json");
        String jsonInput = Resources.toString(url, StandardCharsets.UTF_8);
        JSONObject extractedHashes = new JSONObject(jsonInput);

        //get hashes arr
        List<JSONObject> slaveDocHashToSign = new ArrayList<>();
        slaveDocHashToSign.addAll(toStream(extractedHashes.getJSONArray("batchsignhashes"))
                .collect(Collectors.toList()));

        //call api
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, senderApiKey);
        String txId = integration.createBatchSignTransaction(batchSignConsentPath, slaveDocHashToSign, signingMethod, signerEmail, packageName);
        logger.info(">>>master.pid: " + txId);


    }  //end master package


    /** passing master package id to api call */
    @Ignore
    @Test
    public void IntegrationInjectSignedHash() throws Exception{
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);

        //get signed hash from tx attr
        String mpid = "{replace master package id from previous test step}";
        String signedHashesAttr = integration.getSignedHashesFromTransaction(mpid);

        //extract signed hashes from master and inject to slave
        JSONObject signedHashes = new JSONObject(signedHashesAttr);
        JSONArray signedHashArr = signedHashes.getJSONObject("data").getJSONArray("scBatchSignHashes");
        Map<String, String> resp = integration.injectSignedHash(signedHashArr, mpid);

        if(resp.get("injectedSuccessStatusCode").equals("200")){
            logger.info(">>>inject success: {}", resp);

        }else{
            logger.info(">>>inject signed hash failed: {}", resp);
            Assert.fail(resp.toString());

        }

    }




    //utility method for test/demo
    private void save2file(JSONObject pageinfo, String filename) {
        String path2file = String.format("src/test/resources/%s", filename);
        try (FileWriter file = new FileWriter(path2file)) {
            //write any JSONArray or JSONObject instance to the file
            file.write(String.valueOf(pageinfo));
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAsPdfContent(JSONObject txInfo) throws FileNotFoundException, UnsupportedEncodingException {
        List<JSONObject> txArrList = new ArrayList<>();
        txArrList.addAll(toStream(txInfo.getJSONArray("scTransactions"))
                .collect(Collectors.toList()));

        String txName = null;
        String txDate = null;
        String txSender = null;
        ArrayList<String> totalTx = new ArrayList<>();

        for (JSONObject tx: txArrList) {
            txName = tx.get("name").toString();
            txDate = tx.get("updated").toString();
            txSender = tx.getJSONObject("sender").get("email").toString();

            String txDetail = txName + "\t |" + txDate + "\t |" + txSender;

            totalTx.add(txDetail);

        }
        logger.info(">>>transaction details saved as content of pdf: " + totalTx);

        PrintWriter writer = new PrintWriter("src/test/resources/batchSignConsent.txt", "UTF-8");
        writer.println("Transactions to batch sign in Master Package\n");
        writer.println("txName\t\t\t Date/Update\t\t\t Sender/email\n");

        for (String item : totalTx) {
            writer.println(item);
        }
        writer.close();
    }

    static Stream<JSONObject> toStream(JSONArray arr) {
        return StreamSupport.stream(arr.spliterator(), false)
                .map(JSONObject.class::cast);
    }



}
