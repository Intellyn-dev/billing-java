package com.intellyn.billing.repository;

import com.intellyn.billing.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findByCustomerIdAndActiveTrue(String customerId);
}
