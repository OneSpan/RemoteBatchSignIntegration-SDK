package com.onespan.integration.api.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FailedTransaction {

    private String transactionId;

    private String documentId;

    private String error;

}
