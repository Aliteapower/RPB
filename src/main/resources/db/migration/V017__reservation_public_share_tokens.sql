create table if not exists reservation_public_share_tokens (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    store_id uuid not null,
    reservation_id uuid not null,
    token text not null,
    status text not null default 'active',
    expires_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_reservation_public_share_tokens_token unique (token),
    constraint fk_reservation_public_share_tokens_store_scope
        foreign key (store_id, tenant_id) references stores(id, tenant_id),
    constraint fk_reservation_public_share_tokens_reservation_scope
        foreign key (reservation_id, tenant_id, store_id) references reservations(id, tenant_id, store_id),
    constraint ck_reservation_public_share_tokens_status check (status in ('active', 'revoked')),
    constraint ck_reservation_public_share_tokens_token_not_blank check (length(trim(token)) > 0)
);

create unique index if not exists ux_reservation_public_share_tokens_active_reservation
    on reservation_public_share_tokens (tenant_id, store_id, reservation_id)
    where status = 'active';

create index if not exists ix_reservation_public_share_tokens_reservation
    on reservation_public_share_tokens (tenant_id, store_id, reservation_id);

create index if not exists ix_reservation_public_share_tokens_lookup
    on reservation_public_share_tokens (token, status);
