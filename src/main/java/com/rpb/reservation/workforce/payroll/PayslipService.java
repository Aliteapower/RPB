package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class PayslipService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollRepository repository;private final WorkforceEmployeeRepository employees;
 public PayslipService(WorkforceAccess gate,PayrollAccess access,PayrollRepository repository,WorkforceEmployeeRepository employees){this.gate=gate;this.access=access;this.repository=repository;this.employees=employees;}
 public List<PayslipSummary> mine(CurrentActor actor,UUID store,int year){gate.enter(actor,store);if(year<2000||year>2100)throw WorkforceException.invalid();var self=employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);return repository.published(actor.tenantId(),self.id(),year).stream().map(b->{var line=line(b,self.id());return new PayslipSummary(b.id(),b.calculation().companyName(),b.month().toString(),line.netPay(),PayrollValues.money(PayrollValues.paid(repository.payments(actor.tenantId(),b.id()),self.id())),b.status());}).toList();}
 public Payslip mineDetail(CurrentActor actor,UUID store,UUID id){gate.enter(actor,store);var self=employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);if(!b.status().equals("published"))throw WorkforceException.missing();return view(b,self.id());}
 public Payslip preview(CurrentActor actor,UUID store,UUID id,UUID employee){gate.enter(actor,store);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));return view(b,employee);}
 private PayrollLine line(PayrollRepository.State b,UUID employee){if(b.calculation()==null)throw WorkforceException.missing();return b.calculation().lines().stream().filter(l->l.employeeId().equals(employee)).findFirst().orElseThrow(WorkforceException::missing);}
 private Payslip view(PayrollRepository.State b,UUID employee){
  var l=line(b,employee);if(l.profile()==null)throw WorkforceException.missing();var i=l.input();var clean=new LineInput(i.employeeId(),i.version(),i.workingDays(),i.payableDays(),i.unitsReviewed(),i.specialDaysReviewed(),i.items(),"");
  var display=new PayslipLine(l.employeeId(),l.employeeName(),l.employeeNo(),l.profile().rule(),clean,l.workSeconds(),l.normalSeconds(),l.overtimeSeconds(),l.paidLeaveSeconds(),l.unpaidLeaveSeconds(),l.basicPay(),l.overtimePay(),l.allowances(),l.reimbursements(),l.deductions(),l.grossPay(),l.employeeCpf(),l.employerCpf(),l.sdl(),l.employerCharges(),l.netPay(),l.cpfRateCode(),l.components());
  var all=repository.payments(b.tenant(),b.id()).stream().filter(p->p.employeeId().equals(employee)).toList();var paid=PayrollValues.paid(all,employee);var publicPayments=all.stream().map(p->new Payment(p.id(),p.employeeId(),p.amount(),p.paymentDate(),p.method(),p.reference(),"",p.reversesId(),null,p.createdAt(),p.bank())).toList();
  return new Payslip(b.id(),b.calculation().companyName(),b.month().toString(),b.status(),display,publicPayments,PayrollValues.money(paid),PayrollValues.money(new BigDecimal(l.netPay()).subtract(paid)),PayBreakdown.of(l),l.profile().payment(),l.cdacCalculation());
 }
}
