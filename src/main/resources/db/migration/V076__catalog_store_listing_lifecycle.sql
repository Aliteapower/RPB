alter table catalog_product_stores add column enabled boolean not null default true;
alter table catalog_product_stores add column version int not null default 0 check(version>=0);
