package com.intellyn.billing.service;

import com.intellyn.billing.dto.WebhookPayload;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.model.InvoiceStatus;
import com.intellyn.billing.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final InvoiceRepository invoiceRepository;
    private final SubscriptionService subscriptionService;

    public StripeWebhookService(InvoiceRepository invoiceRepository,
                                SubscriptionService subscriptionService) {
        this.invoiceRepository = invoiceRepository;
        this.subscriptionService = subscriptionService;
    }

    public void processEvent(WebhookPayload payload) {
        if ("invoice.payment_succeeded".equals(payload.getType())) {
            String invoiceId = payload.getData().getInvoiceId();
            Invoice invoice = invoiceRepository.findByStripeInvoiceId(invoiceId)
                .orElse(null);
            if (invoice == null) {
                log.warn("Invoice not found for stripe id: {}", invoiceId);
                return;
            }
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setUpdatedAt(Instant.now());
            invoiceRepository.save(invoice);
            subscriptionService.activatePlan(invoice.getCustomerId());
            log.info("Processed payment for invoice {}", invoiceId);
        }
    }
}
