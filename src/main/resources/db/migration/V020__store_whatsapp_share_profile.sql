alter table stores
    add column if not exists whatsapp_business_phone_e164 text null;

alter table stores
    drop constraint if exists ck_stores_whatsapp_business_phone_e164,
    add constraint ck_stores_whatsapp_business_phone_e164
        check (
            whatsapp_business_phone_e164 is null
            or whatsapp_business_phone_e164 ~ '^[+][1-9][0-9]{1,14}$'
        );
