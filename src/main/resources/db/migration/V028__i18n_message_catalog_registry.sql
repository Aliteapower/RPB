alter table i18n_message_catalog
    add column if not exists version integer not null default 0;

create table if not exists i18n_message_key_registry (
    i18n_key text primary key,
    message_namespace text not null,
    category text not null,
    display_name text not null,
    description text null,
    text_kind text not null,
    tenant_editable boolean not null default true,
    placeholder_names text[] not null default '{}'::text[],
    status text not null default 'active',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_i18n_message_key_registry_kind check (text_kind in ('label', 'template', 'status', 'prompt')),
    constraint ck_i18n_message_key_registry_status check (status in ('active', 'inactive'))
);

create index if not exists ix_i18n_message_key_registry_active
    on i18n_message_key_registry (status, tenant_editable, message_namespace, category, sort_order);

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
        ('reason.cancellation.customer_request', 'reason', 'cancellation', 'Customer cancellation reason', 'Customer-facing cancellation reason label.', 'label', true, '{}'::text[], 10),
        ('reason.cancellation.store_closed', 'reason', 'cancellation', 'Store closed cancellation reason', 'Customer-facing cancellation reason when store cannot serve.', 'label', true, '{}'::text[], 20),
        ('reason.no_show.customer_unreachable', 'reason', 'no_show', 'No-show customer unreachable', 'No-show reason label for customer unreachable.', 'label', true, '{}'::text[], 30),
        ('reason.queue.skip.no_response', 'reason', 'queue', 'Queue skipped no response', 'Queue skipped reason when guest does not respond.', 'label', true, '{}'::text[], 40),
        ('reason.table.override.vip_guest', 'reason', 'table', 'VIP table override', 'Manual table override reason for VIP service.', 'label', true, '{}'::text[], 50),
        ('reason.cleaning.default', 'reason', 'cleaning', 'Default cleaning reason', 'Default cleaning flow reason label.', 'label', true, '{}'::text[], 60),
        ('status.reservation.confirmed', 'status', 'reservation', 'Reservation confirmed status', 'Configurable display label for confirmed reservations.', 'status', false, '{}'::text[], 70),
        ('status.reservation.arrived', 'status', 'reservation', 'Reservation arrived status', 'Configurable display label for arrived reservations.', 'status', false, '{}'::text[], 80),
        ('status.queue.waiting', 'status', 'queue', 'Queue waiting status', 'Configurable display label for waiting queue tickets.', 'status', false, '{}'::text[], 90),
        ('status.queue.called', 'status', 'queue', 'Queue called status', 'Configurable display label for called queue tickets.', 'status', false, '{}'::text[], 100),
        ('public_booking.prompt.arrival_note', 'public_booking', 'prompt', 'Public booking arrival note', 'Customer-facing arrival note on public booking pages.', 'prompt', true, '{}'::text[], 110),
        ('public_booking.prompt.policy', 'public_booking', 'prompt', 'Public booking policy', 'Customer-facing public booking policy prompt.', 'prompt', true, array['holdMinutes']::text[], 120),
        ('reservation.share.arrival_note', 'reservation_share', 'prompt', 'Reservation share arrival note', 'Customer-facing arrival note rendered inside reservation share templates.', 'prompt', true, '{}'::text[], 130),
        ('reservation.share.whatsapp_template', 'reservation_share', 'template', 'WhatsApp reservation share template', 'Customer-facing WhatsApp reservation confirmation template.', 'template', true, array['storeName','reservationNo','reservationDate','reservationTime','partySize','tableCode','holdMinutes','contactName','guestSalutation','maskedPhone','storeAddress','googleMapUrl','storePhone','arrivalNote','confirmInstruction','cancelInstruction','changeInstruction','replyInstruction','reservationCode','reservedStartAt']::text[], 140),
        ('reservation.share.wechat_template', 'reservation_share', 'template', 'WeChat reservation share template', 'Customer-facing WeChat reservation confirmation template.', 'template', true, array['storeName','reservationNo','reservationDate','reservationTime','partySize','tableCode','holdMinutes','contactName','guestSalutation','maskedPhone','storeAddress','googleMapUrl','storePhone','arrivalNote','confirmInstruction','cancelInstruction','changeInstruction','replyInstruction','reservationCode','reservedStartAt']::text[], 150),
        ('reservation.share.restaurant_reservation_confirmation_v1', 'reservation_share', 'template', 'Restaurant reservation confirmation template V1', 'Platform seed template migrated from platform_reservation_share_template_seeds.', 'template', true, array['storeName','reservationNo','reservationDate','reservationTime','partySize','tableCode','holdMinutes','contactName','guestSalutation','maskedPhone','storeAddress','googleMapUrl','storePhone','arrivalNote','confirmInstruction','cancelInstruction','changeInstruction','replyInstruction','reservationCode','reservedStartAt']::text[], 160),
        ('queue.ticket.customer_notice', 'queue', 'template', 'Queue ticket customer notice', 'Customer-facing queue ticket notice.', 'template', true, array['storeName','queueNo','partySize','waitGroups']::text[], 170),
        ('call_screen.welcome.title', 'call_screen', 'display', 'Call screen welcome title', 'Call screen welcome title text.', 'prompt', true, '{}'::text[], 180),
        ('call_screen.welcome.subtitle', 'call_screen', 'display', 'Call screen welcome subtitle', 'Call screen welcome subtitle text.', 'prompt', true, '{}'::text[], 190),
        ('call_screen.queue.notice', 'call_screen', 'display', 'Call screen queue notice', 'Call screen queue notice text.', 'prompt', true, array['queueNo']::text[], 200)
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

