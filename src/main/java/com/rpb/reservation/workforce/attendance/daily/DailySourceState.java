package com.rpb.reservation.workforce.attendance.daily;

import java.util.Set;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay;

/** Whether a day supplies the attendance basis required by its pay mode. */
public final class DailySourceState {
    private DailySourceState() {}
    public static boolean payrollReady(EffectiveDay day) {
        // Free fixed pay never depends on leave/clock review. Leave records remain independently visible.
        if (Set.of("free","contract").contains(day.mode())) return true;
        return Set.of("system_valid", "human_valid", "rest", "not_applicable").contains(day.state()) && day.issues().isEmpty();
    }
}
