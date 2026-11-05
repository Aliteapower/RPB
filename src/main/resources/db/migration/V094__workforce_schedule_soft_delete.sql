-- Keep schedule IDs and all attendance/audit relationships intact.
alter table workforce_schedules add column deleted_at timestamptz null;
alter table workforce_schedules add constraint ck_workforce_schedule_deleted_cancelled check(deleted_at is null or status='cancelled');
