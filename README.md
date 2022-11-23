## RemoteBatchSignIntegration-SDK
A Java SDK supporting batch signing

---

### Requirements
* JDK 8
---
### Running

#### Configure Batch Signer and Sender Account
1. Enable Owner Account with jTSP Settings in BackOffice

2. Create Batch Signer and Batch Sender in SenderUI > Admin > Senders > invite by email

3. Grant manager right to created Senders

4. Fetch created Sender apikey by login and call ```api/session/apiKey```


#### Quick Maven Test Project
1. To build simple test maven project run

``` 
mvn archetype:generate -DgroupId=com.onespan.integration.api -DartifactId=batchSignIntegrationTest -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.1 -DinteractiveMode=false 
```

2. To configure, Open test project in IntelliJ

_Edit pom file by add to_

* properties
``` 
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
  </properties>
```

* dependency
    * update junit version to 4.13.2
    * add batch sign integration lib
```
  <!-- add batch sign integration lib -->
  <dependency>
    <groupId>com.onespan.integration</groupId>
    <artifactId>remote-batch-sign-integration</artifactId>
    <version>0.0.6</version>
    <classifier>jar-with-dependencies</classifier>
  </dependency>
  
```

_Create resources folder under project root/test directory_

From RemoteBatchSignIntegration Project
* Copy log4j2.properties file to resources folder
* Copy TestBatchSignIntegrationDemo.java to test/java/com/onespan/integration/api/

_The whole test project structure is_
```
├── pom.xml
└── src
    ├── main
    │   └── java
    │       └── com
    │           └── onespan
    │               └── integration
    │                   └── api
    │                       └── App.java
    └── test
        ├── java
        │   └── com
        │       └── onespan
        │           └── integration
        │               └── api
        │                   ├── AppTest.java
        │                   └── TestBatchSignIntegrationDemo.java
        └── resources
            └── log4j2.properties
```

3. To run the test case

Open TestBatchSignIntegrationDemo.java, configure variables

* senderApiKey={_Sender ApiKey value_}
* signerApiKey={_Signer ApiKey value_}
* server={_Environment domain name_}

* signerEmail={_Singer Email address_}
* packageName={_Any String for master package name_}
* signingMethod={_swisscomdirect:eidas_} //option: _swisscomdirect:zertes_

Batch sign integration work flow:
> Step 1: Run Test case to get unsigned transaction data

    IntegrationGetUnsignedTransactonWithFilter

> Step 2: Run Test case to get extracted document hashes from selected transactions

    IntegrationExtractHashesFromSelectedTransacton

> Step 3: Run Test cast to create master package

    IntegrationCreateMasterTransacton

> Step 4: sign master package as jtsp signature

> Step 5: Run Test cast to fetch signed hashes from master package and inject to slave packages

    IntegrationInjectSignedHash

> Step 6: Login esl as signer to verify all slave packages are signed properly

---

#### Performance

Test result shows that the package size impact the batch sign performance, recommended package for batch sign is a package with 2~4 documents

![BatchSignIntegrationPerformance](src/main/resources/asserts/images/jTSP%20Performance%20benchmarking%20to%20assess%20batch%20signing.png)

Recommendation for batch sign Integration: for better performance, break down docs into more trx rather than a single trx with several documents.


