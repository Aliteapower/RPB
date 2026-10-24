-- Configurable offline collection subjects; preserve legacy codes and all original business values.
alter table wallet_programs add column channel_labels jsonb not null default '{}'::jsonb check(jsonb_typeof(channel_labels)='object');
alter table wallet_collections add column channel_name text null;

create function wallet_collection_channel_valid(value text) returns boolean language sql immutable strict as $$
 select value ~ '^(cash|paynow|credit_card|other)(:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$'
$$;
create function wallet_collection_channels_valid(value jsonb) returns boolean language sql immutable strict as $$
 select case when jsonb_typeof(value) <> 'array' then false else
 jsonb_array_length(value) between 1 and 30
 and not exists(select 1 from jsonb_array_elements(value) v where jsonb_typeof(v)<>'string' or not wallet_collection_channel_valid(v#>>'{}'))
 and (select count(distinct v) from jsonb_array_elements(value) v)=jsonb_array_length(value) end
$$;
do $$declare item record;begin
 for item in select conname,conrelid::regclass as tbl from pg_constraint where contype='c' and conrelid in ('wallet_programs'::regclass,'wallet_topups'::regclass,'wallet_collections'::regclass,'wallet_topup_quotes'::regclass) and pg_get_constraintdef(oid) like '%channel%' and pg_get_constraintdef(oid) not like '%channel_labels%' loop
 execute format('alter table %s drop constraint %I',item.tbl,item.conname);
 end loop;
end$$;
alter table wallet_programs add constraint ck_wallet_program_channels check(wallet_collection_channels_valid(channels));
alter table wallet_topups add constraint ck_wallet_topup_channel check(wallet_collection_channel_valid(channel));
alter table wallet_collections add constraint ck_wallet_collection_channel check(wallet_collection_channel_valid(channel));
alter table wallet_topup_quotes add constraint ck_wallet_quote_channel check(wallet_collection_channel_valid(channel));
-- Subject aliases must not bypass duplicate receipt protection for the same underlying settlement type.
create unique index ux_wallet_collection_settlement_receipt on wallet_collections(tenant_id,split_part(channel,':',1),collection_account,receipt_reference);
