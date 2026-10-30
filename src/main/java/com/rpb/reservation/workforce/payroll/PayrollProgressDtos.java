package com.rpb.reservation.workforce.payroll;

import java.util.List;
import java.util.UUID;
import com.rpb.reservation.workforce.payroll.PayrollDtos.BatchSummary;

/** Read projections and bounded bulk command contracts; amounts remain decimal strings. */
public final class PayrollProgressDtos {
    private PayrollProgressDtos() {}
    public record ChangeReport(UUID batchId,long batchVersion,String status,boolean sourceChanged,
            Comparison lastCalculation,Comparison previousMonth,List<PendingChange> pending,int recalculated,int reused) {}
    public record Comparison(boolean available,String baselineMonth,Long baselineVersion,String totalGrossDelta,
            String totalNetDelta,String totalEmployerCostDelta,List<LineChange> lines) {}
    public record LineChange(UUID employeeId,String employeeName,String kind,List<String> reasons,String basicPayDelta,
            String grossPayDelta,String netPayDelta,String employeeCpfDelta,String employerCostDelta) {}
    public record PendingChange(UUID employeeId,String employeeName,String reason) {}
    public record BulkEntry(UUID batchId,Long expectedVersion) {}
    public record BulkWrite(String action,String note,List<BulkEntry> entries) {}
    public record BulkItem(UUID batchId,String status,String code,BatchSummary batch) {}
    public record BulkResult(UUID commandId,String action,List<BulkItem> results) {}
}