with migrated_key_source (
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
) as (
    select
        'reservation.share.' || seed.seed_key,
        'reservation_share',
        'template',
        seed.display_name,
        'Reservation share template migrated from platform_reservation_share_template_seeds.',
        'template',
        true,
        array['storeName','reservationNo','reservationDate','reservationTime','partySize','tableCode','holdMinutes','contactName','guestSalutation','maskedPhone','storeAddress','googleMapUrl','storePhone','arrivalNote','confirmInstruction','cancelInstruction','changeInstruction','replyInstruction','reservationCode','reservedStartAt']::text[],
        case when seed.status = 'active' then 'active' else 'inactive' end,
        1000 + row_number() over (order by seed.seed_key)
    from platform_reservation_share_template_seeds seed
    where seed.deleted_at is null

    union all

    select
        'reservation.meal_period.' || seed.period_key || '.display_name',
        'reservation_meal_period',
        'display_name',
        seed.display_name || ' display name',
        'Meal period display name migrated from platform_reservation_meal_period_seeds.',
        'label',
        true,
        '{}'::text[],
        case when seed.status = 'active' then 'active' else 'inactive' end,
        2000 + seed.sort_order
    from platform_reservation_meal_period_seeds seed
    where seed.deleted_at is null

    union all

    select
        'reservation.meal_period.' || period.period_key || '.display_name',
        'reservation_meal_period',
        'display_name',
        period.display_name || ' display name',
        'Meal period display name migrated from store_reservation_meal_periods.',
        'label',
        true,
        '{}'::text[],
        case when period.status = 'active' then 'active' else 'inactive' end,
        2200 + row_number() over (order by period.period_key)
    from store_reservation_meal_periods period
    where period.deleted_at is null

    union all

    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.title',
        'call_screen',
        seed.seed_key,
        seed.display_name || ' slide ' || slide.sort_order || ' title',
        'Call screen text slide title migrated from platform_call_screen_ad_seed_slides.',
        'prompt',
        true,
        '{}'::text[],
        case when slide.status = 'active' then 'active' else 'inactive' end,
        3000 + (slide.sort_order * 10) + 1
    from platform_call_screen_ad_seed_sets seed
    join platform_call_screen_ad_seed_slides slide on slide.seed_set_id = seed.id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.subtitle',
        'call_screen',
        seed.seed_key,
        seed.display_name || ' slide ' || slide.sort_order || ' subtitle',
        'Call screen text slide subtitle migrated from platform_call_screen_ad_seed_slides.',
        'prompt',
        true,
        '{}'::text[],
        case when slide.status = 'active' then 'active' else 'inactive' end,
        3000 + (slide.sort_order * 10) + 2
    from platform_call_screen_ad_seed_sets seed
    join platform_call_screen_ad_seed_slides slide on slide.seed_set_id = seed.id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.tagline',
        'call_screen',
        seed.seed_key,
        seed.display_name || ' slide ' || slide.sort_order || ' tagline',
        'Call screen text slide tagline migrated from platform_call_screen_ad_seed_slides.',
        'prompt',
        true,
        '{}'::text[],
        case when slide.status = 'active' then 'active' else 'inactive' end,
        3000 + (slide.sort_order * 10) + 3
    from platform_call_screen_ad_seed_sets seed
    join platform_call_screen_ad_seed_slides slide on slide.seed_set_id = seed.id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null
),
migrated_keys as (
    select distinct on (i18n_key)
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
    from migrated_key_source
    order by i18n_key, sort_order
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
    status,
    sort_order
from migrated_keys
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
        ('reason.cancellation.customer_request', 'zh-CN', '顾客申请取消'),
        ('reason.cancellation.customer_request', 'en-SG', 'Customer requested cancellation'),
        ('reason.cancellation.store_closed', 'zh-CN', '门店临时闭店'),
        ('reason.cancellation.store_closed', 'en-SG', 'Store closed temporarily'),
        ('reason.no_show.customer_unreachable', 'zh-CN', '联系不上顾客'),
        ('reason.no_show.customer_unreachable', 'en-SG', 'Customer unreachable'),
        ('reason.queue.skip.no_response', 'zh-CN', '叫号无回应'),
        ('reason.queue.skip.no_response', 'en-SG', 'No response after call'),
        ('reason.table.override.vip_guest', 'zh-CN', '贵宾安排'),
        ('reason.table.override.vip_guest', 'en-SG', 'VIP arrangement'),
        ('reason.cleaning.default', 'zh-CN', '常规清台'),
        ('reason.cleaning.default', 'en-SG', 'Standard cleaning'),
        ('status.reservation.confirmed', 'zh-CN', '已预约'),
        ('status.reservation.confirmed', 'en-SG', 'Booked'),
        ('status.reservation.arrived', 'zh-CN', '已到店'),
        ('status.reservation.arrived', 'en-SG', 'Arrived'),
        ('status.queue.waiting', 'zh-CN', '等待中'),
        ('status.queue.waiting', 'en-SG', 'Waiting'),
        ('status.queue.called', 'zh-CN', '已叫号'),
        ('status.queue.called', 'en-SG', 'Called'),
        ('public_booking.prompt.arrival_note', 'zh-CN', '请按预约时间到店，如需调整请提前联系门店。'),
        ('public_booking.prompt.arrival_note', 'en-SG', 'Please arrive at your reserved time. Contact the store ahead if you need changes.'),
        ('public_booking.prompt.policy', 'zh-CN', '订位将保留 {{holdMinutes}} 分钟，超时可能释放座位。'),
        ('public_booking.prompt.policy', 'en-SG', 'Bookings are held for {{holdMinutes}} minutes. Seats may be released after that.'),
        ('reservation.share.arrival_note', 'zh-CN', '请按预约时间到店，如需调整请提前联系门店。'),
        ('reservation.share.arrival_note', 'en-SG', 'Please arrive at your reserved time. Contact the store ahead if you need changes.'),
        ('reservation.share.whatsapp_template', 'zh-CN', '您好 {{contactName}}{{guestSalutation}}，{{storeName}} 已确认您的订位：{{reservationDate}} {{reservationTime}}，{{partySize}} 位，桌台 {{tableCode}}。订位号 {{reservationNo}}，保留 {{holdMinutes}} 分钟。{{arrivalNote}}'),
        ('reservation.share.whatsapp_template', 'en-SG', 'Hi {{contactName}} {{guestSalutation}}, {{storeName}} confirmed your booking for {{reservationDate}} {{reservationTime}}, {{partySize}} pax, table {{tableCode}}. Booking {{reservationNo}} is held for {{holdMinutes}} minutes. {{arrivalNote}}'),
        ('reservation.share.wechat_template', 'zh-CN', '{{storeName}} 订位确认：{{reservationDate}} {{reservationTime}}，{{partySize}} 位，桌台 {{tableCode}}，订位号 {{reservationNo}}。{{arrivalNote}}'),
        ('reservation.share.wechat_template', 'en-SG', '{{storeName}} booking confirmation: {{reservationDate}} {{reservationTime}}, {{partySize}} pax, table {{tableCode}}, booking {{reservationNo}}. {{arrivalNote}}'),
        ('reservation.share.restaurant_reservation_confirmation_v1', 'en-SG', 'Dear {{contactName}} {{guestSalutation}},\n\nThank you for choosing {{storeName}}. Your booking is confirmed:\n\nBooking no.: {{reservationNo}}\nDate: {{reservationDate}}\nTime: {{reservationTime}}\nParty size: {{partySize}} pax\nTable: {{tableCode}}\n\nArrival note:\n{{arrivalNote}}\n\nWe will hold your table for {{holdMinutes}} minutes. Seats may be released after that.\n\nGoogle Map:\n{{googleMapUrl}}\n\nTo change or cancel, please contact {{storePhone}} at least 2 hours ahead.\n\nWe look forward to serving you.\n{{storeName}}\n{{storePhone}} | {{storeAddress}}'),
        ('reservation.meal_period.lunch.display_name', 'en-SG', 'Lunch'),
        ('reservation.meal_period.dinner.display_name', 'en-SG', 'Dinner'),
        ('queue.ticket.customer_notice', 'zh-CN', '{{storeName}} 当前排队号 {{queueNo}}，{{partySize}} 位，前方约 {{waitGroups}} 组。'),
        ('queue.ticket.customer_notice', 'en-SG', '{{storeName}} queue number {{queueNo}}, {{partySize}} pax, around {{waitGroups}} groups ahead.'),
        ('call_screen.welcome.title', 'zh-CN', '欢迎光临'),
        ('call_screen.welcome.title', 'en-SG', 'Welcome'),
        ('call_screen.welcome.subtitle', 'zh-CN', '请留意叫号屏幕与现场广播'),
        ('call_screen.welcome.subtitle', 'en-SG', 'Please watch the call screen and listen for announcements'),
        ('call_screen.queue.notice', 'zh-CN', '请 {{queueNo}} 号顾客准备入座'),
        ('call_screen.queue.notice', 'en-SG', 'Queue {{queueNo}}, please get ready to be seated'),
        ('call_screen.seed.restaurant_default.slide_1.title', 'en-SG', 'Welcome'),
        ('call_screen.seed.restaurant_default.slide_1.subtitle', 'en-SG', 'Shike Restaurant'),
        ('call_screen.seed.restaurant_default.slide_1.tagline', 'en-SG', 'Fresh ingredients · Crafted cooking · Attentive service'),
        ('call_screen.seed.restaurant_default.slide_2.title', 'en-SG', 'Today''s recommendation'),
        ('call_screen.seed.restaurant_default.slide_2.subtitle', 'en-SG', 'Signature charcoal-grilled steak'),
        ('call_screen.seed.restaurant_default.slide_2.tagline', 'en-SG', 'Selected Australian grain-fed beef · Grilled to order'),
        ('call_screen.seed.restaurant_default.slide_3.title', 'en-SG', 'Special offer'),
        ('call_screen.seed.restaurant_default.slide_3.subtitle', 'en-SG', '20% off weekday lunch'),
        ('call_screen.seed.restaurant_default.slide_3.tagline', 'en-SG', 'Monday to Friday 11:00-14:00'),
        ('call_screen.seed.restaurant_default.slide_4.title', 'en-SG', 'Member exclusive'),
        ('call_screen.seed.restaurant_default.slide_4.subtitle', 'en-SG', 'Top-up bonus'),
        ('call_screen.seed.restaurant_default.slide_4.tagline', 'en-SG', 'Top up 500 get 50 · Top up 1000 get 120')
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

insert into i18n_message_catalog (i18n_key, locale, message, status)
select
    'reservation.share.' || seed.seed_key,
    case when seed.locale in ('zh-CN', 'en-SG') then seed.locale else 'zh-CN' end,
    seed.template_text,
    case when seed.status = 'active' then 'active' else 'inactive' end
from platform_reservation_share_template_seeds seed
where seed.deleted_at is null
  and btrim(seed.template_text) <> ''
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id is null
        and existing.store_id is null
        and existing.i18n_key = 'reservation.share.' || seed.seed_key
        and existing.locale = case when seed.locale in ('zh-CN', 'en-SG') then seed.locale else 'zh-CN' end
        and existing.deleted_at is null
  );

