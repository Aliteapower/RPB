alter table stores
    add column if not exists share_display_name text null,
    add column if not exists share_address text null,
    add column if not exists google_map_url text null,
    add column if not exists share_contact_phone text null,
    add column if not exists reservation_share_note text null,
    add column if not exists reservation_share_template text null;
