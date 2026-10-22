-- createPayment used to ask Razorpay for a fresh intent on every call, so a shopper who opened
-- the checkout sheet twice left two live intents behind while only the newest was remembered.
-- Paying the older one then settled nothing at all: the signature is checked against the stored
-- intent, and the webhook looks the order up by the id it names, so the capture lands on an
-- order we can no longer resolve -- money taken, order stuck.
--
-- Recording the amount the stored intent was priced at lets a repeat request recognise "same
-- order, same price" and hand back the intent it already issued instead of replacing it.
ALTER TABLE orders
    ADD COLUMN payment_amount_paise BIGINT NULL;
