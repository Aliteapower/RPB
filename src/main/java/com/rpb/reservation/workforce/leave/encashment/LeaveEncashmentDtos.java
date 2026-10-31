package com.rpb.reservation.workforce.leave.encashment;

import com.rpb.reservation.workforce.payroll.PayrollDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class LeaveEncashmentDtos {
    private LeaveEncashmentDtos() {}
    /** Every recurring amount requires explicit gross-rate classification; labels are not evidence. */
    public record Context(UUID employeeId,String employeeName,List<com.rpb.reservation.workforce.leave.account.LeaveAccountDtos.Balance> balances,PayrollDtos.PayProfile profile) {}
    public record Classification(UUID itemId, boolean included, String reason) {}
    public record Write(UUID employeeId, UUID accountId, BigDecimal quantity, LocalDate valuationOn,
                        LocalDate payableOn, String purpose, String evidence, List<Classification> classifications,
                        String route, String originMonth, Valuation expectedValuation) { public Write(UUID employeeId,UUID accountId,BigDecimal quantity,LocalDate valuationOn,LocalDate payableOn,String purpose,String evidence,List<Classification> classifications,String route,String originMonth){this(employeeId,accountId,quantity,valuationOn,payableOn,purpose,evidence,classifications,route,originMonth,null);} }
    public record Quote(Valuation valuation,BigDecimal available,List<String> issues) {}
    public record Valuation(UUID profileId, long profileVersion, UUID contractId, UUID policyId, long policyVersion,
                            String basis, String unit, String unitRate, String formula,
                            List<Classification> classifications, String amount) {}
    public record Document(UUID id, UUID employeeId, UUID companyId, UUID accountId, UUID creatorId,
                           Write request, Valuation valuation, PayrollDtos.Item item, String status,
                           UUID requestId, UUID consumedBy) {}
}
