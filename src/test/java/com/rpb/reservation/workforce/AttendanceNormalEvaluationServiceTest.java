package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.attendance.daily.*;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceNormalEvaluationRepository.*;
import com.rpb.reservation.workforce.approval.ApprovalRepository;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class AttendanceNormalEvaluationServiceTest {
 private final AttendanceNormalEvaluationRepository repository=mock(AttendanceNormalEvaluationRepository.class);
 private final AttendanceDailyRepository days=mock(AttendanceDailyRepository.class);
 private final AttendanceDailyService daily=mock(AttendanceDailyService.class);
 private final ApprovalRepository approvals=mock(ApprovalRepository.class);
 private final WorkforceEmployeeRepository employees=mock(WorkforceEmployeeRepository.class);
 private final AppGateService gate=mock(AppGateService.class);
 private final AuditLogRepositoryPort audit=mock(AuditLogRepositoryPort.class);
 private final WorkforceJson json=mock(WorkforceJson.class);
 private final Scope scope=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Asia/Singapore");
 private final LocalDate date=LocalDate.of(2026,10,30);
 private final AttendanceNormalEvaluationService service=new AttendanceNormalEvaluationService(repository,days,daily,approvals,employees,gate,audit,json,Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"),ZoneOffset.UTC));
 @Test void disabledCommercialScopeHasNoWritesOrLocks(){when(gate.evaluateCommercial("workforce",scope.tenant(),scope.store())).thenReturn(new AppGateDecision(false,"workforce",scope.tenant(),scope.store(),null,null,null,Map.of()));assertEquals(0,service.evaluate(scope));verifyNoInteractions(repository,days,daily,approvals,employees,audit);}
 @Test void managerPendingRemainsPendingAndSystemAuditHasNoActor(){allow();var candidate=day("pending",null,List.of("WORKFORCE_MANAGER_ADMIN_REVIEW"));when(daily.evaluateForSystem(scope.tenant(),scope.company(),scope.employee(),date)).thenReturn(candidate);assertEquals(1,service.evaluate(scope));verify(repository).save(scope,candidate);var capture=org.mockito.ArgumentCaptor.forClass(AuditLog.class);verify(audit).append(any(StoreScope.class),capture.capture());assertEquals("system",capture.getValue().source());assertEquals("system",capture.getValue().actorType());assertNull(capture.getValue().actorId());var order=inOrder(repository,approvals,employees);order.verify(repository).lockSources(scope.tenant());order.verify(approvals).calendarLock(scope.tenant(),scope.store());order.verify(employees).lock(scope.employee());}
 @Test void defaultNormalCompletedWithoutPunchAppendsOnce(){allow();var candidate=day("system_valid",28800L,List.of());when(daily.evaluateForSystem(scope.tenant(),scope.company(),scope.employee(),date)).thenReturn(candidate);when(days.latest(scope.tenant(),scope.company(),scope.employee(),date)).thenReturn(Optional.empty(),Optional.of(candidate));assertEquals(1,service.evaluate(scope));assertEquals(0,service.evaluate(scope));verify(repository,times(1)).save(scope,candidate);}
 private void allow(){when(gate.evaluateCommercial("workforce",scope.tenant(),scope.store())).thenReturn(AppGateDecision.allow("workforce",scope.tenant(),scope.store(),null));when(repository.adopted(scope,date)).thenReturn(true);when(days.latest(scope.tenant(),scope.company(),scope.employee(),date)).thenReturn(Optional.empty());}
 private EffectiveDay day(String state,Long work,List<String> issues){return new EffectiveDay(UUID.randomUUID(),scope.store(),date,"default_normal",state,"arrangement","fingerprint",1,null,work,0,0,0,Map.of("phase","completed"),issues);}
}
