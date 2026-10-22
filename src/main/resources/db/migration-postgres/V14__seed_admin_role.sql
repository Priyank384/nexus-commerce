-- Dev-only seed: promote a known email to ROLE_ADMIN so the admin surface
-- (users list, metrics, product image upload, order delete, category writes,
-- review moderation) can be exercised without hand-editing rows in a database
-- session. If the account does not exist in this environment the UPDATE is a
-- no-op; register it via POST /api/v1/users first.
--
-- Rotate or delete this row before deploying anywhere real.
UPDATE users
   SET role = 'ADMIN'
 WHERE email = 'root-admin@nexuscommerce.test';
