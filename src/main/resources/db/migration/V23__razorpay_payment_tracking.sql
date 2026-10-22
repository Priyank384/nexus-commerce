-- Razorpay needs two references per order, not one: the order we hand to their API before
-- the shopper pays, and the payment id they mint when they capture it. V20 only had
-- payment_ref, so keeping both would have meant overwriting the first with the second --
-- which is the value the signature check and the webhook lookup depend on.
--
-- All three stay NULL-able: an order created before payment wiring (every historic row) is
-- simply unpaid, and payment_status distinguishes that from a checkout the shopper abandoned.
ALTER TABLE orders
    ADD COLUMN payment_intent_id VARCHAR(120) NULL,
    ADD COLUMN paid_datetime DATETIME(6) NULL;

-- The webhook is matched on the Razorpay order id, and it arrives without a user context,
-- so this lookup has to be indexed rather than a scan of the orders table.
ALTER TABLE orders
    ADD KEY idx_orders_payment_intent (payment_intent_id);
