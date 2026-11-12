-- No backfill or changes to frozen payroll amounts. Legacy bank fields stay unknown (NULL).
alter table workforce_pay_payments add column bank text null check(bank is null or length(bank)<=100);
alter table workforce_pay_payments drop constraint workforce_pay_payments_method_check;
alter table workforce_pay_payments add constraint workforce_pay_payments_method_check check(method in ('cash','bank','paynow','other'));
