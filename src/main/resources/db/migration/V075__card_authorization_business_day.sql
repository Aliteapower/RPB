alter table card_requests add column authorization_day date;
alter table card_requests add constraint ck_card_authorization_day check(authorization_hash is null or authorization_day is not null);

alter table card_entries add column entry_sequence bigint generated always as identity;
alter table card_entries add column stamp_reward_round int check(stamp_reward_round>0);
create index ix_card_entry_sequence on card_entries(tenant_id,card_id,entry_sequence desc);
