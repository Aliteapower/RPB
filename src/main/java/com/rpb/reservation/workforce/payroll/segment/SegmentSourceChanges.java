package com.rpb.reservation.workforce.payroll.segment;
import com.rpb.reservation.workforce.payroll.PayrollSources;
import java.util.Objects;
/** Current facts are an alert only; they never overwrite formal wages. */
public final class SegmentSourceChanges {
 private SegmentSourceChanges(){}
 public static boolean changed(SegmentRepository.State saved,PayrollSources.CapturedLine current){if(current==null||current.profile()==null)return true;var frozen=saved.calculation().cumulative().lines().getFirst();return !frozen.sources().equals(current.sources().stream().filter(r->!r.workDate().isAfter(saved.cutoff())).toList())||!frozen.profile().id().equals(current.profile().id())||frozen.profile().version()!=current.profile().version()||!Objects.equals(frozen.profile().cpf(),current.profile().cpf())||!frozen.input().items().equals(current.input().items());}
}
