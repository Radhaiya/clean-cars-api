--liquibase formatted sql

--changeset liquibase:008-subscription-last-webhook-event-at logicalFilePath:migrations/008-subscription-last-webhook-event-at.sql
-- Guards against out-of-order Razorpay webhook delivery (at-least-once, no
-- ordering guarantee): the timestamp of the last webhook event whose payload
-- actually moved this row's status. A redelivered/delayed event older than
-- this is a no-op instead of regressing the row (e.g. a late `cancelled`
-- retry landing after a newer `activated` already flipped it ACTIVE).
ALTER TABLE subscriptions ADD COLUMN last_webhook_event_at TIMESTAMP NULL;
--rollback ALTER TABLE subscriptions DROP COLUMN last_webhook_event_at;
