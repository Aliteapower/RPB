package com.rpb.reservation.workforce.attendance.daily;
import org.springframework.context.annotation.Configuration;import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
public class AttendanceNormalEvaluationJob {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AttendanceNormalEvaluationJob.class);
 private final AttendanceNormalEvaluationRepository repository;private final AttendanceNormalEvaluationService service;
 public AttendanceNormalEvaluationJob(AttendanceNormalEvaluationRepository repository,AttendanceNormalEvaluationService service){this.repository=repository;this.service=service;}
 @Scheduled(initialDelay=60000,fixedDelay=60000)
 public void evaluate(){String cursor="";while(true){var page=repository.scopes(cursor,100);if(page.isEmpty())return;for(var scope:page){try{service.evaluate(scope);}catch(RuntimeException error){log.error("Attendance evaluation failed tenant {} store {} employee {}",scope.tenant(),scope.store(),scope.employee(),error);}cursor=scope.cursor();}if(page.size()<100)return;}}
}
