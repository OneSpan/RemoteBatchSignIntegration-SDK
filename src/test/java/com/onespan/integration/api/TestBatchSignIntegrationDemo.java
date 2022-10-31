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

    //q2 poornima test parent account:  poornimakalivarapu+86@gmail.com
//    String senderApiKey   = "blRrTXNHUkQ2VndFOlZ6b3JHYWYyak1rOA==";    //qesintegrator@gmail.com    blRrTXNHUkQ2VndFOlZ6b3JHYWYyak1rOA==
//    String signerApiKey   = "eEc0YTVXZG9HeTA5OlZ6b3JHYWYyak1rOA==";    //batchsign@gmail.com    eEc0YTVXZG9HeTA5OlZ6b3JHYWYyak1rOA==
//    String server         = "ossq2.rnd.esignlive.com";

    //q3
//    String senderApiKey = "VUNIcDRIbkc1M1VaOk12OFhOWno2Y3Y0Tg==";    //q3 jtao test account
//    String signerApiKey = "dVdsY1R0QjVMUUlTOk12OFhOWno2Y3Y0Tg==";    //q3 @silanis test account
//    String server = "ossq3.rnd.esignlive.com";

    //q2
//    String senderApiKey         = "YWlkOEkxSDM5Wm84OmVIVnVhODBINmY0Vg==";    //  jtao

    String senderApiKey   = "aXpXd2hSUERtTklXOmVIVnVhODBINmY0Vg==";    //  sender.batchsigner@mail.com   aXpXd2hSUERtTklXOmVIVnVhODBINmY0Vg==
    String signerApiKey	= "VFR3Y0dMS0tmUE1LOmVIVnVhODBINmY0Vg==";    //  signer.directornumber1@mail.com    VFR3Y0dMS0tmUE1LOmVIVnVhODBINmY0Vg==
    String server         = "ossq2.rnd.esignlive.com";

    //q1
//    String senderApiKey = "R25VenBPS294ZEFPOkVXQUs1RGlHOW53Vw==";       // q1 batchSigner sender apikey
//    String signerApiKey = "NmUzWWhJaU9ycU03OkVXQUs1RGlHOW53Vw==";      //q1 director number1
//    String server = "ossq1.rnd.esignlive.com";


    //customize master package
//    String signerEmail = "batchsign@gmail.com";    //poornima test

