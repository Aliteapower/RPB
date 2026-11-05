-- Existing cards start on this migration's local business day: no historical deduction.
alter table customer_cards add column daily_expiry_from date;
alter table customer_cards add column daily_expiry_next_day date;
update customer_cards set daily_expiry_from=greatest((starts_at at time zone timezone)::date,(current_timestamp at time zone timezone)::date);
update customer_cards set daily_expiry_next_day=daily_expiry_from;
alter table customer_cards alter column daily_expiry_from set not null;
alter table customer_cards alter column daily_expiry_next_day set not null;
alter table customer_cards add constraint ck_card_daily_expiry_cursor check(daily_expiry_next_day>=daily_expiry_from);
alter table card_entries drop constraint card_entries_kind_check;
alter table card_entries add constraint card_entries_kind_check check(kind in('grant','stamp','redeem','reverse','refund','stamp_reverse','reward_revoke','daily_expire'));
create index ix_card_daily_expiry on customer_cards(tenant_id,store_id,daily_expiry_next_day,id)
 where kind in('count','period') and status='active' and remaining>0 and daily_limit is not null;

-- Older application binaries can still issue cards after this additive migration.
-- The per-card zone also makes direct imports start prospectively rather than back-charge history.
create or replace function card_daily_expiry_defaults() returns trigger language plpgsql as $$
begin
 if new.daily_expiry_from is null then
  new.daily_expiry_from := greatest((new.starts_at at time zone new.timezone)::date,(current_timestamp at time zone new.timezone)::date);
 end if;
 if new.daily_expiry_next_day is null then new.daily_expiry_next_day := new.daily_expiry_from; end if;
 return new;
end;
$$;
create trigger card_daily_expiry_defaults before insert on customer_cards for each row execute function card_daily_expiry_defaults();
