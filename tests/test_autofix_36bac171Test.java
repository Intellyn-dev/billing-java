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
import static org.mockito.Mockito.*;

/**
 * Regression tests verifying the fix for NullPointerException in calculateTax
 * when invoice.getSubscription() is null.
 *
 * Previously, generateInvoice could produce an Invoice with a null subscription
 * (when subscriptionId is null or not found), and calculateTax would then throw
 * a NullPointerException calling invoice.getSubscription().getPlanType().
 *
 * The fix adds a null-check in calculateTax, returning BigDecimal.ZERO when
 * subscription is null, preventing the NPE.
 */
@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, subscriptionRepository);
    }

    private InvoiceRequest buildRequest(String customerId, int amountCents,
                                        String stripeInvoiceId, String subscriptionId) {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId(customerId);
        request.setAmountCents(amountCents);
        request.setStripeInvoiceId(stripeInvoiceId);
        request.setSubscriptionId(subscriptionId);
        return request;
    }

    @Test
    void generateInvoice_withNullSubscriptionId_doesNotThrowNPE() {
        /**
         * Verifies that when subscriptionId is null, generateInvoice completes
         * without throwing NullPointerException and returns a saved Invoice
         * with no tax applied (amountCents unchanged).
         */
        InvoiceRequest request = buildRequest("cust-001", 5000, "stripe-inv-001", null);

        Invoice savedInvoice = new Invoice();
        savedInvoice.setCustomerId("cust-001");
        savedInvoice.setAmountCents(5000);
        savedInvoice.setStripeInvoiceId("stripe-inv-001");

        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-001", result.getCustomerId());
        assertEquals(5000, result.getAmountCents());
        verify(subscriptionRepository, never()).findById(any());
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    @Test
    void generateInvoice_withSubscriptionIdNotFound_doesNotThrowNPE() {
        /**
         * Verifies that when subscriptionId is provided but no matching Subscription
         * exists in the repository, generateInvoice completes without throwing
         * NullPointerException and returns a saved Invoice with no tax applied.
         */
        InvoiceRequest request = buildRequest("cust-002", 3000, "stripe-inv-002", "sub-missing");

        when(subscriptionRepository.findById("sub-missing")).thenReturn(Optional.empty());

        Invoice savedInvoice = new Invoice();
        savedInvoice.setCustomerId("cust-002");
        savedInvoice.setAmountCents(3000);
        savedInvoice.setStripeInvoiceId("stripe-inv-002");

        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-002", result.getCustomerId());
        assertEquals(3000, result.getAmountCents());
        verify(subscriptionRepository, times(1)).findById("sub-missing");
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    @Test
    void calculateTax_withNullSubscription_returnsZero() {
        /**
         * Directly verifies that calculateTax returns BigDecimal.ZERO when
         * invoice.getSubscription() is null, without throwing NullPointerException.
         */
        Invoice invoice = new Invoice();
        invoice.setCustomerId("cust-003");
        invoice.setAmountCents(10000);

        BigDecimal tax = assertDoesNotThrow(() -> invoiceService.calculateTax(invoice));

        assertNotNull(tax);
        assertEquals(0, BigDecimal.ZERO.compareTo(tax));
    }

    @Test
    void calculateTax_withEnterpriseSubscription_returnsTax() {
        /**
         * Verifies that calculateTax correctly applies the tax rate when
         * subscription is present and plan type is ENTERPRISE.
         */
        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.ENTERPRISE);

        Invoice invoice = new Invoice();
        invoice.setCustomerId("cust-004");
        invoice.setAmountCents(10000);
        invoice.setSubscription(subscription);

        BigDecimal tax = assertDoesNotThrow(() -> invoiceService.calculateTax(invoice));

        assertNotNull(tax);
        assertTrue(tax.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateTax_withNonEnterpriseSubscription_returnsZero() {
        /**
         * Verifies that calculateTax returns BigDecimal.ZERO when subscription
         * is present but plan type is not ENTERPRISE.
         */
        Subscription subscription = new Subscription();
        subscription.setPlanType(PlanType.BASIC);

        Invoice invoice = new Invoice();
        invoice.setCustomerId("cust-005");
        invoice.setAmountCents(8000);
        invoice.setSubscription(subscription);

        BigDecimal tax = assertDoesNotThrow(() -> invoiceService.calculateTax(invoice));

        assertNotNull(tax);
        assertEquals(0, BigDecimal.ZERO.compareTo(tax));
    }

    @Test
    void generateInvoice_withEnterpriseSubscription_appliesTaxToAmount() {
        /**
         * Verifies that when a valid ENTERPRISE subscription is found,
         * tax is calculated and added to the invoice amount correctly.
         */
        InvoiceRequest request = buildRequest("cust-006", 10000, "stripe-inv-006", "sub-enterprise");

        Subscription subscription = new Subscription();
        subscription.setId("sub-enterprise");
        subscription.setPlanType(PlanType.ENTERPRISE);

        when(subscriptionRepository.findById("sub-enterprise"))
                .thenReturn(Optional.of(subscription));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals("cust-006", result.getCustomerId());
        assertTrue(result.getAmountCents() > 10000,
                "Expected tax to be added to amount for ENTERPRISE plan");
        assertNotNull(result.getSubscription());
        assertEquals(PlanType.ENTERPRISE, result.getSubscription().getPlanType());
    }
}