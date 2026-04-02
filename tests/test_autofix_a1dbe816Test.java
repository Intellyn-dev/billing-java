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
 * Regression tests verifying the fix for the NullPointerException in calculateTax.
 *
 * Before the fix, calculateTax would call invoice.getSubscription().getPlanType()
 * without a null check, causing an NPE when:
 *   1. request.getSubscriptionId() is null (no subscription set on invoice), or
 *   2. no subscription is found for the given ID (subscription remains null).
 *
 * The fix adds a null guard in calculateTax: if invoice.getSubscription() == null,
 * return BigDecimal.ZERO instead of dereferencing null.
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

    private Invoice savedInvoiceCapture(Invoice invoice) {
        return invoice;
    }

    @Test
    void calculateTax_returnsZero_whenSubscriptionIsNull() {
        // Verifies the fix: calculateTax must not throw NPE when subscription is null,
        // and must return BigDecimal.ZERO.
        Invoice invoice = new Invoice();
        invoice.setCustomerId("cust-001");
        invoice.setAmountCents(10000);
        // subscription is intentionally not set (remains null)

        BigDecimal tax = invoiceService.calculateTax(invoice);

        assertEquals(BigDecimal.ZERO, tax,
                "calculateTax should return ZERO when subscription is null, not throw NPE");
    }

    @Test
    void generateInvoice_doesNotThrow_whenSubscriptionIdIsNull() {
        // Verifies the fix: generateInvoice must complete without NPE when
        // request.getSubscriptionId() is null, leaving invoice.subscription null.
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-002");
        request.setAmountCents(5000);
        request.setStripeInvoiceId("stripe-abc");
        request.setSubscriptionId(null);

        Invoice expectedSaved = new Invoice();
        expectedSaved.setCustomerId("cust-002");
        expectedSaved.setAmountCents(5000);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request),
                "generateInvoice should not throw NPE when subscriptionId is null");

        assertNotNull(result);
        assertEquals("cust-002", result.getCustomerId());
        // No tax applied since subscription is null, amount should remain 5000
        assertEquals(5000, result.getAmountCents());
        assertNull(result.getSubscription());

        verify(subscriptionRepository, never()).findById(any());
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void generateInvoice_doesNotThrow_whenSubscriptionIdNotFound() {
        // Verifies the fix: generateInvoice must complete without NPE when
        // subscriptionId is provided but no subscription is found (returns empty Optional),
        // leaving invoice.subscription null.
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-003");
        request.setAmountCents(8000);
        request.setStripeInvoiceId("stripe-xyz");
        request.setSubscriptionId("nonexistent-sub-id");

        when(subscriptionRepository.findById("nonexistent-sub-id")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request),
                "generateInvoice should not throw NPE when subscription is not found");

        assertNotNull(result);
        assertEquals("cust-003", result.getCustomerId());
        // No tax applied since subscription was not found, amount should remain 8000
        assertEquals(8000, result.getAmountCents());
        assertNull(result.getSubscription());

        verify(subscriptionRepository).findById("nonexistent-sub-id");
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void generateInvoice_appliesTax_whenEnterpriseSubscriptionFound() {
        // Verifies the positive case: when an ENTERPRISE subscription is found,
        // tax is correctly applied (8% of amount).
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-004");
        request.setAmountCents(10000); // $100.00
        request.setStripeInvoiceId("stripe-ent");
        request.setSubscriptionId("sub-enterprise-1");

        Subscription subscription = new Subscription();
        subscription.setCustomerId("cust-004");
        subscription.setPlanType(PlanType.ENTERPRISE);

        when(subscriptionRepository.findById("sub-enterprise-1")).thenReturn(Optional.of(subscription));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals(PlanType.ENTERPRISE, result.getSubscription().getPlanType());
        // 8% tax on $100.00 = $8.00 = 800 cents; total = 10000 + 800 = 10800
        assertEquals(10800, result.getAmountCents());
    }

    @Test
    void generateInvoice_doesNotApplyTax_whenProSubscriptionFound() {
        // Verifies that non-ENTERPRISE plans (e.g. PRO) result in zero tax.
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId("cust-005");
        request.setAmountCents(6000);
        request.setStripeInvoiceId("stripe-pro");
        request.setSubscriptionId("sub-pro-1");

        Subscription subscription = new Subscription();
        subscription.setCustomerId("cust-005");
        subscription.setPlanType(PlanType.PRO);

        when(subscriptionRepository.findById("sub-pro-1")).thenReturn(Optional.of(subscription));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = assertDoesNotThrow(() -> invoiceService.generateInvoice(request));

        assertNotNull(result);
        assertEquals(PlanType.PRO, result.getSubscription().getPlanType());
        // No tax for PRO plan, amount remains 6000
        assertEquals(6000, result.getAmountCents());
    }

    @Test
    void calculateTax_returnsZero_whenSubscriptionIsNullDirectly() {
        // Direct unit test of calculateTax with a null subscription invoice.
        Invoice invoice = new Invoice();
        invoice.setCustomerId("cust-006");
        invoice.setAmountCents(20000);
        // subscription remains null

        assertDoesNotThrow(() -> invoiceService.calculateTax(invoice),
                "calculateTax must not throw NPE when invoice.getSubscription() is null");

        BigDecimal result = invoiceService.calculateTax(invoice);
        assertEquals(0, result.compareTo(BigDecimal.ZERO),
                "calculateTax should return ZERO for null subscription");
    }
}