//    String signerEmail = "jinsong_tao@silanis.com";  //q3 silanis

    String signerEmail = "directornumber1@mail.com";  //q1 alan.carter q2 jtao
    String packageName = "Batch Signing Master Package";
    String batchSignConsentPath = "src/test/resources/batchSignConsent.txt";
    String signingMethod = "swisscomdirect:eidas";    //test signing method: personalCertificateSigning | swisscomdirect:eidas | swisscomdirect

    String protocol = "https";





    //test case
    @Test
    public void IntegrationGet100PlusUnsignedTransactonWithFilter() throws Exception {
        //api call
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String unsignedTx = integration.getAllUnsignedTransactionWithFilter(2);

        JSONObject selectedTxInfo = new JSONObject(unsignedTx);
        System.out.println("-->unsigned.swcm.transactions: " + selectedTxInfo.getJSONArray("scTransactions").length());

        //save result to file
        save2file(selectedTxInfo, "transaction-with-filter.json");

        //extract tx info as pdf content
        saveAsPdfContent(selectedTxInfo);

    }  //select 100 plus

    @Test
    public void IntegrationGetAllUnsignedTransactonWithFilter() throws Exception {
        //api call
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String unsignedTx = integration.getAllUnsignedTransactionWithFilter();

        JSONObject selectedTxInfo = new JSONObject(unsignedTx);
        System.out.println("-->unsigned.swcm.transactions: " + selectedTxInfo.getJSONArray("scTransactions").length());

        //save result to file
        save2file(selectedTxInfo, "transaction-with-filter.json");

        //extract tx info as pdf content
        saveAsPdfContent(selectedTxInfo);

    }  //select all

    @Ignore
    @Test
    public void IntegrationExtractHashesFromFilteredTransacton() throws Exception {

        URL url = Resources.getResource("transaction-with-filter.json");
        String jsonInput = Resources.toString(url, StandardCharsets.UTF_8);
        JSONObject transactionPage = new JSONObject(jsonInput);

        //check hash doc limit not larger than 250
        String docLimit = transactionPage.get("totalDocuments").toString();
        System.out.println("-->docLimit: " + docLimit);

        //collect filtered tx
        List<JSONObject> selectedTx = new ArrayList<>();
        selectedTx.addAll(toStream(transactionPage.getJSONArray("scTransactions"))
                .collect(Collectors.toList()));
        selectedTx.forEach(System.out::println);

        //call api
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
//        List<JSONObject> batchsignHashlist = integration.extractHashes(selectedTx);
        Map<String, List<JSONObject>> batchsignHashList = integration.extractHashes(selectedTx);
        System.out.println("-->batch.sign.hashes: " + batchsignHashList.get("extractSuccess"));
        System.out.println("-->extract.fail.txs: " + batchsignHashList.get("extractFail").get(0).getJSONArray("failedTxs"));

        JSONObject baJsonOb = new JSONObject();
        baJsonOb.put("batchsignhashes", batchsignHashList.get("extractSuccess"));
        save2file(baJsonOb, "extractedDocHashes.json");


        if (batchsignHashList.get("extractSuccess").size() != selectedTx.size()){
//            System.out.println("--" + batchsignHashList.get("extractFail").get(0).getJSONArray("failedTxs"));
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
        System.out.println("-->extracted.hashes: " + extractedHashes);

        //get hashes arr
        List<JSONObject> slaveDocHashToSign = new ArrayList<>();
        slaveDocHashToSign.addAll(toStream(extractedHashes.getJSONArray("batchsignhashes"))
                .collect(Collectors.toList()));
        System.out.println("-->slave DocHashes To Sign " + slaveDocHashToSign);

        //call api
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, senderApiKey);
        String txId = integration.createBatchSignTransaction(batchSignConsentPath, slaveDocHashToSign, signingMethod, signerEmail, packageName);
        System.out.println("-->master.pid: " + txId);


    }  //end master package


    @Ignore
    @Test
    public void IntegrationInjectSignedHash() throws Exception{
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);

        //get signed hash from tx attr
        String mpid = "PPYCARmt92s40GP--hY269zhplY=";
        String signedHashesAttr = integration.getSignedHashesFromTransaction(mpid);

        //extract signed hashes from master and inject to slave
        JSONObject signedHashes = new JSONObject(signedHashesAttr);
        JSONArray signedHashArr = signedHashes.getJSONObject("data").getJSONArray("scBatchSignHashes");
        Map<String, String> resp = integration.injectSignedHash(signedHashArr, mpid);

        if(resp.get("injectedSuccessStatusCode").equals("200")){
            System.out.println("-->inject success: " + resp);

        }else{
            System.out.println("\n-->inject signed hash failed: " + resp);
            Assert.fail(resp.toString());

        }

    }


    //debug/ debug/ debug
    @Test
    public void testdebugUpdateTxAttr() throws IOException{

//        URL url = Resources.getResource("performance/transaction-update-status-draft.json");
//        URL url = Resources.getResource("performance/transaction-update-status-sent.json");

        URL url = Resources.getResource("performance/transaction-update-attr2.json");
        String jsonInput = Resources.toString(url, StandardCharsets.UTF_8);
        JSONObject update = new JSONObject(jsonInput);
        System.out.println("-->update-attr: " + update);

        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String attr = integration.updateTransactionAttribute("V4ELulC3wI7mgZ-oq45aZuE1wWI=", jsonInput);
        System.out.println("-->" + attr);

    }

    @Test
    public void testdebugDownloadEvidence() throws IOException{
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String resp = integration.downloadEvidence("E6hXtKAFQ-QIsdik5xk8BqMpoMw=");

        String path = "/tmp/summary.txt";
        Files.write( Paths.get(path), resp.getBytes(StandardCharsets.UTF_8));
        System.out.println("-->" + resp);

    }

    @Test
    public void testdebugUpdateEvidence() throws IOException{
        BatchSignIntegration integration = new BatchSignIntegration(protocol, server, signerApiKey);
        String resp = integration.updateEvidence("oiTSN3Wx3k-8LoyS0t5SOqRC4kk=");


        System.out.println("-->" + resp);

    }



    //utility method for test/demo
    private void save2file(JSONObject pageinfo, String filename) {
        String path2file = String.format("src/test/resources/%s", filename);
        try (FileWriter file = new FileWriter(path2file)) {
            //write any JSONArray or JSONObject instance to the file
            file.write(String.valueOf(pageinfo));
            file.flush();

        } catch (IOException e) {//test signing method: personalCertificateSigning / swisscomdirect:eidas / swisscomdirect
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
        System.out.println("---->transaction details: " + totalTx);

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
