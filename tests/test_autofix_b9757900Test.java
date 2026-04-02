package com.intellyn.billing.service;

import com.intellyn.billing.dto.InvoiceRequest;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.model.PlanType;
import com.intellyn.billing.model.Subscription;
import com.intellyn.billing.repository.InvoiceRepository;
import com.intellyn.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests verifying the fix for NullPointerException in InvoiceService.
 *
 * Fix verified:
 * 1. When InvoiceRequest.getSubscriptionId() is null, generateInvoice completes
 *    without throwing NullPointerException and returns an Invoice with zero tax applied.
 * 2. When a subscriptionId is provided but no matching Subscription is found,
 *    generateInvoice completes without throwing NullPointerException and returns
 *    an Invoice with zero tax applied.
 * 3. calculateTax returns BigDecimal.ZERO (not NPE) when invoice.getSubscription() is null.
 * 4. When a valid ENTERPRISE subscription is found, tax is correctly calculated and applied.
 */
public class InvoiceServiceTest {

    private InvoiceRepository invoiceRepository;
    private SubscriptionRepository subscriptionRepository;
    private InvoiceService invoiceService;

    @BeforeEach
    public void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        invoiceService = new InvoiceService(invoiceRepository, subscriptionRepository);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void generateInvoice_nullSubscriptionId_doesNotThrowAndReturnsSavedInvoice() {
        // Verifies fix: null subscriptionId must not cause NPE in calculateTax
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-001");
        request.setAmountCents(1000);
        request.setStripeInvoiceId("stripe-abc");
        request.setSubscriptionId(null);

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-001", result.getCustomerId());
        // No tax should be added when subscription is null
        assertEquals(1000, result.getAmountCents());
        assertNull(result.getSubscription());
        verify(subscriptionRepository, never()).findById(any());
    }

    @Test
    public void generateInvoice_subscriptionIdNotFound_doesNotThrowAndReturnsSavedInvoice() {
        // Verifies fix: missing subscription must not cause NPE in calculateTax
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-002");
        request.setAmountCents(2000);
        request.setStripeInvoiceId("stripe-def");
        request.setSubscriptionId("sub-missing");

        when(subscriptionRepository.findById("sub-missing")).thenReturn(Optional.empty());

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-002", result.getCustomerId());
        // No tax should be added when subscription is not found
        assertEquals(2000, result.getAmountCents());
        assertNull(result.getSubscription());
    }

    @Test
    public void calculateTax_nullSubscription_returnsZeroWithoutException() {
        // Verifies fix: calculateTax must return ZERO, not throw NPE, when subscription is null
        Invoice invoice = new Invoice();
        invoice.setAmountCents(5000);

        BigDecimal tax = assertDoesNotThrow(() -> invoiceService.calculateTax(invoice));

        assertEquals(BigDecimal.ZERO, tax);
    }

    @Test
    public void generateInvoice_enterpriseSubscriptionFound_appliesTaxCorrectly() {
        // Verifies fix: happy path still works — ENTERPRISE plan gets 8% tax applied
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-003");
        request.setAmountCents(10000); // $100.00
        request.setStripeInvoiceId("stripe-ghi");
        request.setSubscriptionId("sub-enterprise");

        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.ENTERPRISE);

        when(subscriptionRepository.findById("sub-enterprise")).thenReturn(Optional.of(subscription));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-003", result.getCustomerId());
        assertNotNull(result.getSubscription());
        assertEquals(PlanType.ENTERPRISE, result.getSubscription().getPlanType());
        // 8% tax on $100.00 = $8.00 => 800 cents added => total 10800
        assertEquals(10800, result.getAmountCents());
    }

    @Test
    public void generateInvoice_nonEnterpriseSubscriptionFound_doesNotApplyTax() {
        // Verifies fix: non-ENTERPRISE plan results in zero tax, no NPE
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-004");
        request.setAmountCents(5000);
        request.setStripeInvoiceId("stripe-jkl");
        request.setSubscriptionId("sub-basic");

        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.BASIC);

        when(subscriptionRepository.findById("sub-basic")).thenReturn(Optional.of(subscription));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals(5000, result.getAmountCents());
        assertNotNull(result.getSubscription());
    }
}