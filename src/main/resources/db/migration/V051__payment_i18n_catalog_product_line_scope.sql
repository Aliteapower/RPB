with key_seed (
    i18n_key,
    message_namespace,
    category,
    display_name,
    description,
    text_kind,
    tenant_editable,
    placeholder_names,
    sort_order
) as (
    values
        (
            'payment.quick_pay.customer_scan_notice',
            'payment',
            'quick_pay',
            'PayNow customer scan notice',
            'Customer-facing instruction shown with a PayNow quick-pay QR code.',
            'prompt',
            true,
            '{}'::text[],
            4100
        ),
        (
            'payment.quick_pay.receipt_note',
            'payment',
            'quick_pay',
            'PayNow receipt note',
            'Customer-facing quick-pay receipt note.',
            'template',
            true,
            array['paymentReference','amount','currency']::text[],
            4110
        ),
        (
            'payment.quick_pay.waiting_message',
            'payment',
            'quick_pay',
            'PayNow display waiting message',
            'Customer display message when no active PayNow QR is being presented.',
            'prompt',
            true,
            '{}'::text[],
            4120
        )
)
insert into i18n_message_key_registry (
    i18n_key,
    message_namespace,
    category,
    display_name,
    description,
    text_kind,
    tenant_editable,
    placeholder_names,
    status,
    sort_order
)
select
    i18n_key,
    message_namespace,
    category,
    display_name,
    description,
    text_kind,
    tenant_editable,
    placeholder_names,
    'active',
    sort_order
from key_seed
on conflict (i18n_key) do update
set message_namespace = excluded.message_namespace,
    category = excluded.category,
    display_name = excluded.display_name,
    description = excluded.description,
    text_kind = excluded.text_kind,
    tenant_editable = excluded.tenant_editable,
    placeholder_names = excluded.placeholder_names,
    status = excluded.status,
    sort_order = excluded.sort_order,
    updated_at = now();

with message_seed (i18n_key, locale, message) as (
    values
        ('payment.quick_pay.customer_scan_notice', 'zh-CN', '请扫码完成 PayNow 转账。'),
        ('payment.quick_pay.customer_scan_notice', 'en-SG', 'Please scan to complete the PayNow transfer.'),
        ('payment.quick_pay.receipt_note', 'zh-CN', '收款参考 {{paymentReference}}，金额 {{currency}} {{amount}}。'),
        ('payment.quick_pay.receipt_note', 'en-SG', 'Payment reference {{paymentReference}}, amount {{currency}} {{amount}}.'),
        ('payment.quick_pay.waiting_message', 'zh-CN', '等待新的 PayNow 收款。'),
        ('payment.quick_pay.waiting_message', 'en-SG', 'Waiting for a new PayNow payment.')
)
insert into i18n_message_catalog (i18n_key, locale, message, status)
select i18n_key, locale, message, 'active'
from message_seed seed
where not exists (
    select 1
    from i18n_message_catalog existing
    where existing.tenant_id is null
      and existing.store_id is null
      and existing.i18n_key = seed.i18n_key
      and existing.locale = seed.locale
      and existing.deleted_at is null
);
