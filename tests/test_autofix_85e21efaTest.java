package com.intellyn.billing.service;

import com.intellyn.billing.dto.InvoiceRequest;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.model.PlanType;
import com.intellyn.billing.model.Subscription;
import com.intellyn.billing.repository.InvoiceRepository;
import com.intellyn.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests verifying that calculateTax and generateInvoice handle a null
 * Subscription gracefully (returning BigDecimal.ZERO for tax) instead of throwing
 * a NullPointerException when invoice.getSubscription() is null.
 *
 * Fix verified:
 *  - calculateTax returns ZERO when invoice has no subscription set.
 *  - generateInvoice completes without error when request.getSubscriptionId() is null.
 *  - generateInvoice completes without error when subscriptionRepository finds no match.
 *  - generateInvoice still applies tax correctly when a valid ENTERPRISE subscription exists.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceNullSubscriptionTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, subscriptionRepository);
    }

    // -----------------------------------------------------------------------
    // calculateTax unit tests
    // -----------------------------------------------------------------------

    @Test
    void calculateTax_returnsZero_whenSubscriptionIsNull() {
        Invoice invoice = new Invoice();
        invoice.setAmountCents(10000);
        // subscription deliberately not set — remains null

        BigDecimal tax = invoiceService.calculateTax(invoice);

        assertEquals(BigDecimal.ZERO, tax,
                "calculateTax must return ZERO instead of throwing NPE when subscription is null");
    }

    @Test
    void calculateTax_returnsZero_whenSubscriptionPlanIsNotEnterprise() {
        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.BASIC);

        Invoice invoice = new Invoice();
        invoice.setAmountCents(10000);
        invoice.setSubscription(subscription);

        BigDecimal tax = invoiceService.calculateTax(invoice);

        assertEquals(BigDecimal.ZERO, tax,
                "calculateTax must return ZERO for non-ENTERPRISE plans");
    }

    @Test
    void calculateTax_returnsTaxAmount_whenSubscriptionPlanIsEnterprise() {
        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.ENTERPRISE);

        Invoice invoice = new Invoice();
        invoice.setAmountCents(10000); // $100.00
        invoice.setSubscription(subscription);

        BigDecimal tax = invoiceService.calculateTax(invoice);

        assertTrue(tax.compareTo(BigDecimal.ZERO) > 0,
                "calculateTax must return a positive tax amount for ENTERPRISE plans");
    }

    // -----------------------------------------------------------------------
    // generateInvoice integration-style tests (with mocked repositories)
    // -----------------------------------------------------------------------

    @Test
    void generateInvoice_doesNotThrow_whenSubscriptionIdIsNull() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-001");
        request.setAmountCents(5000);
        request.setStripeInvoiceId("stripe-001");
        request.setSubscriptionId(null); // key edge case

        Invoice savedInvoice = new Invoice();
        savedInvoice.setCustomerId("cust-001");
        savedInvoice.setAmountCents(5000);
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        Invoice result = assertDoesNotThrow(
                () -> invoiceService.generateInvoice(request),
                "generateInvoice must not throw NPE when subscriptionId is null");

        assertNotNull(result);
        verify(subscriptionRepository, never()).findById(any());
    }

    @Test
    void generateInvoice_doesNotThrow_whenSubscriptionNotFoundInRepository() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-002");
        request.setAmountCents(8000);
        request.setStripeInvoiceId("stripe-002");
        request.setSubscriptionId("sub-missing");

        when(subscriptionRepository.findById("sub-missing")).thenReturn(Optional.empty());

        Invoice savedInvoice = new Invoice();
        savedInvoice.setCustomerId("cust-002");
        savedInvoice.setAmountCents(8000);
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        Invoice result = assertDoesNotThrow(
                () -> invoiceService.generateInvoice(request),
                "generateInvoice must not throw NPE when subscription is not found in repository");

        assertNotNull(result);
        verify(subscriptionRepository).findById("sub-missing");
    }

    @Test
    void generateInvoice_appliesTax_whenEnterpriseSubscriptionFound() {
        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.ENTERPRISE);

        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-003");
        request.setAmountCents(10000); // $100.00
        request.setStripeInvoiceId("stripe-003");
        request.setSubscriptionId("sub-enterprise");

        when(subscriptionRepository.findById("sub-enterprise"))
                .thenReturn(Optional.of(subscription));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = assertDoesNotThrow(
                () -> invoiceService.generateInvoice(request),
                "generateInvoice must not throw when a valid ENTERPRISE subscription is present");

        assertNotNull(result);
        // 8% tax on $100 = $8 => 800 cents added to original 10000
        assertTrue(result.getAmountCents() > 10000,
                "Amount should include tax for ENTERPRISE subscription");
    }

    @Test
    void generateInvoice_noTaxAdded_whenSubscriptionIdIsNull() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-004");
        request.setAmountCents(10000);
        request.setStripeInvoiceId("stripe-004");
        request.setSubscriptionId(null);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.generateInvoice(request);

        assertEquals(10000, result.getAmountCents(),
                "No tax should be added when there is no subscription");
    }
}