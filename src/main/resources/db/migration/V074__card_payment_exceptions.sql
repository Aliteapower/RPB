-- Verified money received against an expired/withdrawn quote is retained for manager refund.
alter table card_purchase_orders drop constraint card_purchase_orders_status_check;
alter table card_purchase_orders add constraint card_purchase_orders_status_check check(status in('pending','issued','refunded','cancelled','exception','exception_refunded'));
