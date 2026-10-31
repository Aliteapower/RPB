package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.positions.PositionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
/** Explicit opt-in, owned synthetic runtime. Never points at the source checkout database. */
@EnabledIfSystemProperty(named="rpb.browser.fixture",matches="true")
class WorkforceGuidedPayrollBrowserFixtureTest extends WorkforcePayrollPostgresTest {
 @AfterAll static void cleanup(){/* Keep this owned schema for the loopback browser runtime; no production data. */}
 @Test void createBrowserFixture()throws Exception{
  var folder=Path.of(System.getProperty("rpb.browser.fixture.dir","target/guided-browser"));assertThat(Files.exists(folder.resolve("fixture.json"))).isFalse();Files.createDirectories(folder);
  // Match Spring Boot ISO date serialization: approval fingerprints must survive the fixture/runtime boundary.
  var browserMapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  var browserJson=new WorkforceJson(browserMapper);var browserAudit=new WorkforceAudit(logs,browserMapper);
  var browserApprovals=new com.rpb.reservation.workforce.approval.ApprovalRepository(jdbc,browserJson);
  var browserSource=new com.rpb.reservation.workforce.approval.ApprovalSources(attendance,new com.rpb.reservation.workforce.leave.LeaveRepository(jdbc),new com.rpb.reservation.workforce.calendar.CalendarRepository(jdbc),browserJson,time);
  approvals=proxy(new com.rpb.reservation.workforce.approval.ApprovalService(access,new com.rpb.reservation.workforce.attendance.AttendanceAccess(access,repository,schedules),repository,schedules,browserApprovals,browserSource,browserAudit,time));
  sources=new PayrollSources(payRepository,configs,repository,schedules,browserApprovals,browserSource,browserJson,new com.rpb.reservation.workforce.workmode.WorkModeResolver(new com.rpb.reservation.workforce.workmode.WorkModeRepository(jdbc,browserJson)));
  batches=proxy(new PayrollBatchService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time),repository,browserAudit,time,tx));
  jdbc.update("update operating_entities set display_name=? where tenant_id=? and id=?","合成已发薪公司",tenant,company);
  jdbc.update("update operating_entities set display_name=? where tenant_id=? and id=?","合成待办公司",tenant,otherCompany);
  jdbc.update("update workforce_employees set name=?,english_name=? where tenant_id=? and id=?","合成员工张三","Synthetic Zhang",tenant,employee);
  completed();var batch=action(create(),"calculate");time.at("2026-11-02T01:00:00Z");batch=action(action(batch,"confirm"),"publish");
  var pending=UUID.randomUUID();employees.save(admin,otherStore,pending,new WorkforceDtos.EmployeeWrite(0L,"QA002","合成员工李四","","","Service",LocalDate.of(2026,10,1),null,"active",otherCompany,LocalDate.of(2026,10,1),otherStore,List.of(otherStore),null,null,null));
  var monthly=UUID.randomUUID();var hourly=UUID.randomUUID();var monthlyRule=PayrollRules.normalize(new Rule("monthly","2800.00","8","44",false,"1.5",List.of(new Item("固定交通津贴","ow_allowance","100.00")),new Generation("attendance",List.of(1,2,3,4,5))));
  var hourlyRule=PayrollRules.normalize(new Rule("hourly","15.00","8","44",false,"1.5",List.of(),new Generation("attendance",List.of(1,2,3,4,5))));
  legacyTemplate(new SalaryTemplate(monthly,otherCompany,"服务员月薪标准",monthlyRule,true,1));legacyTemplate(new SalaryTemplate(hourly,otherCompany,"服务员时薪标准",hourlyRule,true,1));
  var mapper=browserMapper;var defaults=proxy(new PositionSalaryDefaultsService(access,new PayrollAccess(access,directory,jdbc),configs,repository,new PositionRepository(jdbc),new WorkforceAudit(logs,mapper)));var pos=repository.find(tenant,pending).orElseThrow().positionId();
  defaults.save(admin,otherStore,UUID.randomUUID(),new DefaultWrite(0L,otherCompany,pos,LocalDate.of(2026,10,1),monthly,true,"monthly"));defaults.save(admin,otherStore,UUID.randomUUID(),new DefaultWrite(0L,otherCompany,pos,LocalDate.of(2026,10,1),hourly,true,"hourly"));
  time.at("2026-10-28T01:00:00Z");var shift=shifts.save(admin,otherStore,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"合成日班","09:00","17:00",false,"none",0,0,0,0,false,true));var schedule=roster.save(admin,otherStore,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,pending,shift.id(),LocalDate.of(2026,10,29),"scheduled"));
  attendance.save(tenant,new com.rpb.reservation.workforce.attendance.AttendanceDtos.Session(UUID.randomUUID(),schedule.id(),pending,otherStore,schedule.startAt(),null,null,List.of(),false,1));
  jdbc.update("insert into tenant_app_entitlements(tenant_id,app_key,status) values(?,'workforce','enabled')",tenant);
  for(var shop:List.of(store,sibling,otherStore))jdbc.update("insert into store_app_settings(tenant_id,store_id,app_key,is_enabled,entry_visible) values(?,?,'workforce',true,true)",tenant,shop);
  var subscription=UUID.randomUUID();jdbc.update("insert into tenant_product_subscriptions(id,tenant_id,app_key,billing_cycle,status) values(?,?,'workforce','manual','active')",subscription,tenant);
  for(var shop:List.of(store,sibling,otherStore))jdbc.update("insert into tenant_product_subscription_items(subscription_id,tenant_id,app_key,scope_type,store_id,billing_cycle,status) values(?,?,'workforce','store',?,'manual','active')",subscription,tenant,shop);
  // An employed synthetic person has two approved days and one independently settled prefix.
  UUID segmentAccount=account("store_staff",Set.of(otherStore),Set.of("workforce.view")),segmentEmployee=UUID.randomUUID();employees.save(admin,otherStore,segmentEmployee,new WorkforceDtos.EmployeeWrite(0L,"QA003","合成员工王五","","","Service",LocalDate.of(2026,10,1),null,"active",otherCompany,LocalDate.of(2026,10,1),otherStore,List.of(otherStore),segmentAccount,null,null));
  legacyProfile(new PayProfile(UUID.randomUUID(),otherCompany,segmentEmployee,"合成员工王五",LocalDate.of(2026,10,1),hourly,1,hourlyRule,new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1,null,new CdacProfile("not_applicable","Synthetic declaration")));
  for(int day:List.of(28,29)){time.at("2026-10-"+day+"T01:00:00Z");var work=roster.save(admin,otherStore,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,segmentEmployee,shift.id(),LocalDate.of(2026,10,day),"scheduled"));attendance.save(tenant,new com.rpb.reservation.workforce.attendance.AttendanceDtos.Session(UUID.randomUUID(),work.id(),segmentEmployee,otherStore,work.startAt(),work.endAt(),null,List.of(),false,1));time.at("2026-10-30T01:00:00Z");var row=approvals.detail(admin,otherStore,work.id()).row();approvals.approve(admin,otherStore,work.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Synthetic reviewer"));}
  var fixtureJson=new WorkforceJson(mapper);var segmentService=proxy(new com.rpb.reservation.workforce.payroll.segment.SegmentService(access,new PayrollAccess(access,directory,jdbc),new com.rpb.reservation.workforce.payroll.segment.SegmentRepository(jdbc,fixtureJson),payRepository,sources,new PayrollCalculationService(time),repository,new WorkforceAudit(logs,mapper),fixtureJson,jdbc,time));var segment=segmentService.save(admin,otherStore,UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.segment.SegmentDtos.Write(0L,otherCompany,segmentEmployee,"2026-10",LocalDate.of(2026,10,28),null,null,true,false,"Synthetic independent settlement"));for(var act:List.of("calculate","confirm","publish"))segment=segmentService.action(admin,otherStore,segment.id(),UUID.randomUUID(),new com.rpb.reservation.workforce.payroll.segment.SegmentDtos.Action(segment.version(),act,"Synthetic review"));
  String adminToken=UUID.randomUUID()+"-"+UUID.randomUUID(),staffToken=UUID.randomUUID()+"-"+UUID.randomUUID();
  session(adminId,adminToken);session(segmentAccount,staffToken);
  var f=new LinkedHashMap<String,Object>();f.put("tenant",tenant);f.put("admin",adminId);f.put("staff",segmentAccount);f.put("staffStore",otherStore);f.put("adminToken",adminToken);f.put("staffToken",staffToken);f.put("store",store);f.put("otherStore",otherStore);f.put("company",company);f.put("otherCompany",otherCompany);f.put("employee",segmentEmployee);f.put("originalEmployee",employee);f.put("segment",segment.id());f.put("pendingEmployee",pending);f.put("position",pos);f.put("batch",batch.batch().id());f.put("schema",SCHEMA);f.put("backendPort",8499);f.put("proxyPort",8500);
  String browserSchema="workforce_browser_"+UUID.randomUUID().toString().replace("-","");root.execute("alter schema "+SCHEMA+" rename to "+browserSchema);f.put("schema",browserSchema);
  Files.writeString(folder.resolve("fixture.json"),mapper.writeValueAsString(f));
 }
 void session(UUID account,String token)throws Exception{var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));jdbc.update("insert into auth_user_sessions(account_id,tenant_id,session_hash,status,expires_at) values(?,?,?,'active',now()+interval '4 hours')",account,tenant,hash);}
}
