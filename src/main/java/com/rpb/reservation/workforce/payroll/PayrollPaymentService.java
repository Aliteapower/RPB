package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
public class PayrollPaymentService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollRepository repository;private final PayrollBatchService batches;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;private final Clock clock;
 public PayrollPaymentService(WorkforceAccess gate,PayrollAccess access,PayrollRepository repository,PayrollBatchService batches,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock){this.gate=gate;this.access=access;this.repository=repository;this.batches=batches;this.employees=employees;this.audit=audit;this.clock=clock;}
 @Transactional public BatchDetail register(CurrentActor actor,UUID store,UUID id,UUID payment,PaymentWrite c){
  gate.enter(actor,store);if(payment==null||c==null||c.employeeId()==null||c.paymentDate()==null||!PaymentPreferences.method(c.method()))throw WorkforceException.invalid();
  var amount=PayrollValues.decimal(c.amount());String bank=PaymentPreferences.bank(c.bank());String reference=PayrollValues.text(c.reference(),100,c.method().equals("bank")),note=PayrollValues.text(c.note(),500,true);if(amount.signum()<=0||c.paymentDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")))))throw WorkforceException.invalid();
  repository.lock(actor.tenantId(),id);gate.sourceWrite(actor);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));if(!b.status().equals("published")||c.paymentDate().isBefore(b.month().atDay(1)))throw WorkforceException.conflict();
  var line=b.calculation().lines().stream().filter(l->l.employeeId().equals(c.employeeId())).findFirst().orElseThrow(WorkforceException::missing);var e=employees.find(actor.tenantId(),c.employeeId()).orElseThrow();if(actor.actorId().equals(e.accountId()))throw WorkforceException.forbidden();
  var old=repository.payment(actor.tenantId(),payment).orElse(null);if(old!=null){if(repository.payments(actor.tenantId(),id).stream().noneMatch(p->p.id().equals(payment))||!old.employeeId().equals(c.employeeId())||!old.amount().equals(PayrollValues.money(amount))||!old.paymentDate().equals(c.paymentDate())||!old.method().equals(c.method())||!old.reference().equals(reference)||!old.note().equals(note)||!Objects.equals(old.reversesId(),c.reversesId())||!old.actorId().equals(actor.actorId())||!PaymentPreferences.bank(old.bank()).equals(bank))throw WorkforceException.conflict();return batches.detail(actor,store,id);}
  if(c.method().equals("cash")&&!bank.isEmpty()||c.reversesId()==null&&c.method().equals("bank")&&bank.isEmpty())throw WorkforceException.invalid();
  var all=repository.payments(actor.tenantId(),id);
  if(c.reversesId()!=null){var original=all.stream().filter(p->p.id().equals(c.reversesId())&&p.employeeId().equals(c.employeeId())).findFirst().orElseThrow(WorkforceException::missing);if(original.reversesId()!=null||all.stream().anyMatch(p->original.id().equals(p.reversesId()))||new BigDecimal(original.amount()).compareTo(amount)!=0||!original.method().equals(c.method())||!PaymentPreferences.bank(original.bank()).equals(bank)||c.paymentDate().isBefore(original.paymentDate()))throw WorkforceException.conflict();}
  else if(PayrollValues.paid(all,c.employeeId()).add(amount).compareTo(new BigDecimal(line.netPay()))>0)throw new WorkforceException("WORKFORCE_PAYROLL_OVERPAYMENT",409);
  var next=new Payment(payment,c.employeeId(),PayrollValues.money(amount),c.paymentDate(),c.method(),reference,note,c.reversesId(),actor.actorId(),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),bank);repository.payment(b,next);audit.changed(actor,store,c.reversesId()==null?"payroll.payment":"payroll.payment-reverse",payment,null,Map.of("batchId",id,"employeeId",c.employeeId(),"reversal",c.reversesId()!=null));return batches.detail(actor,store,id);
 }
}
