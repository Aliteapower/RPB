alter table tenant_product_subscription_events
    drop constraint if exists ck_tenant_product_subscription_events_type;

alter table tenant_product_subscription_events
    add constraint ck_tenant_product_subscription_events_type check (
        event_type in (
            'purchase',
            'renew',
            'renew_item',
            'suspend',
            'cancel',
            'convert_from_legacy',
            'manual_adjust'
        )
    );
