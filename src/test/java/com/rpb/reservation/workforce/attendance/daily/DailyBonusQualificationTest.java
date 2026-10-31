package com.rpb.reservation.workforce.attendance.daily;

import com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Session;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.IntervalException;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.scheduling.rules.AttendanceRules;
import com.rpb.reservation.workforce.scheduling.rules.AttendanceRules.AnomalyRule;
import com.rpb.reservation.workforce.leave.LeaveDtos.Leave;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DailyBonusQualificationTest {
    final Instant start=Instant.parse("2026-10-30T01:00:00Z"),end=start.plusSeconds(28800);
    final UUID employee=UUID.randomUUID(),store=UUID.randomUUID(),company=UUID.randomUUID();
    AttendanceRules rules(boolean absenceBonus,boolean leaveBonus){return new AttendanceRules(1,List.of(),0,0,0,"full",List.of(new AnomalyRule("LATE","Late",true,true),new AnomalyRule("ABSENCE","Absence",true,absenceBonus),new AnomalyRule("LEAVE","Leave",true,leaveBonus)),null,null);}
    ApprovalSource source(AttendanceRules rules,Instant in,Instant out,List<Leave> leaves){
        var shift=new Shift(UUID.randomUUID(),"Day","09:00","17:00",false,"none",0,0,0,0,false,true,1,rules);
        var s=new Schedule(UUID.randomUUID(),employee,"Test","E1",shift.id(),LocalDate.of(2026,10,30),start,end,"Asia/Singapore","scheduled",shift,UUID.randomUUID(),1);
        var session=in==null?null:new Session(UUID.randomUUID(),s.id(),employee,store,in,out,null,List.of(),false,1);
        return new ApprovalSource(s,session,List.of(),leaves,List.of());
    }
    ApprovalRow row(ApprovalSource source,List<Finding> findings,String status){return new ApprovalRow(source.schedule(),store,28800,0,0,List.of(),"fp",null,false,findings,status,List.of());}
    IntervalException absence(Instant from,Instant to,String status){return new IntervalException(UUID.randomUUID(),employee,company,store,LocalDate.of(2026,10,30),"absence",null,"Absence",false,from,to,"reason",status,UUID.randomUUID(),UUID.randomUUID(),"review",null,1,start);}
    String qualify(String mode,ApprovalSource source,ApprovalRow row,List<IntervalException> exceptions){return DailyBonusQualification.source(mode,source,row,exceptions,end.plusSeconds(1));}
    @Test void intervalLeaveBridgePreservesComplianceAndMedicalEvidence(){
        var original=source(rules(false,false),start,end,List.of());
        var compliance=new com.rpb.reservation.workforce.compliance.ComplianceEvidence.Terms(null,null);
        var enriched=new ApprovalSource(original.schedule(),original.session(),original.corrections(),original.leaves(),original.calendar(),original.clockPolicy(),compliance);
        var leave=new IntervalException(UUID.randomUUID(),employee,company,store,LocalDate.of(2026,10,30),"leave",UUID.randomUUID(),"Medical",true,start,end,"reason","approved",UUID.randomUUID(),UUID.randomUUID(),"review",null,1,start,"MC-TEST");
        var bridged=AttendanceDailyService.withIntervalLeaves(enriched,List.of(leave));
        assertThat(bridged.compliance()).isSameAs(compliance);
        assertThat(bridged.leaves()).singleElement().satisfies(l->{assertThat(l.medicalReference()).isEqualTo("MC-TEST");assertThat(l.scheduleId()).isEqualTo(original.schedule().id());});
    }
    @Test void completeSystemClockNeedsNoAdditionalHumanBonusReview(){var s=source(rules(true,true),start,end,List.of());assertThat(qualify("clock",s,row(s,List.of(),"pending"),List.of())).isEqualTo("eligible");assertThat(DailyBonusQualification.aggregate("clock","system_valid",List.of(),List.of("eligible"))).isEqualTo("eligible");}
    @Test void managerPendingUnknownAndMissingStayPending(){var s=source(rules(true,true),null,null,List.of());assertThat(qualify("clock",s,row(s,List.of(),"pending"),List.of())).isEqualTo("pending");assertThat(DailyBonusQualification.aggregate("clock","pending",List.of("WORKFORCE_MANAGER_ADMIN_REVIEW"),List.of("eligible"))).isEqualTo("pending");assertThat(DailyBonusQualification.aggregate("free","not_applicable",List.of(),List.of())).isEqualTo("pending");assertThat(DailyBonusQualification.aggregate("default_normal","system_valid",List.of(),List.of())).isEqualTo("pending");}
    @Test void currentReviewedAndUnresolvedFindingsRetainTheirMeaning(){var s=source(rules(true,true),start.plusSeconds(600),end,List.of());assertThat(qualify("clock",s,row(s,List.of(new Finding("LATE","Late",600,"pending",true)),"pending"),List.of())).isEqualTo("pending");assertThat(qualify("clock",s,row(s,List.of(new Finding("LATE","Late",600,"excused",true)),"eligible"),List.of())).isEqualTo("eligible");assertThat(qualify("clock",s,row(s,List.of(new Finding("LATE","Late",600,"confirmed",true)),"ineligible"),List.of())).isEqualTo("ineligible");}
    @Test void approvedAbsenceUsesExplicitAbsenceRuleForLateAndWholeShift(){for(boolean affects:List.of(true,false)){var s=source(rules(affects,false),start.plusSeconds(600),end,List.of());assertThat(qualify("clock",s,row(s,List.of(new Finding("LATE","Late",600,"pending",true)),"pending"),List.of(absence(start,start.plusSeconds(600),"approved")))).isEqualTo(affects?"ineligible":"eligible");var missing=source(rules(affects,false),null,null,List.of());assertThat(qualify("clock",missing,row(missing,List.of(new Finding("MISSING","Missing",28800,"pending",true)),"pending"),List.of(absence(start,end,"approved")))).isEqualTo(affects?"ineligible":"eligible");}}
    @Test void incompleteOrUnconfiguredAbsenceCannotExcuseMissingTime(){var s=source(rules(false,false),start.plusSeconds(600),end,List.of());var row=row(s,List.of(new Finding("LATE","Late",600,"pending",true)),"pending");assertThat(qualify("clock",s,row,List.of(absence(start,start.plusSeconds(300),"approved")))).isEqualTo("pending");assertThat(qualify("clock",s,row,List.of(absence(start,start.plusSeconds(600),"pending")))).isEqualTo("pending");var absentRule=new AttendanceRules(1,List.of(),0,0,0,"full",List.of(),null,null);var noRule=source(absentRule,null,null,List.of());assertThat(qualify("default_normal",noRule,null,List.of(absence(start,end,"approved")))).isEqualTo("pending");}
    @Test void defaultNormalUsesLeaveAndAbsenceRulesWithoutManufacturingMissingPunches(){var s=source(rules(false,false),null,null,List.of());assertThat(qualify("default_normal",s,null,List.of())).isEqualTo("eligible");var noRules=source(null,null,null,List.of());assertThat(qualify("default_normal",noRules,null,List.of())).isEqualTo("pending");var leave=new Leave(UUID.randomUUID(),UUID.randomUUID(),employee,"Test",UUID.randomUUID(),"Leave",true,start,end,"reason","approved",UUID.randomUUID(),UUID.randomUUID(),"review",1,start);var withLeave=source(rules(false,true),null,null,List.of(leave));assertThat(qualify("default_normal",withLeave,null,List.of())).isEqualTo("ineligible");}
}
