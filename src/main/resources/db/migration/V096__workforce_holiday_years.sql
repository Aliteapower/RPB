create table workforce_holiday_years (
 tenant_id uuid not null references tenants(id),year integer not null check(year between 2000 and 2100),
 version bigint not null check(version>0),created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,year)
);
create table workforce_public_holidays (
 id uuid primary key,tenant_id uuid not null,year integer not null,holiday_date date not null,
 name text not null check(length(trim(name)) between 1 and 100),english_name text not null default '' check(length(english_name)<=100),
 observed_on date,observed_kind text not null check(observed_kind in ('none','official','agreed')),enabled boolean not null,tentative boolean not null default false,
 version bigint not null check(version>0),deleted_at timestamptz,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 foreign key(tenant_id,year) references workforce_holiday_years(tenant_id,year),
 check(extract(year from holiday_date)=year),
 check((observed_on is null and observed_kind='none') or (observed_on>holiday_date and observed_on<=holiday_date+365 and observed_kind<>'none'))
);
create index ix_workforce_public_holiday_year on workforce_public_holidays(tenant_id,year) where deleted_at is null;
create index ix_workforce_public_holiday_observed on workforce_public_holidays(tenant_id,observed_on) where deleted_at is null and observed_on is not null;
