create table if not exists platform_reservation_share_template_seeds (
    id uuid primary key,
    seed_key text not null,
    display_name text not null,
    locale text not null default 'zh-CN',
    template_text text not null,
    status text not null default 'active',
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    deleted_at timestamp with time zone null,
    version integer not null default 0,
    constraint uq_platform_reservation_share_template_seeds_key unique (seed_key),
    constraint ck_platform_reservation_share_template_seeds_status check (status in ('active', 'disabled'))
);

create index if not exists ix_platform_reservation_share_template_seeds_active
    on platform_reservation_share_template_seeds (status, seed_key)
    where deleted_at is null;

insert into platform_reservation_share_template_seeds (
    id,
    seed_key,
    display_name,
    locale,
    template_text,
    status
)
values (
    '8e3333d1-9a73-466e-92ed-7f5d34db9b71',
    'restaurant_reservation_confirmation_v1',
    '餐厅预约确认模板 V1',
    'zh-CN',
    $template$尊敬的 {{contactName}} {{guestSalutation}}，

感谢您选择 {{storeName}}。我们很荣幸地确认您的预订安排，具体信息如下：

预订编号：{{reservationNo}}

日期：{{reservationDate}}

时间：{{reservationTime}}

人数：{{partySize}}位成人

桌位：{{tableCode}} (已预留)

温馨提示：
{{arrivalNote}}

预留时间：为保证所有宾客的用餐体验，我们将为您保留座位 {{holdMinutes}}分钟。若超过保留时间，座位可能被取消，敬请谅解。

Google Map：
{{googleMapUrl}}

如需修改或取消，请至少提前 2 小时致电 {{storePhone}} 联系我们。

期待为您奉上一场味蕾盛宴！

顺颂时祺，
{{storeName}} 预订部
{{storePhone}} | {{storeAddress}}
$template$,
    'active'
)
on conflict (seed_key) do update
set display_name = excluded.display_name,
    locale = excluded.locale,
    template_text = excluded.template_text,
    status = excluded.status,
    updated_at = now(),
    version = platform_reservation_share_template_seeds.version + 1;

update stores
set reservation_share_template = seed.template_text,
    updated_at = now(),
    version = stores.version + 1
from platform_reservation_share_template_seeds seed
where seed.seed_key = 'restaurant_reservation_confirmation_v1'
  and seed.status = 'active'
  and seed.deleted_at is null
  and stores.reservation_share_template is null
  and stores.deleted_at is null;
