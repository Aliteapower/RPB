-- 0 months explicitly denotes unlimited storage. Historical rules/items remain unchanged.
alter table wine_storage_rules drop constraint wine_storage_rules_months_check;
alter table wine_storage_rules add constraint wine_storage_rules_months_check check(months between 0 and 120);
alter table wine_storage_items alter column expires_at drop not null;
