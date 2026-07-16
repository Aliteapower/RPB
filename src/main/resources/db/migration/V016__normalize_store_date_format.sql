update stores
set date_format = 'DD-MM-YYYY',
    updated_at = now(),
    version = version + 1
where date_format in ('yyyy-MM-dd', 'dd-MM-yyyy');
