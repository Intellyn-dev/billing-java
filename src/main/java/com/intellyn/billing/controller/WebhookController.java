package com.intellyn.billing.controller;

import com.intellyn.billing.dto.WebhookPayload;
import com.intellyn.billing.service.StripeWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final StripeWebhookService webhookService;

    public WebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.OK)
    public void handleStripeWebhook(@RequestBody WebhookPayload payload) {
        webhookService.processEvent(payload);
    }
}
