package com.intellyn.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookPayload {
    private String type;

    @JsonProperty("data")
    private WebhookData data;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public WebhookData getData() { return data; }
    public void setData(WebhookData data) { this.data = data; }
}
