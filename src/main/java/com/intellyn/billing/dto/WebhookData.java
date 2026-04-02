package com.intellyn.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookData {
    @JsonProperty("invoice_id")
    private String invoiceId;

    @JsonProperty("customer_id")
    private String customerId;

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