insert into i18n_message_catalog (tenant_id, store_id, i18n_key, locale, message, status)
select
    store.tenant_id,
    store.id,
    'reservation.share.restaurant_reservation_confirmation_v1',
    case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end,
    store.reservation_share_template,
    'active'
from stores store
left join platform_reservation_share_template_seeds seed
  on seed.seed_key = 'restaurant_reservation_confirmation_v1'
 and seed.deleted_at is null
where store.deleted_at is null
  and store.reservation_share_template is not null
  and btrim(store.reservation_share_template) <> ''
  and (seed.template_text is null or store.reservation_share_template is distinct from seed.template_text)
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id = store.tenant_id
        and existing.store_id = store.id
        and existing.i18n_key = 'reservation.share.restaurant_reservation_confirmation_v1'
        and existing.locale = case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end
        and existing.deleted_at is null
  );

insert into i18n_message_catalog (tenant_id, store_id, i18n_key, locale, message, status)
select
    store.tenant_id,
    store.id,
    'reservation.share.arrival_note',
    case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end,
    store.reservation_share_note,
    'active'
from stores store
where store.deleted_at is null
  and store.reservation_share_note is not null
  and btrim(store.reservation_share_note) <> ''
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id = store.tenant_id
        and existing.store_id = store.id
        and existing.i18n_key = 'reservation.share.arrival_note'
        and existing.locale = case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end
        and existing.deleted_at is null
  );

