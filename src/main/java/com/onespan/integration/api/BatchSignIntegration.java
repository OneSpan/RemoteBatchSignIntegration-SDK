package com.onespan.integration.api;

import com.google.common.io.Resources;
import com.onespan.integration.api.exception.SwisscomBatchSignErrorCode;
import com.onespan.integration.api.exception.SwisscomBatchSignException;
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
        logger.info("fetch unsigned transaction {}", "-->");

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

        logger.info("filtered swcm transactions: {}", filteredTxArr.length());

        //count tx
        long totalTx = toStream(filteredTxArr).count();

        //count docs in all packages
        long totalDoc = toStream(filteredTxArr)
                .flatMap(tx -> toStream(tx.getJSONArray("documents")))
                .count();

        logger.info("total documents to batch sign: {}", totalDoc);

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

        logger.info("getFilteredTransaction URL: {}", url);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);

        String response = clientResponse.getEntity(String.class);

        logger.debug("getFilteredTransaction Response: {}", response);

        JSONObject txJsonOb = new JSONObject(response);
        JSONArray destinationArray;

        if (clientResponse.getStatus() == 200 && txJsonOb.has("results")) {
            destinationArray = txJsonOb.getJSONArray("results");

        } else {
            throw new SwisscomBatchSignException("empty results in response", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
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

                logger.info("getFilteredTransactionPagination URL: {}", url);
                WebResource webResource = client.resource(url);
                ClientResponse clientResponse = getClientResponse("application/json", webResource, GET);

                String response = clientResponse.getEntity(String.class);

                logger.debug("getFilteredTransaction Response: {}", response);

                JSONObject jo = new JSONObject(response);
                JSONArray sourceArray;

                //
                if (clientResponse.getStatus() == 200 && jo.has("results")) {
                    sourceArray = jo.getJSONArray("results");

                } else {
                    throw new SwisscomBatchSignException("empty results in response", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
                }

                for (int j = 0; j < sourceArray.length(); j++) {
                    destinationArray.put(sourceArray.getJSONObject(j));
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

        logger.debug("apikey user email {}", email);

        return email;

    }


    public Map<String, List<JSONObject>> extractHashes(List<JSONObject> selectedtxs) throws Exception {
        logger.info("extract document hash {}", "-->");

        //check total doc no more than 250 limit
        Long doc2Hash = selectedtxs.stream()
                .flatMap(tx -> toStream(tx.getJSONArray("documents")))
                .count();

        if (doc2Hash > 250) {
            throw new SwisscomBatchSignException("batch sign cannot sign more than 250 documents", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);
        }

        String apiUserEmail = getSignerEmail();

        List<JSONObject> txDocHashes = new ArrayList<>();

        int totalDocHashed = 0;

        boolean isSelfSign = false;

        //loop selected txs
        List<String> docHashFailedTx = new ArrayList<>();
        for (JSONObject tx : selectedtxs) {

            logger.info("selected transaction name: {}", tx.get("name"));
            String pid = tx.get("id").toString();

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

            //
            JSONObject docHash = new JSONObject();
            String did;
            String apid = null;

            //loop tx documents
            String hashPayload = null;

            String hashUrl = null;
            try {
                for (JSONObject doc : documents) {
                    logger.info("selected document name: {}", doc.get("name"));

                    did = doc.get("id").toString();

                    List<JSONObject> approvals = new ArrayList<>();
                    String finalRid = Rid;
                    approvals.addAll(toStream(doc.getJSONArray("approvals"))
                            .filter(ap -> finalRid.equals(ap.get("role")))
                            .collect(Collectors.toList()));

                    //handle no approval for batch signer
                    if (approvals.isEmpty()) {
                        logger.info("no approval for role, skip this document");
                        continue;
                    }


                    //loop approvals for accepted -->
                    String timestamp = null;
                    for (JSONObject ap : approvals) {

                        apid = ap.get("id").toString();
                        logger.info("start api call accept approval: {}", apid);
                        String response = "{}";

                        if (ap.get("accepted").equals(null)) {
                            String url = buildPath(getServerPath(), pid, "documents", did, "approvals", apid, "sign");
                            logger.info("Accept approvals URL: {}", url);
                            WebResource webResource = client.resource(url);
                            ClientResponse clientResponse = getClientResponse(webResource, POST, "{}");
                            if (clientResponse.getStatus() != 200) {
                                logger.info("status: {}", clientResponse.getStatus());
                                throw new Error("Error for integration extract hash, please update transaction info: " + clientResponse.getEntity(String.class));
                            }
                            response = clientResponse.getEntity(String.class);

                            logger.debug("accepted.resp {}", response);

                            JSONObject resp = new JSONObject(response);
                            timestamp = resp.get("accepted").toString();

                            logger.info("acceptedtime: {}", timestamp);

                        } else {

                            timestamp = ap.get("accepted").toString();
                            logger.info("accepted");

                        }

                    }  //end accepted approvals loop


                    //generate hash payload outside
                    if (isSelfSign && "default-consent".equals(did)) {
                        logger.info("self-sign skip default-consent");

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
                        logger.info("Hash Document URL: " + hashUrl);

                        String response = null;
                        response = doExtractHash(hashPayload, hashUrl);

                        logger.debug("signature.placeholder: {}", response);

                        //extract doc hash from response
                        String value = (String) new JSONObject(response).get("value");

                        byte[] xml = Base64.getDecoder().decode(value);

                        Node node = extractXmlTag(new String(xml), "hash")
                                .orElseThrow(() -> new Exception("Failed to get esl certificate"));
                        String hash = node.getTextContent();
                        logger.info("extracted hash: {}", hash);

                        String piddidapidnonce = pid + "|" + did + "|" + apid + "|" + tempParams.get("nonce") + "|" + Sid;
                        docHash.put(piddidapidnonce, hash);
                        logger.debug("hash key-value: {}", docHash);

                    }//end doc loop
                }

                txDocHashes.add(docHash);

            } catch (Exception e) {
                logger.info("extract hash failed, will do retry: {}", e.getMessage());
                logger.info("failed transacton: {}", pid);

                docHashFailedTx.add(pid);

                //retry
                int retryCounter = 0;
                int maxRetries = 3;
                while (retryCounter < maxRetries) {
                    TimeUnit.MILLISECONDS.sleep(1000);
                    try {
                        doExtractHash(hashPayload, hashUrl);
                        break;
                    } catch (Exception ex) {
                        retryCounter++;
                        logger.info("FAILED - Command failed on retry {} of {} error: {}" , retryCounter, maxRetries, ex);

                        if (retryCounter >= maxRetries) {
                            logger.info("Max retries exceeded.");
                            break;
                        }
                    }
                }


            }//catch

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
            throw new SwisscomBatchSignException(clientResponse.getEntity(String.class), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);
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


    //
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
            throw new Exception("Fail to parse xml string, caused by ");
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
        logger.info("create master transaction {}", "-->");

        String requestURL = buildPath(getServerPath());
        logger.info("create Transaction URL: {}", requestURL);

        URL urlnew = Resources.getResource("payload_master_package_draft.json");
        String payloadTmpl = Resources.toString(urlnew, StandardCharsets.UTF_8);

        //update payload info
        payloadTmpl = payloadTmpl.replace("${batchSigner@email}", signerEmail);
        payloadTmpl = payloadTmpl.replace("${batchSignMasterPackageName}", packageName);


        //inject hashes arr to payload
        JSONObject hashesPayload = new JSONObject(payloadTmpl);
        JSONObject data = hashesPayload.getJSONObject("data");
        data.put("scBatchSignHashes", batchSignHashes);

        logger.debug("master package payload with hashes: " + hashesPayload);

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

            logger.info("created master pid: {}", response);

        } else {

            // get and write out response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            String inputLine;
            response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            logger.info("create transaction failed: {}", response);

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
        logger.info("fetch signed hashes {}", "-->");

        String url = buildPath(getServerPath(), guid);
        logger.info("get master Transaction URL: " + url);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse("application/json", webResource, HTTPMethod.GET);

        if (clientResponse.getStatus() != 200) {
            throw new SwisscomBatchSignException(String.valueOf(clientResponse.getStatus()), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
        }

        String response = clientResponse.getEntity(String.class);
        logger.debug("get master Transaction Response: {}", response);

        JSONObject signedHashes = new JSONObject(response);
        JSONArray signedHashArr = signedHashes.getJSONObject("data").getJSONArray("scBatchSignHashes");
        if (signedHashArr.isEmpty()) {
            throw new SwisscomBatchSignException("at least has one signed hash in master package", SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_RESPONSE_RESULT_UNAVAILABLE);
        }

        return response;
    }


    public Map<String, String> injectSignedHash(JSONArray signedHashes, String mpid) throws Exception {
        logger.info("inject signed hashes to slave transaction {}", "-->");

        List<String> injectFailedTx = new ArrayList<>();
        String injectSuccessResp = null;

        List<JSONObject> signedHashObs = toStream(signedHashes).collect(Collectors.toList());
        for (JSONObject signedHashOb : signedHashObs) {
            String hashKey = signedHashOb.keys().next();
            String[] hashIds = hashKey.split("[|]", 5);
            String signedHash = signedHashOb.get(hashKey).toString();

            try {
                injectSuccessResp = doInjectSignedHashes(hashIds, signedHash, mpid);

            } catch (SwisscomBatchSignException e) {
                logger.info("inject hash failed, will do retry: {}", e.getMessage());
                injectFailedTx.add(hashIds[0]);

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
                        logger.info("FAILED - Command failed on retry {} of {} , error: {}", retryCounter, maxRetries, ex);
                        if (retryCounter >= maxRetries) {
                            injectSuccessResp = "403 Forbidden";
                            logger.info("Max retries exceeded.");
                            break;
                        }
                    }
                }

            }

        }
        injectFailedTx = injectFailedTx.stream().distinct().collect(Collectors.toList());

        Map<String, String> injectHashesMap = new HashMap<>();
        injectHashesMap.put("injectedSuccessStatusCode", injectSuccessResp);
        injectHashesMap.put("injectedFailedTxs", injectFailedTx.toString());

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

        logger.debug("signedHashToken: {}", signedHashJson);

        String url = buildPath(getServerPath(), pid, "documents", did, "actions");
        logger.info("inject signed hash URL: {}", url);
        WebResource webResource = client.resource(url);
        ClientResponse clientResponse = getClientResponse(webResource, HTTPMethod.POST, signedHashJson.toString());
        if (clientResponse.getStatus() != 200) {
            throw new SwisscomBatchSignException(clientResponse.getEntity(String.class), SwisscomBatchSignErrorCode.SWISSCOM_BATCH_SIGN_DOC_EXCEED_LIMIT);
        }
        String response = clientResponse.getEntity(String.class);
        logger.info("getTransaction Response: {}", response);
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
        logger.info("inject master pid as Evidence: {}", clientResponse.getStatusInfo());
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
