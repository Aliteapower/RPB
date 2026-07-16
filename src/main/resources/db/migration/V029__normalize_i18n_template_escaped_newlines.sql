with normalized as (
    select
        catalog.id,
        replace(
            replace(
                replace(catalog.message, chr(92) || 'r' || chr(92) || 'n', chr(10)),
                chr(92) || 'n',
                chr(10)
            ),
            chr(92) || 'r',
            chr(10)
        ) as normalized_message
    from i18n_message_catalog catalog
    join i18n_message_key_registry registry
      on registry.i18n_key = catalog.i18n_key
    where registry.text_kind = 'template'
      and catalog.deleted_at is null
      and catalog.message is not null
      and (
          catalog.message like '%' || chr(92) || 'n' || '%'
          or catalog.message like '%' || chr(92) || 'r' || '%'
      )
)
update i18n_message_catalog catalog
set message = normalized.normalized_message,
    updated_at = now(),
    version = catalog.version + 1
from normalized
where normalized.id = catalog.id
  and catalog.message is distinct from normalized.normalized_message;
