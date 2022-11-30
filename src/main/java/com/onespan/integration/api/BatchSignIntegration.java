package com.onespan.integration.api;

import com.google.common.io.Resources;
import com.onespan.integration.api.exception.SwisscomBatchSignErrorCode;
import com.onespan.integration.api.exception.SwisscomBatchSignException;
import com.onespan.integration.api.model.FailedTransaction;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.onespan.integration.api.BatchSignIntegration.HTTPMethod.GET;
import static com.onespan.integration.api.BatchSignIntegration.HTTPMethod.POST;


public class BatchSignIntegration {

    private static final Logger logger = LogManager.getLogger(BatchSignIntegration.class);
    private static Client client = null;
    private static String protocol = null;
    private static String server = null;
    private static String authorizationHeaderValue = null;
    private static final String authorizationHeaderName = "Authorization";


    public BatchSignIntegration(String protocol, String server, String apiKey) {
        BatchSignIntegration.client = new Client();
        BatchSignIntegration.protocol = protocol;
        BatchSignIntegration.server = server;
        BatchSignIntegration.authorizationHeaderValue = "Basic " + apiKey;
    }


    public String getAllUnsignedTransactionWithFilter(int... number) {
        logger.info("Fetching unsigned transactions");

        JSONArray selectedTxArray;

        if (number.length > 0) {
            selectedTxArray = handlePagination(number);
        } else {
            selectedTxArray = handleNonPagination();
        }

        //check is swisscom transaction
        JSONArray filteredTxArr = new JSONArray();
        filteredTxArr.putAll(toStream(selectedTxArray)
                .filter(BatchSignIntegration::isSwisscomSign)
                .collect(Collectors.toList()));

        //count tx
        long totalTx = toStream(filteredTxArr).count();
        logger.info("Number of swcm transactions found: {}", totalTx);

        //count docs in all packages
        long totalDoc = toStream(filteredTxArr)
                .flatMap(tx -> toStream(tx.getJSONArray("documents")))
                .count();

        logger.info("Number of documents to batch sign: {}", totalDoc);

        //add tx arr to json object list
        JSONObject scFilteredTxJson = new JSONObject();
        scFilteredTxJson.put("scTransactions", filteredTxArr);
        scFilteredTxJson.put("totalDocuments", totalDoc);
        scFilteredTxJson.put("totalPackages", totalTx);

        return scFilteredTxJson.toString();
    }

    private JSONArray handleNonPagination() {

        String url = buildPath(getServerPath(), "?query=INBOX&predefined=awaitingSignature&from=1&to=100");
        url = url.replace("/packages/", "/packages");
        url = url.replaceFirst("/*$", "");

        logger.info("URL of the Filtered Transactions: {}", url);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);

        String response = clientResponse.getEntity(String.class);

        logger.debug("Filtered unsigned transactions: {}", response);

        JSONObject txJsonOb = new JSONObject(response);
        JSONArray destinationArray;

        if (clientResponse.getStatus() == 200 && txJsonOb.has("results")) {
            destinationArray = txJsonOb.getJSONArray("results");
        } else {
            throw new SwisscomBatchSignException("Unsigned transactions are not available", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
        }
        return destinationArray;
    }

    private JSONArray handlePagination(int[] number) {
        String query = "?query=INBOX&predefined=awaitingSignature&from=%s&to=%s";
        JSONArray destinationArray = new JSONArray();
        int pagination = 100;

        int fromNum;
        int toNum;

        for (int i = 0; i <= number[0] / pagination; i++) {
            int toNumber = i * pagination + pagination;
            toNum = Math.min(number[0], toNumber);
            fromNum = i * pagination + 1;

            if (toNum < fromNum) {
                logger.info("handel just match pagination*X size: {}", String.valueOf(number[0]));

            } else {

                String url = buildPath(getServerPath(), String.format(query, String.valueOf(fromNum), String.valueOf(toNum)));
                url = url.replace("/packages/", "/packages");
                url = url.replaceFirst("/*$", "");

                logger.info("Pagination URL of the Filtered Transactions: {}", url);
                WebResource webResource = client.resource(url);
                ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);

                String response = clientResponse.getEntity(String.class);

                logger.debug("Filtered unsigned transactions: {}", response);

                JSONObject jo = new JSONObject(response);
                JSONArray sourceArray;

                if (clientResponse.getStatus() == 200 && jo.has("results")) {
                    sourceArray = jo.getJSONArray("results");
                    for (int j = 0; j < sourceArray.length(); j++) {
                        destinationArray.put(sourceArray.getJSONObject(j));
                    }
                } else {
                    throw new SwisscomBatchSignException("Unsigned transactions are not available", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
                }
            }
        }
        return destinationArray;
    }

