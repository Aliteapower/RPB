package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.statutory.StatutoryPolicies;
import com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
class PayrollConfigurationBirthDateTest {
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),company=UUID.randomUUID(),employee=UUID.randomUUID(),template=UUID.randomUUID();
 final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of("workforce.view"),Set.of(store));
 final WorkforceAccess gate=mock(WorkforceAccess.class);final PayrollAccess access=mock(PayrollAccess.class);final PayrollConfigurationRepository repository=mock(PayrollConfigurationRepository.class);final WorkforceEmployeeRepository employees=mock(WorkforceEmployeeRepository.class);
 final WorkforceAudit audit=mock(WorkforceAudit.class);
 final PayrollConfigurationService service=new PayrollConfigurationService(gate,access,repository,employees,audit,Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"),ZoneOffset.UTC));
 final Rule rule=new Rule("hourly","12.00","8.00","44.00",false,"1.50",List.of());
 void employee(LocalDate birth){when(employees.find(tenant,employee)).thenReturn(Optional.of(new WorkforceDtos.Employee(employee,"E1","Employee","","","",LocalDate.of(2026,1,1),null,"active",company,LocalDate.of(2026,1,1),store,List.of(store),null,1,true,null,birth,"",true)));when(repository.findTemplate(tenant,template)).thenReturn(Optional.of(new SalaryTemplate(template,company,"Template",rule,true,1)));}
 ProfileWrite command(CpfProfile cpf){return new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),template,rule,cpf);}
 @Test void employeeBirthdayOverridesMissingOrConflictingManualBirthday(){var birth=LocalDate.of(1989,5,10);employee(birth);var saved=service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("citizen",null,null,"GG","")));assertThat(saved.cpf().birthDate()).isEqualTo(birth);var order=inOrder(access,employees);order.verify(access).enter(actor,store,company);order.verify(access).employee(actor,company,employee);order.verify(employees).find(tenant,employee);assertThat(service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("citizen",LocalDate.of(1995,1,1),null,"GG",""))).cpf().birthDate()).isEqualTo(birth);}
 @Test void legacyManualBirthdayRemainsFallbackWhenEmployeeBirthdayMissing(){employee(null);var birth=LocalDate.of(1991,2,3);assertThat(service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("citizen",birth,null,"GG",""))).cpf().birthDate()).isEqualTo(birth);assertThatThrownBy(()->service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("citizen",null,null,"GG","")))).isInstanceOf(WorkforceException.class);}
 @Test void foreignProfileDoesNotGainCpfBirthdayFromEmployee(){employee(LocalDate.of(1990,1,1));assertThat(service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("foreign",null,null,"GG",""))).cpf().birthDate()).isNull();}
 @Test void authoritativeBirthdayIsUsedForPrDateValidation(){employee(LocalDate.of(1990,1,1));assertThatThrownBy(()->service.saveProfile(actor,store,UUID.randomUUID(),command(new CpfProfile("spr",LocalDate.of(1980,1,1),LocalDate.of(1985,1,1),"GG","")))).isInstanceOf(WorkforceException.class);}
 @Test void birthdayMapLoadsOnlyAfterCompanyAccess(){doThrow(WorkforceException.forbidden()).when(access).enter(actor,store,company);assertThatThrownBy(()->service.profiles(actor,store,company)).isInstanceOf(WorkforceException.class);verifyNoInteractions(repository);}
 @Test void birthdayMapIsReturnedForAuthorizedEmployeePicker(){when(repository.employeeBirthDates(tenant,company)).thenReturn(Map.of(employee,LocalDate.of(1990,1,1)));assertThat(service.profiles(actor,store,company).employeeBirthDates()).containsEntry(employee,LocalDate.of(1990,1,1));verify(access).enter(actor,store,company);}
 @Test void homeUsesCurrentSingaporeMonthAndConfiguredPolicyRevision(){var t=OfficialStatutoryTemplates.forYear(2027);var id=UUID.randomUUID();when(repository.activePolicy(tenant,YearMonth.of(2027,1))).thenReturn(new StatutoryPolicies.Policy(id,t.name(),t.year(),t.currency(),t.effectiveFrom(),t.effectiveTo(),true,4,t.config()));var summary=service.home(actor,store).policy();assertThat(summary.version()).isEqualTo(id+"@4");assertThat(summary.year()).isEqualTo(2027);assertThat(summary.cpfSource()).isEqualTo(t.config().cpfSource());}
 @Test void homeNeverInvents2026PolicyWhenUnconfigured(){var summary=service.home(actor,store).policy();assertThat(summary.version()).isEqualTo("UNCONFIGURED");assertThat(summary.year()).isZero();assertThat(summary.owCeiling()).isEqualTo("0");}
 @Test void monthRangeSupportsSavedFuturePoliciesWithoutPermittingOutOfRangeYears(){assertThat(PayrollValues.month("2000-01")).isEqualTo(YearMonth.of(2000,1));assertThat(PayrollValues.month("2027-01")).isEqualTo(YearMonth.of(2027,1));assertThat(PayrollValues.month("2100-12")).isEqualTo(YearMonth.of(2100,12));for(var invalid:List.of("1999-12","2101-01","2027-13","2027-1"))assertThatThrownBy(()->PayrollValues.month(invalid)).isInstanceOf(WorkforceException.class);}
 @Test void stalePreviewCannotTreatLegacySourceAsReplayAndValidVersionCapturesMissingSource(){
  var birth=LocalDate.of(1990,1,1);employee(birth);var id=UUID.randomUUID();var cpf=new CpfProfile("citizen",birth,null,"GG","");var old=new PayProfile(id,company,employee,"Employee",LocalDate.of(2026,10,1),template,1,rule,cpf,1);
  when(repository.findProfile(tenant,id)).thenReturn(Optional.of(old));
  assertThatThrownBy(()->service.saveProfile(actor,store,id,new ProfileWrite(0L,company,employee,old.effectiveFrom(),template,rule,cpf,null,null,1L,null,null))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CONFLICT");
  var saved=service.saveProfile(actor,store,id,new ProfileWrite(1L,company,employee,old.effectiveFrom(),template,rule,cpf,null,null,1L,null,null));
  assertThat(saved.origin()).isNotNull();assertThat(saved.version()).isEqualTo(2);assertThat(saved.origin().templateRule()).isEqualTo(rule);
  when(repository.findProfile(tenant,id)).thenReturn(Optional.of(saved));
  assertThat(service.saveProfile(actor,store,id,new ProfileWrite(1L,company,employee,old.effectiveFrom(),template,rule,cpf,null,null,1L,null,null))).isEqualTo(saved);
  verify(repository,times(1)).saveProfile(eq(tenant),any());verify(audit,times(1)).changed(any(),any(),any(),any(),any(),any());
  when(repository.findProfile(tenant,id)).thenReturn(Optional.of(old));when(repository.lockedProfile(tenant,id)).thenReturn(true);assertThatThrownBy(()->service.saveProfile(actor,store,id,new ProfileWrite(1L,company,employee,old.effectiveFrom(),template,rule,cpf,null,null,1L,null,null))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_PAYROLL_PROFILE_LOCKED");
 }
}
