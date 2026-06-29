alter table stores
    add column if not exists share_email text null;

alter table stores
    drop constraint if exists ck_stores_share_email,
    add constraint ck_stores_share_email
        check (
            share_email is null
            or (
                position('@' in share_email) > 1
                and position('.' in split_part(share_email, '@', 2)) > 1
                and share_email !~ '\s'
            )
        );
