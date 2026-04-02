package com.intellyn.billing.service;

import com.intellyn.billing.model.PlanType;
import com.intellyn.billing.model.Subscription;
import com.intellyn.billing.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public void activatePlan(String customerId) {
        subscriptionRepository.findByCustomerIdAndActiveTrue(customerId).ifPresent(sub -> {
            sub.setActive(true);
            subscriptionRepository.save(sub);
        });
    }

    public Subscription createSubscription(String customerId, PlanType planType) {
        Subscription sub = new Subscription();
        sub.setCustomerId(customerId);
        sub.setPlanType(planType);
        return subscriptionRepository.save(sub);
    }
}
