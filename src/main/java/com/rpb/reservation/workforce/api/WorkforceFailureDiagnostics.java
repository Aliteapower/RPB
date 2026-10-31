package com.rpb.reservation.workforce.api;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;

/** Deliberately excludes exception messages, stack traces, request values and SQL binds. */
final class WorkforceFailureDiagnostics {
    private static final Logger LOG=LoggerFactory.getLogger(WorkforceFailureDiagnostics.class);
    private WorkforceFailureDiagnostics() {}
    static String record(Exception failure,HttpServletRequest request) {
        String id=UUID.randomUUID().toString();
        Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route=matched instanceof String pattern?pattern:"unmatched";
        String method=Set.of("GET","PUT","POST","DELETE","PATCH","HEAD","OPTIONS").contains(request.getMethod())?request.getMethod():"OTHER";
        String state="unknown";Throwable cause=failure;
        for(int depth=0;cause!=null&&depth<10;depth++,cause=cause.getCause()) {
            if(cause instanceof SQLException sql) {
                String value=sql.getSQLState();
                if(value!=null&&value.matches("[A-Z0-9]{5}"))state=value;
                break;
            }
        }
        LOG.error("workforce_request_failed requestId={} method={} route={} failureType={} sqlState={}",id,method,route,failure.getClass().getSimpleName(),state);
        return id;
    }
}
