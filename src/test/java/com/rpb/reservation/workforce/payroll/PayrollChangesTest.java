package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayrollChangesTest {
    UUID employee=UUID.randomUUID();
    PayrollLine line(UUID id,String gross,String net,String sdl){return new PayrollLine(id,"Employee","E1",null,null,0,0,0,0,0,gross,"0","0","2.00","0",gross,"1.00","3.00",sdl,"4.00",net,"none",List.of(),List.of(),List.of());}
    PayrollSnapshot snapshot(List<PayrollLine> lines,String gross,String net,String cost){return new PayrollSnapshot("fingerprint","Company","2026-10","v1",lines,List.of(),gross,net,"1","3","2",cost,Instant.EPOCH);}
    @Test void missingEitherSideNeverProducesFakeZeroSalary(){
        var snapshot=snapshot(List.of(),"0","0","0");
        assertThat(PayrollChanges.compare(null,1L,snapshot).available()).isFalse();
        assertThat(PayrollChanges.compare(snapshot,1L,null).totalGrossDelta()).isNull();
    }
    @Test void signedDeltasUseDecimalArithmeticAndRawEmployeeSdl(){
        var before=snapshot(List.of(line(employee,"0.30","0.20","0.01")),"0.30","0.20","9.31");
        var after=snapshot(List.of(line(employee,"0.10","-0.10","0.02")),"0.10","-0.10","9.12");
        var comparison=PayrollChanges.compare(before,7L,after);
        assertThat(comparison.baselineVersion()).isEqualTo(7L);
        assertThat(comparison.totalGrossDelta()).isEqualTo("-0.20");
        assertThat(comparison.lines().getFirst().employerCostDelta()).isEqualTo("-0.19");
        assertThat(comparison.lines().getFirst().netPayDelta()).isEqualTo("-0.30");
    }
    @Test void additionsAndRemovalsHaveExplicitKindsAndUnaffectedLinesAreOmitted(){
        UUID added=UUID.randomUUID();var original=line(employee,"100","90","0.25");
        var before=snapshot(List.of(original),"100","90","109.25");
        assertThat(PayrollChanges.compare(before,1L,before).lines()).isEmpty();
        var after=snapshot(List.of(line(added,"200","180","0.50")),"200","180","209.50");
        var changes=PayrollChanges.compare(before,1L,after).lines();
        assertThat(changes).extracting(PayrollProgressDtos.LineChange::kind).containsExactly("added","removed");
        assertThat(changes.get(1).grossPayDelta()).isEqualTo("-100");
    }
}
