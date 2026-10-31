package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.payroll.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class WorkforcePayrollBoardPostgresTest extends WorkforcePayrollPostgresTest {
 PayrollBoardService board(){return proxy(new PayrollBoardService(new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time),time));}
 @Test void missingPeopleRemainVisibleAndNeverGetFakeZeroAmounts(){
  jdbc.update("delete from workforce_pay_profiles where tenant_id=?",tenant);
  var b=board().read(admin,store,company,"2026-10");
  assertThat(b.rows()).hasSize(1);assertThat(b.rows().getFirst().suggestedEffectiveFrom()).isEqualTo(repository.find(tenant,employee).orElseThrow().hireDate());assertThat(b.rows().getFirst().issues()).contains("PROFILE_MISSING");
  assertThat(b.rows().getFirst().net()).isNull();assertThat(b.rows().getFirst().breakdown()).isNull();assertThat(b.totals()).isNull();
  assertThatThrownBy(()->board().read(worker,store,company,"2026-10")).hasMessage("WORKFORCE_FORBIDDEN");
 }
 @Test void calculatedAmountsAreAuthoritativeAndFrozenNamesNeverFollowCurrentEmployee()throws Exception{
  jdbc.update("update workforce_positions set name='Frozen Service' where tenant_id=? and id=(select position_id from workforce_employees where tenant_id=? and id=?)",tenant,tenant,employee);completed();var b=action(create(),"calculate");var view=board().read(admin,store,company,"2026-10");
  assertThat(view.rows().getFirst().net()).isEqualTo(b.calculation().lines().getFirst().netPay());
  assertThat(view.totals().sdl()).isEqualTo(b.calculation().totalSdl());
  time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");
  var frozen=board().read(admin,store,company,"2026-10");
  assertThat(frozen.rows().getFirst().positionName()).isEqualTo("Frozen Service");
  jdbc.update("update workforce_employees set name='CHANGED',job_title='Changed position' where tenant_id=? and id=?",tenant,employee);
  jdbc.update("update workforce_positions set name='Changed position' where tenant_id=? and id=(select position_id from workforce_employees where tenant_id=? and id=?)",tenant,tenant,employee);
  var after=board().read(admin,store,company,"2026-10");
  assertThat(after.frozen()).isTrue();assertThat(after.rows()).isEqualTo(frozen.rows());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
  var oldLine=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(b.calculation().lines().getFirst());oldLine.remove("positionName");
  var compatible=mapper.treeToValue(oldLine,com.rpb.reservation.workforce.payroll.PayrollDtos.PayrollLine.class);
  assertThat(compatible.positionName()).isNull();assertThat(compatible.netPay()).isEqualTo(b.calculation().lines().getFirst().netPay());
 }
 @Test void staleOrBlockedCalculationHasUnknownBoardAmounts(){
  completed();action(create(),"calculate");
  jdbc.update("update workforce_schedules set version=version+1 where tenant_id=?",tenant);
  var b=board().read(admin,store,company,"2026-10");assertThat(b.sourceChanged()).isTrue();
  assertThat(b.rows().getFirst().net()).isNull();assertThat(b.totals()).isNull();
  assertThat(b.rows().getFirst().approvals()).hasSize(1);assertThat(b.rows().getFirst().approvals().getFirst().storeId()).isEqualTo(store);
 }
 @Test void latestEmployeePayslipUsesOnlyPublishedOwnSnapshotAcrossYears(){
  completed();var b=action(create(),"calculate");assertThat(payslips.latest(worker,store).slip()).isNull();
  time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");assertThat(payslips.latest(worker,store).slip()).isNull();b=action(b,"publish");
  var v=payslips.latest(worker,store).slip();assertThat(v.batchId()).isEqualTo(b.batch().id());assertThat(v.month()).isEqualTo("2026-10");assertThat(v.netPay()).isEqualTo("640.00");
 }

}
