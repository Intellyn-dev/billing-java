package com.intellyn.billing.service;

import com.intellyn.billing.dto.InvoiceRequest;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.model.InvoiceStatus;
import com.intellyn.billing.model.PlanType;
import com.intellyn.billing.model.Subscription;
import com.intellyn.billing.repository.InvoiceRepository;
import com.intellyn.billing.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class InvoiceService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    private final InvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          SubscriptionRepository subscriptionRepository) {
        this.invoiceRepository = invoiceRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public Invoice generateInvoice(InvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(request.getCustomerId());
        invoice.setAmountCents(request.getAmountCents());
        invoice.setStripeInvoiceId(request.getStripeInvoiceId());

        if (request.getSubscriptionId() != null) {
            subscriptionRepository.findById(request.getSubscriptionId())
                .ifPresent(invoice::setSubscription);
        }

        BigDecimal tax = calculateTax(invoice);
        int taxCents = tax.multiply(BigDecimal.valueOf(100)).intValue();
        invoice.setAmountCents(invoice.getAmountCents() + taxCents);

        return invoiceRepository.save(invoice);
    }

    public BigDecimal calculateTax(Invoice invoice) {
        Subscription subscription = invoice.getSubscription();
        if (subscription == null) {
            return BigDecimal.ZERO;
        }
        PlanType plan = subscription.getPlanType();
        return plan == PlanType.ENTERPRISE
            ? invoice.getAmount().multiply(TAX_RATE)
            : BigDecimal.ZERO;
    }

    public Invoice getInvoice(String id) {
        return invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
    }
}
