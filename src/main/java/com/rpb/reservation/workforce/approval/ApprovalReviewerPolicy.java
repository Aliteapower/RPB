package com.rpb.reservation.workforce.approval;
import java.util.UUID;
/** One review-authority policy shared by single approvals and the workbench. */
public final class ApprovalReviewerPolicy {
 private ApprovalReviewerPolicy(){}
 public static String block(boolean administrator,boolean manager,UUID actor,UUID employeeAccount,boolean subjectManager){
  if(!manager)return "WORKFORCE_FORBIDDEN";
  if(actor.equals(employeeAccount))return "WORKFORCE_SELF_REVIEW";
  if(subjectManager&&!administrator)return "WORKFORCE_MANAGER_ADMIN_REVIEW";
  return null;
 }
}
