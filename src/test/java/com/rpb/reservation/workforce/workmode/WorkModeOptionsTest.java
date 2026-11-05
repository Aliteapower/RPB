package com.rpb.reservation.workforce.workmode;

import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkModeOptionsTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 @Test void oldPolicyAssignmentPositionAndClockJsonRoundTripWithoutNewNullFields()throws Exception{
  var id=UUID.randomUUID();var records=new LinkedHashMap<String,Class<?>>();
  records.put("{\"mode\":\"onsite\",\"assignmentId\":null,\"assignmentVersion\":0,\"point\":null,\"addressId\":null,\"addressVersion\":0}",ClockPolicy.class);
  records.put("{\"expectedVersion\":1,\"effectiveFrom\":\"2026-11-01\",\"mode\":\"field\",\"inheritPosition\":false}",AssignmentWrite.class);
  records.put("{\"scheduleId\":\""+id+"\",\"action\":\"IN\",\"breakRuleId\":null,\"location\":null}",ClockWrite.class);
  records.put("{\"id\":\""+id+"\",\"action\":\"IN\",\"occurredAt\":null,\"actorId\":null}",Event.class);
  records.put("{\"id\":\""+id+"\",\"name\":\"Old\",\"enabled\":true,\"version\":1}",com.rpb.reservation.workforce.positions.PositionDtos.Position.class);
  for(var entry:records.entrySet())assertThat(mapper.readTree(mapper.writeValueAsString(mapper.readValue(entry.getKey(),entry.getValue())))).isEqualTo(mapper.readTree(entry.getKey()));
 }
 @Test void historicalNullPolicyAllowsOnlyLegacyAndSingletonNeverExpands(){
  assertThat(WorkModeOptions.select(null,null)).isNull();assertThat(WorkModeOptions.select(null,"legacy")).isNull();assertThatThrownBy(()->WorkModeOptions.select(null,"field")).extracting("code").isEqualTo("WORKFORCE_CLOCK_MODE_FORBIDDEN");
  var old=new ClockPolicy("onsite",null,0,null,null,0);assertThat(WorkModeOptions.select(old,null)).isSameAs(old);assertThatThrownBy(()->WorkModeOptions.select(old,"field")).extracting("code").isEqualTo("WORKFORCE_CLOCK_MODE_FORBIDDEN");
 }
 @Test void actualSelectionDoesNotMutatePermissionSnapshotAndNoGeometryReachesUnpunchedSource(){
  var home=new ClockOption("home",new Point("private",1d,2d,100d),UUID.randomUUID(),2,null);var field=new ClockOption("field",null,null,0,null);var policy=new ClockPolicy("field",UUID.randomUUID(),3,null,null,0,null,List.of(field,home));
  assertThat(WorkModeOptions.select(policy,"home").point()).isEqualTo(home.point());assertThat(policy.mode()).isEqualTo("field");assertThat(WorkModeOptions.geometryFree(policy).options()).allMatch(p->p.point()==null&&p.addressId()==null&&p.addressVersion()==0&&p.siteVersion()==null);
 }
}
