-- Keep the scanned/entered identifier exactly as supplied; barcode remains the internal matching key.
alter table catalog_product_barcodes add column raw_barcode varchar(14);
alter table catalog_product_barcodes add constraint ck_catalog_raw_barcode check (
 raw_barcode is null or (raw_barcode ~ '^([0-9]{8}|[0-9]{12,14})$' and lpad(raw_barcode,14,'0')=barcode)
);
