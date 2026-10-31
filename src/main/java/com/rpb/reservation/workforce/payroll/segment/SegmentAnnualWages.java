package com.rpb.reservation.workforce.payroll.segment;

import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.PayrollSources;
import com.rpb.reservation.workforce.statutory.annual.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Final-range encashments use the existing annual wage allocator and unique event registry. */
final class SegmentAnnualWages {
    private final JdbcTemplate jdbc;
    private final WorkforceJson json;
    private final Clock clock;
    SegmentAnnualWages(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}
    void confirm(SegmentRepository.State s,PayrollSources.Capture capture) {
        var line=s.calculation().cumulative().lines().getFirst();
        var events=AnnualWageRepository.events(line.input(),"segment:"+s.id());
        if(events.isEmpty())return;
        var annual=new AnnualWageRepository(jdbc,json,clock);
        annual.lock(s.tenant(),s.company(),s.employee(),s.month().getYear());
        annual.subject(s.tenant(),s.company(),s.employee(),s.month().getYear());
        for(var event:events) {
            boolean used=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_wage_event_consumptions where tenant_id=? and company_id=? and event_id=? and active)",Boolean.class,s.tenant(),s.company(),event.evidence().eventId()));
            if(used)throw new WorkforceException("WAGE_EVENT_ALREADY_CONSUMED",409);
            var existing=jdbc.query("select snapshot from workforce_wage_events where tenant_id=? and company_id=? and event_id=?",(r,n)->json.read(r.getString(1),AnnualWageDtos.WageEvent.class),s.tenant(),s.company(),event.evidence().eventId());
            if(!existing.isEmpty())throw new WorkforceException("WAGE_EVENT_ALREADY_CONSUMED",409);
            jdbc.update("insert into workforce_wage_events(tenant_id,company_id,event_id,employee_id,year,snapshot) values(?,?,?,?,?,?::jsonb)",s.tenant(),s.company(),event.evidence().eventId(),s.employee(),s.month().getYear(),json.write(event));
            jdbc.update("insert into workforce_wage_event_consumptions(tenant_id,company_id,employee_id,event_id,segment_id) values(?,?,?,?,?)",s.tenant(),s.company(),s.employee(),event.evidence().eventId(),s.id());
        }
        var source=capture.lines().getFirst();
        if(source.annual()!=null) {
            var context=AnnualWageRepository.current(source.annual(),s.month(),new BigDecimal(line.grossPay()).subtract(AnnualWageRepository.aw(events)),line.profile().cpf(),s.calculation().cumulative().statutoryPolicy(),events);
            var snapshot=new AnnualWageDtos.FrozenAssessment(s.id(),s.employee(),s.calculation().fingerprint(),context,new AnnualWageCalculator().assess(context));
            jdbc.update("insert into workforce_segment_aw_assessments(tenant_id,company_id,employee_id,year,segment_id,snapshot) values(?,?,?,?,?,?::jsonb)",s.tenant(),s.company(),s.employee(),s.month().getYear(),s.id(),json.write(snapshot));
        }
    }
}
