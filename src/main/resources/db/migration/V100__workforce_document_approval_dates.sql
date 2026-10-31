alter table workforce_employee_documents add column holder_name text not null default '' check(length(holder_name)<=100);
alter table workforce_employee_documents add column approved_on date null;
alter table workforce_employee_documents add column pr_effective_on date null;
alter table workforce_employee_documents add constraint ck_workforce_document_approval_period check(approved_on is null or expires_on is null or approved_on<=expires_on);
alter table workforce_employee_documents add constraint ck_workforce_document_pr_period check(pr_effective_on is null or expires_on is null or pr_effective_on<=expires_on);
