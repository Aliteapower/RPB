-- History is append-only even if a future persistence change accidentally issues an update.
create trigger compliance_policy_immutable before update or delete on workforce_compliance_policies
 for each row execute function workforce_pay_template_immutable();
create trigger employment_contract_immutable before update or delete on workforce_employment_contracts
 for each row execute function workforce_pay_template_immutable();
create trigger leave_lot_immutable before update or delete on workforce_leave_credit_lots
 for each row execute function workforce_pay_template_immutable();
create trigger leave_ledger_immutable before update or delete on workforce_leave_ledger
 for each row execute function workforce_pay_template_immutable();
create trigger compliance_command_immutable before update or delete on workforce_compliance_commands
 for each row execute function workforce_pay_template_immutable();
create trigger leave_encash_consumption_immutable before update or delete on workforce_leave_encashment_consumption
 for each row execute function workforce_pay_template_immutable();
create function workforce_leave_encashment_evidence_guard() returns trigger language plpgsql as $$
begin
 if (new.snapshot - 'status' - 'requestId' - 'consumedBy') is distinct from
    (old.snapshot - 'status' - 'requestId' - 'consumedBy') then
  raise exception 'Leave encashment valuation and source evidence are immutable';
 end if;
 return new;
end;
$$;
create trigger leave_encash_evidence_guard before update on workforce_leave_encashments
 for each row execute function workforce_leave_encashment_evidence_guard();
