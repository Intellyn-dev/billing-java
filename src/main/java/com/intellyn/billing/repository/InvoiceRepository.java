package com.intellyn.billing.repository;

import com.intellyn.billing.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);
}
