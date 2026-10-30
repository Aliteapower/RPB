package com.rpb.reservation.workforce.approval;
import com.rpb.reservation.workforce.domain.WorkforceException;
/** Optional text snapshots; no approval decision or source validation belongs here. */
public final class ApprovalNotes {
    private ApprovalNotes(){}
    public static String normalize(String value){String text=value==null?"":value.trim();if(text.length()>500)throw WorkforceException.invalid();return text;}
}
