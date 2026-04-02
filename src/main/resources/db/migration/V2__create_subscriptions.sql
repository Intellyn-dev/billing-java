CREATE TABLE subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE invoices ADD CONSTRAINT fk_invoice_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id);

CREATE INDEX idx_subscriptions_customer ON subscriptions(customer_id);