insert into i18n_message_catalog (i18n_key, locale, message, status)
select
    'reservation.meal_period.' || seed.period_key || '.display_name',
    'zh-CN',
    seed.display_name,
    case when seed.status = 'active' then 'active' else 'inactive' end
from platform_reservation_meal_period_seeds seed
where seed.deleted_at is null
  and btrim(seed.display_name) <> ''
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id is null
        and existing.store_id is null
        and existing.i18n_key = 'reservation.meal_period.' || seed.period_key || '.display_name'
        and existing.locale = 'zh-CN'
        and existing.deleted_at is null
  );

insert into i18n_message_catalog (tenant_id, store_id, i18n_key, locale, message, status)
select
    period.tenant_id,
    period.store_id,
    'reservation.meal_period.' || period.period_key || '.display_name',
    case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end,
    period.display_name,
    case when period.status = 'active' then 'active' else 'inactive' end
from store_reservation_meal_periods period
join stores store on store.id = period.store_id
 and store.tenant_id = period.tenant_id
left join platform_reservation_meal_period_seeds seed on seed.id = period.source_seed_id
 and seed.deleted_at is null
where period.deleted_at is null
  and store.deleted_at is null
  and btrim(period.display_name) <> ''
  and (seed.display_name is null or period.display_name is distinct from seed.display_name)
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id = period.tenant_id
        and existing.store_id = period.store_id
        and existing.i18n_key = 'reservation.meal_period.' || period.period_key || '.display_name'
        and existing.locale = case when store.locale in ('zh-CN', 'en-SG') then store.locale else 'zh-CN' end
        and existing.deleted_at is null
  );