    public static boolean isSwisscomSign(JSONObject tx) {

        String signerEmail = getSignerEmail();

        String pid = tx.get("id").toString();
        boolean isSwisscom = false;
        String rid = null;

        List<JSONObject> roles = new ArrayList<>();
        roles.addAll(toStream(tx.getJSONArray("roles"))
                .map(role -> role.put("pid", tx.get("id")))
                .collect(Collectors.toList()));

        for (JSONObject role : roles) {
            List<JSONObject> signers = new ArrayList<>();
            signers.addAll(toStream(role.getJSONArray("signers"))
                    .collect(Collectors.toList()));

            for (JSONObject signer : signers) {
                if (signerEmail.equals(signer.get("email"))) {
                    rid = role.get("id").toString();
                }
            }

        }

        String url = buildPath(getServerPath(), pid, "roles", rid, "verification");
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);

        String response = null;
        if (clientResponse.getStatus() == 200) {
            response = clientResponse.getEntity(String.class);
        } else {
            response = "{}";
        }

        JSONObject resp2jsonOb = new JSONObject(response);

        if (clientResponse.getStatus() == 200 && resp2jsonOb.get("typeId").toString().startsWith("swisscom")) {
            isSwisscom = true;
            logger.debug(pid + "/" + rid + " is SWCM: {}", isSwisscom);
        }

