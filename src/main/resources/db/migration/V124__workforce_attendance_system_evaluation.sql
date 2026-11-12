-- System evaluation has no fabricated staff identity. Human FK remains intact.
alter table workforce_attendance_day_versions alter column actor_id drop not null;
alter table workforce_attendance_day_versions add column initiator_kind text not null default 'human';
alter table workforce_attendance_day_versions add constraint workforce_attendance_day_initiator check
 ((initiator_kind='human' and actor_id is not null) or (initiator_kind='system' and actor_id is null));
create index workforce_attendance_policies_evaluation on workforce_attendance_policies(tenant_id,source_store_id,employee_id,effective_from) where employee_id is not null;
