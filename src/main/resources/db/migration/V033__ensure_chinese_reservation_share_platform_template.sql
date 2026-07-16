with chinese_default(template_text) as (
    values ($template$尊敬的 {{contactName}} {{guestSalutation}}，

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
$template$)
)
insert into i18n_message_catalog (i18n_key, locale, message, status)
select 'reservation.share.restaurant_reservation_confirmation_v1', 'zh-CN', chinese_default.template_text, 'active'
from chinese_default
where not exists (
    select 1
    from i18n_message_catalog existing
    where existing.tenant_id is null
      and existing.store_id is null
      and existing.i18n_key = 'reservation.share.restaurant_reservation_confirmation_v1'
      and existing.locale = 'zh-CN'
      and existing.deleted_at is null
);
