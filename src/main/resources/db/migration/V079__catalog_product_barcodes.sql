create table catalog_product_barcodes (
 tenant_id uuid not null, barcode varchar(14) not null check(barcode ~ '^[0-9]{14}$'), product_id uuid not null,
 created_at timestamptz not null default now(),created_by uuid not null,
 primary key(tenant_id,barcode),foreign key(product_id,tenant_id) references catalog_products(id,tenant_id)
);
create index ix_catalog_barcodes_product on catalog_product_barcodes(tenant_id,product_id);
