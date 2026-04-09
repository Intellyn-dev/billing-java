package com.intellyn.billing.service;

import com.intellyn.billing.dto.InvoiceRequest;
import com.intellyn.billing.model.Invoice;
import com.intellyn.billing.model.InvoiceStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regression tests verifying the fix for NullPointerException in generateInvoice/calculateTax.
 *
 * Previously, if subscriptionId was null or no subscription was found for the given ID,
 * invoice.subscription remained null, and calculateTax would throw a NullPointerException
 * when calling invoice.getSubscription().getPlanType().
 *
 * The fix ensures:
 * 1. calculateTax returns BigDecimal.ZERO when invoice.getSubscription() is null.
 * 2. generateInvoice completes without exception when subscriptionId is null.
 * 3. generateInvoice completes without exception when subscriptionRepository returns empty Optional.
 * 4. generateInvoice still applies tax correctly when a valid subscription is found.
 */
@ExtendWith(MockitoExtension.class)
public class InvoiceServiceRegressionTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, subscriptionRepository);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Verifies that generateInvoice does not throw NullPointerException when
     * request.getSubscriptionId() is null (no subscription lookup attempted).
     */
    @Test
    void generateInvoice_nullSubscriptionId_doesNotThrowAndReturnsInvoice() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-001");
        request.setAmountCents(1000);
        request.setStripeInvoiceId("stripe-abc");
        request.setSubscriptionId(null);

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-001", result.getCustomerId());
        // No tax applied when subscription is null
        assertEquals(1000, result.getAmountCents());
        assertNull(result.getSubscription());
        verify(subscriptionRepository, never()).findById(anyString());
    }

    /**
     * Verifies that generateInvoice does not throw NullPointerException when
     * subscriptionRepository.findById() returns an empty Optional (subscription not found).
     */
    @Test
    void generateInvoice_subscriptionNotFound_doesNotThrowAndReturnsInvoice() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-002");
        request.setAmountCents(2000);
        request.setStripeInvoiceId("stripe-def");
        request.setSubscriptionId("sub-nonexistent");

        when(subscriptionRepository.findById("sub-nonexistent")).thenReturn(Optional.empty());

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-002", result.getCustomerId());
        // No tax applied when subscription is not found
        assertEquals(2000, result.getAmountCents());
        assertNull(result.getSubscription());
    }

    /**
     * Verifies that calculateTax returns BigDecimal.ZERO directly when
     * invoice.getSubscription() is null, without throwing NullPointerException.
     */
    @Test
    void calculateTax_nullSubscription_returnsZeroWithoutException() {
        Invoice invoice = new Invoice();
        invoice.setAmountCents(5000);
        // subscription intentionally not set

        BigDecimal tax = assertDoesNotThrow(() -> invoiceService.calculateTax(invoice));

        assertEquals(BigDecimal.ZERO, tax);
    }

    /**
     * Verifies that generateInvoice correctly applies tax for ENTERPRISE plan
     * when a valid subscription is found, confirming the happy path still works.
     */
    @Test
    void generateInvoice_enterpriseSubscriptionFound_appliesTaxCorrectly() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-003");
        request.setAmountCents(10000); // $100.00
        request.setStripeInvoiceId("stripe-ghi");
        request.setSubscriptionId("sub-enterprise-1");

        Subscription subscription = new Subscription();
        subscription.setId("sub-enterprise-1");
        subscription.setPlanType(PlanType.ENTERPRISE);

        when(subscriptionRepository.findById("sub-enterprise-1")).thenReturn(Optional.of(subscription));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-003", result.getCustomerId());
        assertNotNull(result.getSubscription());
        assertEquals(PlanType.ENTERPRISE, result.getSubscription().getPlanType());
        // 8% tax on $100.00 = $8.00 = 800 cents added to 10000
        assertEquals(10800, result.getAmountCents());
    }

    /**
     * Verifies that generateInvoice does not apply tax for non-ENTERPRISE plans,
     * confirming calculateTax returns zero for non-enterprise subscriptions.
     */
    @Test
    void generateInvoice_nonEnterpriseSubscriptionFound_noTaxApplied() {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-004");
        request.setAmountCents(5000);
        request.setStripeInvoiceId("stripe-jkl");
        request.setSubscriptionId("sub-basic-1");

        Subscription subscription = new Subscription();
        subscription.setId("sub-basic-1");
        subscription.setPlanType(PlanType.BASIC);

        when(subscriptionRepository.findById("sub-basic-1")).thenReturn(Optional.of(subscription));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-004", result.getCustomerId());
        // No tax for BASIC plan
        assertEquals(5000, result.getAmountCents());
    }
}