        return isSwisscom;
    }

    private static String getSignerEmail() {
        String url = buildPath(getServerPath());
        url = url.replace("/packages/", "/session/");

        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);
        String response = clientResponse.getEntity(String.class);

        JSONObject session = new JSONObject(response);
        JSONObject user = session.getJSONObject("user");
        String email = user.get("email").toString();

        logger.debug("Email id of the signer {}", email);
        return email;
    }


    public Map<String, List<JSONObject>> extractHashes(List<JSONObject> selectedtxs) throws Exception {
        logger.info("Extracting the document hashes for the transactions");

        //check total doc not more than 250 limit
        Long doc2Hash = selectedtxs.stream()
                .flatMap(tx -> toStream(tx.getJSONArray("documents")))
                .count();

        if (doc2Hash > 250) {
            throw new SwisscomBatchSignException("Cannot sign more than 250 documents", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);
        }

        String apiUserEmail = getSignerEmail();

        List<JSONObject> txDocHashes = new ArrayList<>();

        int totalDocHashed = 0;

        boolean isSelfSign = false;

        //loop selected txs
        List<String> docHashFailedTx = new ArrayList<>();
        for (JSONObject tx : selectedtxs) {
            String pid = tx.get("id").toString();
            logger.info("Begin to extract the document hash for the transaction : {} ,  Transaction Id : {} ", tx.get("name"), pid);

            //roles in package
            List<JSONObject> roles = new ArrayList<>();
            roles.addAll(toStream(tx.getJSONArray("roles"))
                    .collect(Collectors.toList()));

            if (roles.size() == 1) {
                isSelfSign = true;
            }

            String Rid = null;
            String Sid = null;
            for (JSONObject role : roles) {

                List<JSONObject> signers = new ArrayList<>();
                signers.addAll(toStream(role.getJSONArray("signers"))
                        .filter(signer -> apiUserEmail.equals(signer.get("email")))
                        .collect(Collectors.toList()));

                if (!signers.isEmpty()) {
                    Rid = role.get("id").toString();
                    Sid = signers.get(0).get("id").toString();
                }

            }

            //docs in package
            List<JSONObject> documents = new ArrayList<>();
            documents.addAll(toStream(tx.getJSONArray("documents"))
                    .map(doc -> doc.put("pid", tx.get("id")))
                    .collect(Collectors.toList()));

            //sort documents by index
            documents.sort(Comparator.comparing(o -> o.get("index").toString()));

            totalDocHashed += documents.size();

            JSONObject docHash = new JSONObject();
            String did;
            String apid = null;

            //loop tx documents
            String hashPayload = null;

            String hashUrl = null;
            try {
                for (JSONObject doc : documents) {
                    logger.info("Extracting the document hash for the document : {}", doc.get("name"));

                    did = doc.get("id").toString();

                    List<JSONObject> approvals = new ArrayList<>();
                    String finalRid = Rid;
                    approvals.addAll(toStream(doc.getJSONArray("approvals"))
                            .filter(ap -> finalRid.equals(ap.get("role")))
                            .collect(Collectors.toList()));

                    //handle no approval for batch signer
                    if (approvals.isEmpty()) {
                        logger.info("No approvals for role, skipping the document {} ",doc.get("name"));
                        continue;
                    }


                    //loop approvals for accepted
                    String timestamp = null;
                    for (JSONObject ap : approvals) {

                        apid = ap.get("id").toString();
                        String response = "{}";

                        if (ap.get("accepted").equals(null)) {
                            String url = buildPath(getServerPath(), pid, "documents", did, "approvals", apid, "sign");
                            logger.debug("Accept approvals URL: {}", url);
                            WebResource webResource = client.resource(url);
                            ClientResponse clientResponse = getClientResponse(webResource, POST, "{}");
                            if (clientResponse.getStatus() != 200) {
                                logger.error("Extract hash failed with status: {} and error : {} ", clientResponse.getStatus(), clientResponse.getEntity(String.class));
                                throw new Error("Extract hash failed : " + clientResponse.getEntity(String.class));
                            }
                            response = clientResponse.getEntity(String.class);

                            logger.debug("accepted.resp {}", response);

                            JSONObject resp = new JSONObject(response);
                            timestamp = resp.get("accepted").toString();

                            logger.debug("Document accepted time: {}", timestamp);
                        } else {
                            timestamp = ap.get("accepted").toString();
                            logger.debug("Document accepted time: {}", timestamp);
                        }
                    }


                    //generate hash payload outside
                    if (isSelfSign && "default-consent".equals(did)) {
                        logger.debug("Skipping the self-sign for default-consent document");

                    } else {
                        Map<String, String> tempParams = new HashMap<>();
                        tempParams.put("approvalId", apid);
                        tempParams.put("acceptTime", timestamp);
                        tempParams.put("nonce", generateNonce(32));

                        String token = prepareTemplate("get_document_hash", tempParams);
                        token = Base64.getEncoder().encodeToString(token.getBytes());

                        JsonObject json = Json.createObjectBuilder()
                                .add("token", token)
                                .build();

                        logger.debug("hash template: {}", json);

                        //generate doc hashes
                        hashPayload = json.toString();

                        hashUrl = buildPath(getServerPath(), pid, "documents", did, "hash");
                        logger.debug("Hash Document URL: " + hashUrl);

                        String response = null;
                        response = doExtractHash(hashPayload, hashUrl);

                        //extract doc hash from response
                        String value = (String) new JSONObject(response).get("value");

                        byte[] xml = Base64.getDecoder().decode(value);

                        Node node = extractXmlTag(new String(xml), "hash")
                                .orElseThrow(() -> new Exception("Failed to get esl certificate"));
                        String hash = node.getTextContent();
                        String piddidapidnonce = pid + "|" + did + "|" + apid + "|" + tempParams.get("nonce") + "|" + Sid;
                        docHash.put(piddidapidnonce, hash);
                    }
                    logger.info("Successfully extracted the document hash for the document  {} in transaction", doc.get("name"));
                }

                txDocHashes.add(docHash);

            } catch (Exception e) {
                logger.info("Extracting document hash for the transaction {} failed,  {}",pid, e.getMessage());

                docHashFailedTx.add(pid);

                //retry
                int retryCounter = 0;
                int maxRetries = 3;
                while (retryCounter < maxRetries) {
                    TimeUnit.MILLISECONDS.sleep(1000);
                    try {
                        logger.info("Retrying extracting document hash for the transaction {}",pid);
                        doExtractHash(hashPayload, hashUrl);
                        break;
                    } catch (Exception ex) {
                        retryCounter++;
                        logger.info("FAILED - extracting the document hash for the transaction {} on retry {} with error: {}",pid, retryCounter, ex.getMessage());

                        if (retryCounter >= maxRetries) {
                            logger.error("Max retries for extracting document hash exceeded.");
                            break;
                        }
                    }
                }
            }

        }
        docHashFailedTx = docHashFailedTx.stream().distinct().collect(Collectors.toList());

        Map<String, List<JSONObject>> extractHashesMap = new HashMap<>();
        extractHashesMap.put("extractSuccess", txDocHashes);

        JSONObject failedTxs = new JSONObject();
        failedTxs.put("failedTxs", docHashFailedTx);
        List<JSONObject> failedJoList = new ArrayList<>();
        failedJoList.add(failedTxs);
        extractHashesMap.put("extractFail", failedJoList);

        return extractHashesMap;

    }

    private String doExtractHash(String hashpayload, String url) throws SwisscomBatchSignException {
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse(webResource, HTTPMethod.POST, hashpayload);
        if (clientResponse.getStatus() != 200) {
            throw new SwisscomBatchSignException(clientResponse.getEntity(String.class), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_DOC_FAILED_EXTRACT_HASH);
        }
        String response = clientResponse.getEntity(String.class);
        return response;
    }


    public static String generateNonce(int size) {
        if (size <= 0) {
            return "";
        }

        int begin = 48; // '0'
        int end = 122; // 'z'
        Random random = new Random();

        return random.ints(begin, end + 1)
                .filter(i -> (i < 58 || i > 64 && (i < 91 || i > 96))) //remove unwanted characters
                .limit(size)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public Optional<Node> extractXmlTag(String xml, String tag) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            String tmp = xml.replaceAll("\0", "").trim();
            Document doc = builder.parse(new ByteArrayInputStream(tmp.getBytes()));
            doc.normalizeDocument();
            Element root = doc.getDocumentElement();
            return Optional.ofNullable(root.getElementsByTagName(tag).item(0));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new Exception("Fail to parse xml string,  "+ e.getMessage());
        }
    }

    public String prepareTemplate(String templateName, Map<String, String> kv) throws IOException {
        String filename = "templates\\swisscom_" + templateName + ".tmpl";
        kv.put("signaturePlaceholderSize", Integer.toString(49152));
        InputStream is = new ClassPathResource(filename).getInputStream();
        String template = IOUtils.toString(is, StandardCharsets.UTF_8);
        is.close();
        String[] keys = kv.keySet().stream()
                .map(s -> "${" + s + "}")
                .toArray(String[]::new);
        String[] values = kv.values().toArray(new String[0]);

        return StringUtils.replaceEach(template, keys, values);
    }

    public String createBatchSignTransaction(String filePath, List<JSONObject> batchSignHashes, String signingMethod, String signerEmail, String packageName) throws IOException {
        logger.info("Started creating master transaction");

        String requestURL = buildPath(getServerPath());

        URL urlnew = Resources.getResource("payload_master_package_draft.json");
        String payloadTmpl = Resources.toString(urlnew, StandardCharsets.UTF_8);

        //update payload info
        payloadTmpl = payloadTmpl.replace("${batchSigner@email}", signerEmail);
        payloadTmpl = payloadTmpl.replace("${batchSignMasterPackageName}", packageName);


        //inject hashes arr to payload
        JSONObject hashesPayload = new JSONObject(payloadTmpl);
        JSONObject data = hashesPayload.getJSONObject("data");
        data.put("scBatchSignHashes", batchSignHashes);

        logger.debug("Master package payload with hashes: " + hashesPayload);

        //process payload end
        String charset = "UTF-8";
        File uploadFile = new File(filePath);
        String boundary = Long.toHexString(System.currentTimeMillis());
        String CRLF = "\r\n";

        HttpsURLConnection connection = null;
        URL url = new URL(requestURL);
        connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Authorization", authorizationHeaderValue);
        connection.setRequestProperty("Accept", "application/json; esl-api-version=11.0");
        OutputStream output = connection.getOutputStream();

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

        try {

            // Add pdf file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + uploadFile.getName() + "\"")
                    .append(CRLF);
            writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(uploadFile.getName()))
                    .append(CRLF);
            writer.append(CRLF).flush();
            Files.copy(uploadFile.toPath(), output);
            output.flush();
            writer.append(CRLF).flush();

            // add json payload
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"payload\"").append(CRLF);
            writer.append("Content-Type: application/json; charset=" + charset).append(CRLF);
            writer.append(CRLF).append(hashesPayload.toString()).append(CRLF).flush();

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(CRLF).flush();

        } catch (IOException ex) {
            logger.info(ex);

        }

        // get and write out response code
        int responseCode = ((HttpURLConnection) connection).getResponseCode();

        StringBuffer response = null;
        if (responseCode == 200) {

            // get and write out response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            logger.info("Successfully created master transaction with transaction Id: {}", response);

        } else {

            // get and write out response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            String inputLine;
            response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            logger.info("Creating master transaction failed : {}", response);

        }

        //config signing method
        JSONObject pidOb = new JSONObject(response.toString());
        String pid = pidOb.get("id").toString();                //may throws exception
        String rid = hashesPayload.getJSONArray("roles").optJSONObject(0).get("id").toString();
        JSONObject jsonInput = new JSONObject();
        jsonInput.put("typeId", signingMethod);

        setSigningMethod(pid, rid, jsonInput.toString());

        return response.toString();
    }

    private String setSigningMethod(String pid, String rid, String payload) {
        String url = buildPath(getServerPath(), pid, "roles", rid, "verification");
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse(webResource, POST, payload);

        String response = clientResponse.getEntity(String.class);
        logger.debug("set signing method Response: {}", response);

        //update tx SENT
        if (clientResponse.getStatus() == 200) {
            String payloadSent = "{\"status\": \"SENT\"}";
            String url2 = buildPath(getServerPath(), pid);
            WebResource webResource2 = client.resource(url2);
            ClientResponse clientResponse2 = getClientResponse(webResource2, POST, payloadSent);

        }

        return response;
    }


    public String getSignedHashesFromTransaction(String guid) {
        logger.info("Started extracting signed hashes from master transaction {}",guid);

        String url = buildPath(getServerPath(), guid);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, HTTPMethod.GET);

        if (clientResponse.getStatus() != 200) {
            throw new SwisscomBatchSignException(String.valueOf(clientResponse.getStatus()), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
        }

        String response = clientResponse.getEntity(String.class);
        logger.debug("Master transaction response: {}", response);

        JSONObject signedHashes = new JSONObject(response);
        JSONArray signedHashArr = signedHashes.getJSONObject("data").getJSONArray("scBatchSignHashes");
        if (signedHashArr.isEmpty()) {
            logger.error("Signed hash not available in the master transaction {}",guid);
            throw new SwisscomBatchSignException("Signed hash not available in the master transaction", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_SIGNED_HASH_UNAVAILABLE);
        }
        logger.info("Completed extracting signed hashes from master transaction {}",guid);
        return response;
    }


    public Map<String, String> injectSignedHash(JSONArray signedHashes, String mpid) throws Exception {
        logger.info("Started injecting signed hashes to slave transactions");

        List<String> injectFailedTx = new ArrayList<>();
        List<String> injectFailedTxDetails = new ArrayList<>();
        String statusCode = null;

        List<JSONObject> signedHashObs = toStream(signedHashes).collect(Collectors.toList());
        for (JSONObject signedHashOb : signedHashObs) {
            String hashKey = signedHashOb.keys().next();
            String[] hashIds = hashKey.split("[|]", 5);
            String signedHash = signedHashOb.get(hashKey).toString();

            try {
                statusCode = doInjectSignedHashes(hashIds, signedHash, mpid);

            } catch (SwisscomBatchSignException e) {
                logger.debug("Injecting hash failed with error : {}", e.getMessage());

                //retry
                int retryCounter = 0;
                int maxRetries = 3;
                while (retryCounter < maxRetries) {
                    TimeUnit.MILLISECONDS.sleep(1000);
                    try {
                        doInjectSignedHashes(hashIds, signedHash, mpid);
                        break;
                    } catch (Exception ex) {
                        retryCounter++;
                        logger.debug("FAILED - injecting the document hash for the master transaction {} on retry {} out of {} retires with error: {}",mpid, retryCounter,maxRetries, ex.getMessage());

                        if (retryCounter >= maxRetries) {
                            FailedTransaction failedTxn = new FailedTransaction();
                            failedTxn.setTransactionId(hashIds[0]);
                            failedTxn.setDocumentId(hashIds[1]);
                            failedTxn.setError(ex.getMessage());
                            injectFailedTxDetails.add(failedTxn.toString());
                            injectFailedTx.add(hashIds[0]);

                            statusCode = "400";
                            logger.error("FAILED - maximum retires of injecting the document hash for the transaction : {} document id:  {}  with error: {}",hashIds[0], hashIds[1], ex.getMessage());
                            break;
                        }
                    }
                }

            }

        }
        injectFailedTx = injectFailedTx.stream().distinct().collect(Collectors.toList());

        Map<String, String> injectHashesMap = new HashMap<>();
        injectHashesMap.put("StatusCode", statusCode);
        injectHashesMap.put("FailedTxs", injectFailedTx.toString());
        injectHashesMap.put("ErrorDetails", injectFailedTxDetails.toString());

        return injectHashesMap;
    }

    private String doInjectSignedHashes(String[] ids, String signedHash, String mpid) throws IOException, SwisscomBatchSignException {
        String pid = ids[0];
        String did = ids[1];
        String apid = ids[2];
        String apiNonce = ids[3];

        String sid = ids[4];

        //inject evidence summary
        inject2EvidenceSummary(pid, sid, "session_fields_slave.json", mpid);

        //generate signed hash token
        Map<String, String> tempParams = new HashMap<>();
        tempParams.put("approvalId", apid);
        tempParams.put("signedHash", signedHash);
        tempParams.put("algorithm", "SHA256");
        tempParams.put("nonce", apiNonce);

        String token = prepareTemplate("inject_signature", tempParams);
        token = Base64.getEncoder().encodeToString(token.getBytes());

        JsonObject signedHashJson = Json.createObjectBuilder()
                .add("signedHash", token)
                .build();

        String url = buildPath(getServerPath(), pid, "documents", did, "actions");
        logger.debug("Inject signed hash URL: {}", url);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse(webResource, HTTPMethod.POST, signedHashJson.toString());
        String response = clientResponse.getEntity(String.class);
        if (clientResponse.getStatus() != 200) {
            JSONObject errorResponse = new JSONObject(response);
            throw new SwisscomBatchSignException(errorResponse.get("message").toString(), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_INJECT_SIGNED_HASH_FAILED);
        }
        logger.debug("Inject signed hash response: {}", response);
        return String.valueOf(clientResponse.getStatus());
    }

    private void inject2EvidenceSummary(String pid, String signerId, String sessionFields, String evidence) throws IOException {

        URL pathUrl = Resources.getResource(sessionFields);
        String tokenPayload = Resources.toString(pathUrl, StandardCharsets.UTF_8);

        tokenPayload = tokenPayload.replace("${#packageId}", pid);
        tokenPayload = tokenPayload.replace("${#signerId}", signerId);
        tokenPayload = tokenPayload.replace("${#evidence}", evidence);

        String url = buildPath(getServerPath());
        url = url.replace("packages", "signerAuthenticationTokens");

        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse(webResource, POST, tokenPayload);
        String response = clientResponse.getEntity(String.class);

        JSONObject sessionToken = new JSONObject(response);
        String token = sessionToken.get("value").toString();

        url = url.replace("api/signerAuthenticationTokens", "access?sessionToken=");
        url = url.replaceFirst("/*$", "");
        url = url + token;

        webResource = client.resource(url);
        clientResponse = getClientResponse("application/json", webResource, GET);
        logger.debug("Call is inject master transaction id to evidence summary, status : {}", clientResponse.getStatusInfo());
    }


    //private method
    private static Stream<JSONObject> toStream(JSONArray arr) {
        return StreamSupport.stream(arr.spliterator(), false) // false => not parallel stream
                .map(JSONObject.class::cast);
    }

    private static String getServerPath() {
        return protocol + "://" + server + "/api/packages";
    }

    public enum HTTPMethod {
        POST, GET, DELETE, PUT;
    }

    private static String buildPath(String... subPaths) {
        StringBuilder path = new StringBuilder();
        for (String subPath : subPaths) {
            path.append(subPath);
            path.append('/');
        }
        return path.toString();
    }

    private ClientResponse getClientResponse(WebResource webResource, HTTPMethod method, String payload) {
        switch (method) {
            case POST:
                return webResource
                        .type("application/json")
                        .accept("application/json")
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .post(ClientResponse.class, payload);
            case PUT:
                return webResource
                        .type("application/json")
                        .accept("application/json")
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .put(ClientResponse.class, payload);
        }

        return null;
    }

    private static ClientResponse getClientResponse(String acceptType, WebResource webResource, HTTPMethod method) {
        switch (method) {
            case GET:
                return webResource
                        .accept(acceptType)
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .get(ClientResponse.class);
            case DELETE:
                return webResource
                        .accept(acceptType)
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .delete(ClientResponse.class);
            case POST:
                return webResource
                        .accept(acceptType)
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .post(ClientResponse.class);
            case PUT:
                return webResource
                        .accept(acceptType)
                        .header(authorizationHeaderName, authorizationHeaderValue)
                        .put(ClientResponse.class);
        }

        return null;
    }
}
