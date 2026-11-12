with default_queue_groups (
    group_code,
    min_party_size,
    max_party_size,
    display_i18n_key,
    sort_order
) as (
    values
        ('1-2', 1, 2, 'queue.group.1_2', 1),
        ('3-4', 3, 4, 'queue.group.3_4', 2),
        ('5-6', 5, 6, 'queue.group.5_6', 3),
        ('7+', 7, null, 'queue.group.7_plus', 4)
)
insert into queue_groups (
    id,
    tenant_id,
    store_id,
    group_code,
    min_party_size,
    max_party_size,
    display_i18n_key,
    status,
    sort_order
)
select
    gen_random_uuid(),
    store.tenant_id,
    store.id,
    defaults.group_code,
    defaults.min_party_size,
    defaults.max_party_size,
    defaults.display_i18n_key,
    'active',
    defaults.sort_order
from stores store
cross join default_queue_groups defaults
where store.deleted_at is null
  and not exists (
      select 1
      from queue_groups existing
      where existing.tenant_id = store.tenant_id
        and existing.store_id = store.id
  )
on conflict (tenant_id, store_id, group_code) where deleted_at is null do nothing;
