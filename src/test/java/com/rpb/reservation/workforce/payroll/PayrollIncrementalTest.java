package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Employee;
import com.rpb.reservation.workforce.statutory.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayrollIncrementalTest {
 private final Clock clock=Clock.fixed(Instant.parse("2026-10-30T03:04:05.123456789Z"),ZoneOffset.UTC);
 private final YearMonth month=YearMonth.of(2026,10);
 private final UUID company=UUID.randomUUID(),store=UUID.randomUUID();
 private final PayrollCalculationService calculator=new PayrollCalculationService(clock);
 private final PayrollIncremental engine=new PayrollIncremental(calculator);

 @Test void missingCacheAndExplicitFullCalculateEveryEmployeeExactly() {
  var current=capture(List.of(line(0),line(1),line(2)));var first=engine.calculate(current,month,null,false);
  assertThat(first.snapshot()).isEqualTo(calculator.calculate(current,month));
  assertThat(first.recalculated()).isEqualTo(3);assertThat(first.reused()).isZero();
  var full=engine.calculate(current,month,first.cache(),true);
  assertThat(full.snapshot()).isEqualTo(first.snapshot());assertThat(full.recalculated()).isEqualTo(3);assertThat(full.reused()).isZero();
 }
 @Test void unchangedLinesAreReusedAndCompanyTotalsReaggregatedInCurrentOrder() {
  var a=line(0);var b=line(1);var old=capture(List.of(a,b));var previous=engine.calculate(old,month,null,false);
  var current=capture(List.of(b,a));var result=engine.calculate(current,month,previous.cache(),false);
  assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));assertThat(result.recalculated()).isZero();assertThat(result.reused()).isEqualTo(2);
  assertThat(result.snapshot().lines().getFirst()).isSameAs(previous.snapshot().lines().get(1));assertThat(result.changedEmployeeIds()).isEmpty();
 }
 @Test void oneAndMultipleChangesRecalculateOnlyAffectedLines() {
  var a=line(0);var b=line(1);var c=line(2);var baseline=engine.calculate(capture(List.of(a,b,c)),month,null,false).cache();
  for(var changed:List.of(List.of(changedInput(a),b,c),List.of(changedInput(a),changedInput(b),c))) {
   var current=capture(changed);var result=engine.calculate(current,month,baseline,false);int count=changed.get(1)==b?1:2;
   assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));assertThat(result.recalculated()).isEqualTo(count);assertThat(result.reused()).isEqualTo(3-count);
   assertThat(result.changedEmployeeIds()).contains(a.employee().id());
  }
 }
 @Test void globalPolicyCompanyMonthAndEngineChangesFallBackToFullCalculation() {
  var current=capture(List.of(line(0),line(1)));var previous=engine.calculate(current,month,null,false).cache();
  var p=current.statutoryPolicy();var revised=new StatutoryPolicies.Policy(p.id(),p.name(),p.year(),p.currency(),p.effectiveFrom(),p.effectiveTo(),p.enabled(),p.version()+1,p.config());
  var policyCapture=new PayrollSources.Capture("policy",current.company(),current.lines(),current.stores(),revised);
  var configRevision=new StatutoryPolicies.Policy(p.id(),p.name(),p.year(),p.currency(),p.effectiveFrom(),p.effectiveTo(),p.enabled(),p.version(),p.config().withOwCeiling("7900"));
  var sameVersionConfig=new PayrollSources.Capture("config",current.company(),current.lines(),current.stores(),configRevision);
  var companyCapture=new PayrollSources.Capture("company",new PayrollRepository.CompanyInfo("Changed","TEST",7,true,2),current.lines(),current.stores(),p);
  for(var next:List.of(policyCapture,companyCapture,sameVersionConfig)) {var result=engine.calculate(next,month,previous,false);assertThat(result.recalculated()).isEqualTo(2);assertThat(result.snapshot()).isEqualTo(calculator.calculate(next,month));}
  assertThat(engine.calculate(current,month.plusMonths(1),previous,false).recalculated()).isEqualTo(2);
  var obsolete=new PayrollIncremental.Cache("old",previous.globalKey(),previous.lineKeys(),previous.snapshot());
  assertThat(engine.calculate(current,month,obsolete,false).recalculated()).isEqualTo(2);
 }
 @Test void addedAndRemovedEmployeesAreReportedWithoutRecalculatingUnaffectedEmployees() {
  var a=line(0);var b=line(1);var c=line(2);var old=engine.calculate(capture(List.of(a,b)),month,null,false).cache();
  var current=capture(List.of(c,a));var result=engine.calculate(current,month,old,false);
  assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));assertThat(result.recalculated()).isEqualTo(1);assertThat(result.reused()).isEqualTo(1);
  assertThat(result.changedEmployeeIds()).containsExactly(c.employee().id(),b.employee().id());
  assertThat(engine.changed(current,month,old)).isEqualTo(result.changedEmployeeIds());
 }
 @Test void completeCapturedLineIncludingCrossMonthContextIssuesAndSpecialDaysInvalidatesCache() {
  var a=line(0);var old=engine.calculate(capture(List.of(a)),month,null,false).cache();var ref=a.sources().getFirst();
  var context=new SourceRef(ref.sourceId(),ref.storeId(),month.atDay(1).minusDays(1),2,"prior-month-edited",72000,0,0,false);
  var refs=new ArrayList<>(a.sources());refs.addFirst(context);
  var crossMonth=new PayrollSources.CapturedLine(a.employee(),a.profile(),a.input(),refs,a.issues(),a.specialDays());
  var issues=new PayrollSources.CapturedLine(a.employee(),a.profile(),a.input(),a.sources(),List.of("SOURCE_PENDING"),a.specialDays());
  var special=new PayrollSources.CapturedLine(a.employee(),a.profile(),a.input(),a.sources(),a.issues(),true);
  for(var value:List.of(crossMonth,issues,special)) {var current=capture(List.of(value));var result=engine.calculate(current,month,old,false);assertThat(result.recalculated()).isOne();assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));}
 }
 @Test void missingKeysOrSnapshotAlwaysFallBackToFull() {
  var current=capture(List.of(line(0),line(1)));var old=engine.calculate(current,month,null,false).cache();
  for(var invalid:List.of(new PayrollIncremental.Cache(old.engineKey(),old.globalKey(),Map.of(),old.snapshot()),new PayrollIncremental.Cache(old.engineKey(),old.globalKey(),old.lineKeys(),null),new PayrollIncremental.Cache(old.engineKey(),old.globalKey(),null,old.snapshot()))) {
   var result=engine.calculate(current,month,invalid,false);assertThat(result.recalculated()).isEqualTo(2);assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));
  }
 }
 @Test void employeeAndProfileFieldsInvalidateEvenWhenInputAndSourcesStayTheSame() {
  var a=line(0);var previous=engine.calculate(capture(List.of(a)),month,null,false).cache();
  var e=a.employee();var employee=new Employee(e.id(),e.employeeNo(),"Renamed",e.phone(),e.email(),e.jobTitle(),e.hireDate(),e.leaveDate(),e.status(),e.companyId(),e.companyEffectiveDate(),e.mainStoreId(),e.storeIds(),e.accountId(),e.version()+1,e.editable(),e.positionId(),e.birthDate(),e.englishName(),e.enabled());
  var renamed=new PayrollSources.CapturedLine(employee,a.profile(),a.input(),a.sources(),a.issues(),false);
  var repriced=priced(a,"100");
  for(var changed:List.of(renamed,repriced)) {var current=capture(List.of(changed));var result=engine.calculate(current,month,previous,false);assertThat(result.recalculated()).isOne();assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));}
 }
 @Test void fractionalSdlAndNegativeNetKeepOriginalCompanyRoundingAndIssues() {
  var a=priced(line(0),"100");var b=priced(line(1),"100");var previous=engine.calculate(capture(List.of(a,b)),month,null,false).cache();
  var calculated=previous.snapshot().lines().getFirst();var deductions=new java.math.BigDecimal(calculated.grossPay()).add(new java.math.BigDecimal(calculated.reimbursements())).toPlainString();
  var input=new LineInput(a.employee().id(),2,null,null,true,true,List.of(new Item("Deduction","deduction",deductions)),"reviewed");
  var changed=new PayrollSources.CapturedLine(a.employee(),a.profile(),input,a.sources(),a.issues(),false);var current=capture(List.of(changed,b));var result=engine.calculate(current,month,previous,false);
  assertThat(result.recalculated()).isOne();assertThat(result.reused()).isOne();assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));
  assertThat(result.snapshot().lines().getFirst().issues()).contains("NEGATIVE_NET");assertThat(new java.math.BigDecimal(result.snapshot().lines().getFirst().netPay()).signum()).isNegative();
  var exact=result.snapshot().lines().stream().map(l->new java.math.BigDecimal(l.sdl())).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
  assertThat(result.snapshot().totalSdl()).isEqualTo(exact.setScale(0,java.math.RoundingMode.FLOOR).setScale(2).toPlainString());
  var individuallyRounded=result.snapshot().lines().stream().map(l->new java.math.BigDecimal(l.sdl()).setScale(0,java.math.RoundingMode.FLOOR)).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
  assertThat(exact.setScale(0,java.math.RoundingMode.FLOOR)).isGreaterThan(individuallyRounded);
 }
 @Test void emptyCompanyAndRemovalOfAllEmployeesRetainFullCalculationIssues() {
  var current=capture(List.of());var previous=engine.calculate(capture(List.of(line(0))),month,null,false).cache();
  var result=engine.calculate(current,month,previous,false);assertThat(result.snapshot()).isEqualTo(calculator.calculate(current,month));assertThat(result.snapshot().issues()).contains("NO_EMPLOYEES");assertThat(result.changedEmployeeIds()).hasSize(1);
  assertThat(engine.calculate(current,month,result.cache(),false).snapshot()).isEqualTo(result.snapshot());
 }
 @Test void comparisonIgnoresOnlyTimeAndRejectsEveryOtherChangedField() throws Exception {
  var snapshot=calculator.calculate(capture(List.of(line(0))),month);var components=PayrollSnapshot.class.getRecordComponents();var signature=Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);var constructor=PayrollSnapshot.class.getDeclaredConstructor(signature);
  for(int index=0;index<components.length;index++) {
   var values=new Object[components.length];for(int i=0;i<values.length;i++)values[i]=components[i].getAccessor().invoke(snapshot);
   values[index]=components[index].getType()==String.class?values[index]+"!":components[index].getType()==List.class?List.of():components[index].getType()==Instant.class?Instant.EPOCH:null;
   var changed=constructor.newInstance(values);assertThat(PayrollIncremental.sameResult(snapshot,changed)).as(components[index].getName()).isEqualTo(components[index].getName().equals("calculatedAt"));
  }
 }
 private PayrollSources.Capture capture(List<PayrollSources.CapturedLine> lines) {
  var template=OfficialStatutoryTemplates.forYear(2026);
  var policy=new StatutoryPolicies.Policy(new UUID(0,50),template.name(),2026,"SGD",template.effectiveFrom(),template.effectiveTo(),true,1,template.config());
  return new PayrollSources.Capture("fp-"+lines.hashCode(),new PayrollRepository.CompanyInfo("Synthetic","TEST",7,true,1),lines,Set.of(store),policy);
 }
 private PayrollSources.CapturedLine changedInput(PayrollSources.CapturedLine line) {
  var old=line.input();var input=new LineInput(old.employeeId(),old.version()+1,old.workingDays(),old.payableDays(),true,true,List.of(new Item("Huge deduction","deduction","999999.00")),"changed");
  return new PayrollSources.CapturedLine(line.employee(),line.profile(),input,line.sources(),line.issues(),line.specialDays());
 }
 private PayrollSources.CapturedLine priced(PayrollSources.CapturedLine line,String rate) {
  var p=line.profile();var r=p.rule();var rule=new Rule(r.basis(),rate,r.dayHours(),r.weekHours(),r.overtimeEnabled(),r.overtimeMultiplier(),r.items(),r.generation());
  var profile=new PayProfile(p.id(),p.companyId(),p.employeeId(),p.employeeName(),p.effectiveFrom(),p.templateId(),p.templateVersion(),rule,p.cpf(),p.version()+1,p.payment(),p.cdac());
  return new PayrollSources.CapturedLine(line.employee(),profile,line.input(),line.sources(),line.issues(),line.specialDays());
 }
 private PayrollSources.CapturedLine line(int i) {
  var id=new UUID(0,i+1);var employee=new Employee(id,"E"+i,"Synthetic "+i,"","","",month.atDay(1),null,"active",company,month.atDay(1),null,List.of(),null,1,false);
  var rule=new Rule("hourly","13.17","8","44",true,"1.5",List.of(new Item("Expense","reimbursement","17.89")));
  var profile=new PayProfile(new UUID(1,i),company,id,employee.name(),month.atDay(1),null,0,rule,new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1,null,new CdacProfile("applicable","reviewed"));
  var input=new LineInput(id,1,null,null,true,true,List.of(),"reviewed");
  var refs=List.of(new SourceRef(new UUID(2,i),store,month.atDay(1),1,"source",37001+i,1800,600,true));
  return new PayrollSources.CapturedLine(employee,profile,input,refs,List.of("SHARED"),false);
 }
}
