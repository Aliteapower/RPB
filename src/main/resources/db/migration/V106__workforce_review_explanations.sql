create table workforce_review_explanations (
 tenant_id uuid not null references tenants(id), id uuid not null,
 code text not null check(code in ('GENERAL','LATE','EARLY','BREAK_SHORT','BREAK_OVER','MISSING')),
 content text not null check(length(content) between 1 and 500),
 enabled boolean not null, version bigint not null check(version>0),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 primary key(tenant_id,id), unique(tenant_id,code,content)
);
alter table workforce_approvals drop constraint workforce_approvals_note_check;
alter table workforce_approvals add constraint workforce_approvals_note_check check(length(note)<=500);