with call_screen_platform_messages (i18n_key, message, status) as (
    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.title',
        slide.title,
        case when slide.status = 'active' then 'active' else 'inactive' end
    from platform_call_screen_ad_seed_slides slide
    join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.subtitle',
        slide.subtitle,
        case when slide.status = 'active' then 'active' else 'inactive' end
    from platform_call_screen_ad_seed_slides slide
    join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        'call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.tagline',
        slide.tagline,
        case when slide.status = 'active' then 'active' else 'inactive' end
    from platform_call_screen_ad_seed_slides slide
    join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
    where seed.ad_type = 'text'
      and seed.deleted_at is null
      and slide.deleted_at is null
)
insert into i18n_message_catalog (i18n_key, locale, message, status)
select i18n_key, 'zh-CN', message, status
from call_screen_platform_messages migrated
where btrim(migrated.message) <> ''
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id is null
        and existing.store_id is null
        and existing.i18n_key = migrated.i18n_key
        and existing.locale = 'zh-CN'
        and existing.deleted_at is null
  );

with call_screen_tenant_messages (tenant_id, locale, i18n_key, message, status, platform_message) as (
    select
        slide.tenant_id,
        case when tenant.default_locale in ('zh-CN', 'en-SG') then tenant.default_locale else 'zh-CN' end,
        'call_screen.seed.' || seed.seed_key || '.slide_' || platform_slide.sort_order || '.title',
        slide.title,
        case when slide.status = 'active' then 'active' else 'inactive' end,
        platform_slide.title
    from tenant_call_screen_text_slides slide
    join tenants tenant on tenant.id = slide.tenant_id
    join platform_call_screen_ad_seed_slides platform_slide on platform_slide.id = slide.source_seed_slide_id
    join platform_call_screen_ad_seed_sets seed on seed.id = platform_slide.seed_set_id
    where seed.ad_type = 'text'
      and tenant.deleted_at is null
      and seed.deleted_at is null
      and platform_slide.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        slide.tenant_id,
        case when tenant.default_locale in ('zh-CN', 'en-SG') then tenant.default_locale else 'zh-CN' end,
        'call_screen.seed.' || seed.seed_key || '.slide_' || platform_slide.sort_order || '.subtitle',
        slide.subtitle,
        case when slide.status = 'active' then 'active' else 'inactive' end,
        platform_slide.subtitle
    from tenant_call_screen_text_slides slide
    join tenants tenant on tenant.id = slide.tenant_id
    join platform_call_screen_ad_seed_slides platform_slide on platform_slide.id = slide.source_seed_slide_id
    join platform_call_screen_ad_seed_sets seed on seed.id = platform_slide.seed_set_id
    where seed.ad_type = 'text'
      and tenant.deleted_at is null
      and seed.deleted_at is null
      and platform_slide.deleted_at is null
      and slide.deleted_at is null

    union all

    select
        slide.tenant_id,
        case when tenant.default_locale in ('zh-CN', 'en-SG') then tenant.default_locale else 'zh-CN' end,
        'call_screen.seed.' || seed.seed_key || '.slide_' || platform_slide.sort_order || '.tagline',
        slide.tagline,
        case when slide.status = 'active' then 'active' else 'inactive' end,
        platform_slide.tagline
    from tenant_call_screen_text_slides slide
    join tenants tenant on tenant.id = slide.tenant_id
    join platform_call_screen_ad_seed_slides platform_slide on platform_slide.id = slide.source_seed_slide_id
    join platform_call_screen_ad_seed_sets seed on seed.id = platform_slide.seed_set_id
    where seed.ad_type = 'text'
      and tenant.deleted_at is null
      and seed.deleted_at is null
      and platform_slide.deleted_at is null
      and slide.deleted_at is null
)
insert into i18n_message_catalog (tenant_id, store_id, i18n_key, locale, message, status)
select tenant_id, null, i18n_key, locale, message, status
from call_screen_tenant_messages migrated
where btrim(migrated.message) <> ''
  and migrated.message is distinct from migrated.platform_message
  and not exists (
      select 1
      from i18n_message_catalog existing
      where existing.tenant_id = migrated.tenant_id
        and existing.store_id is null
        and existing.i18n_key = migrated.i18n_key
        and existing.locale = migrated.locale
        and existing.deleted_at is null
  );
