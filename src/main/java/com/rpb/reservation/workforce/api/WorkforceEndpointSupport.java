package com.rpb.reservation.workforce.api;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
/** Shared request security/error contract, without business/persistence responsibilities. */
public abstract class WorkforceEndpointSupport {
    private final CurrentActorProvider actors;
    protected WorkforceEndpointSupport(CurrentActorProvider actors){this.actors=actors;}
    @ModelAttribute public void protect(HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","private, no-store");
        if(!"GET".equals(request.getMethod())){String site=request.getHeader("Sec-Fetch-Site");if(!"1".equals(request.getHeader("X-RPB-Workforce"))||"cross-site".equals(site)||"same-site".equals(site))throw WorkforceException.forbidden();}
    }
    protected CurrentActor actor(){return actors.currentActor().orElseThrow(()->new WorkforceException("WORKFORCE_UNAUTHENTICATED",401));}
    @ExceptionHandler(WorkforceException.class) public ResponseEntity<Map<String,Object>> error(WorkforceException e){return failure(e.status(),e.code());}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) public ResponseEntity<Map<String,Object>> invalid(Exception e){return failure(400,"WORKFORCE_INVALID");}
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<Map<String,Object>> conflict(Exception e){return failure(409,"WORKFORCE_CONFLICT");}
    @ExceptionHandler({DataAccessException.class,IllegalStateException.class}) public ResponseEntity<Map<String,Object>> persistence(Exception e,HttpServletRequest request){
        String requestId=WorkforceFailureDiagnostics.record(e,request);
        return ResponseEntity.status(500).header("Cache-Control","private, no-store").header("X-RPB-Request-Id",requestId).body(Map.of("success",false,"code","WORKFORCE_PERSISTENCE_ERROR"));
    }
    private ResponseEntity<Map<String,Object>> failure(int status,String code){return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(Map.of("success",false,"code",code));}
}
