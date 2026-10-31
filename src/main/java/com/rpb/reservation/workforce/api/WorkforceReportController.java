package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.reporting.*;
import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceReportController extends WorkforceEndpointSupport {
    private final PayrollReportService reports;
    private final PayrollReconciliationService reconciliation;
    public WorkforceReportController(CurrentActorProvider actors,PayrollReportService reports,PayrollReconciliationService reconciliation) {
        super(actors); this.reports=reports; this.reconciliation=reconciliation;
    }
    @GetMapping("/payroll-report") public Report report(@PathVariable UUID storeId,@RequestParam String month,@RequestParam List<UUID> companyIds) {
        return reports.read(actor(),storeId,month,companyIds);
    }
    @GetMapping("/payroll-report/export") public ResponseEntity<byte[]> export(@PathVariable UUID storeId,@RequestParam String month,@RequestParam List<UUID> companyIds,@RequestParam String kind,@RequestParam String fingerprint) {
        var csv=reports.export(actor(),storeId,month,companyIds,kind,fingerprint);
        return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\"workforce-"+kind+"-"+month+".csv\"").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.getBytes(StandardCharsets.UTF_8));
    }
    @PutMapping("/payroll-reconciliation") public Comparison compare(@PathVariable UUID storeId,@RequestBody CompareWrite c) {
        return reconciliation.compare(actor(),storeId,c);
    }
}
