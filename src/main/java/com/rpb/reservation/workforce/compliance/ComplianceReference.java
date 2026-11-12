package com.rpb.reservation.workforce.compliance;
import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
/** Reference material only. Operators must review and publish a stored policy version. */
public final class ComplianceReference {
 private ComplianceReference(){}
 public static Reference singapore(){return new Reference("新加坡合同、休假与工时参考（2026核对）",new Rules("sg-leave-hours-v1",List.of(7,8,9,10,11,12,13,14),3,List.of(new SickTier(3,5,15),new SickTier(4,8,30),new SickTier(5,11,45),new SickTier(6,14,60)),n("35"),n("44"),n("4500"),n("2600"),n("1.5"),12,1,12,72,6,45,n("9"),n("8"),5,0,false,false),List.of(
 source("part-time","兼职定义与合同","part-time-employment/who-is-a-part-time-employee","以合同每周工时判定，不按月/日/时薪标签判断；合同保留基本/总小时率及工作安排。"),
 source("hours","工时、加班及休息","hours-of-work-overtime-and-rest-days","按工作类别、基本工资及实际职责判断Part IV；轮班和特殊安排须另核对，不以排班长度消除加班。"),
 source("pt-overtime","兼职额外工时","part-time-employment/overtime","超过本人但未超同类全职标准与超过全职标准分别计算。"),
 source("annual","年假资格及服务年额度","leave/annual-leave/eligibility-and-entitlement","满服务资格后享有年假，按服务年限及完整服务月折算；不在1月重置资历。"),
 source("carry","年假结转","leave/annual-leave/special-situations","Part IV法定未休年假结转随后12个月，企业额外部分按合同；不是所有余额年底清零。"),
 source("sick","门诊和住院病假","leave/sick-leave/eligibility-and-entitlement","按服务月阶梯；住院总上限包含门诊使用部分，须有适用医疗证明。"),
 source("pt-leave","兼职假期与折现限制","part-time-employment/leave","按比较工时授予小时额度；病假不能折现；部分每周至少5天且30至34小时安排不能并入小时工资折现年假。"),
 source("gross","休假工资与折现基数","salary/monthly-and-daily-salary","gross rate不等于工资单总应发，住房/餐食/交通等须按官方定义区分；不乘加班倍数。"),
 source("termination","离职未休年假","termination-of-employment/termination-with-notice","离职结算按未休年假与最后适用gross rate核对；失当解雇等例外须有依据。"),
 new Source("cpf-leave","CPF年假折现","https://www.cpf.gov.sg/service/article/are-cpf-contributions-payable-on-encashment-of-leave","现金年假折现属于须核对CPF的工资，另按OW/AW及年度资料判断。",LocalDate.of(2026,10,31))));}
 private static Source source(String code,String title,String path,String summary){return new Source(code,title,"https://www.mom.gov.sg/employment-practices/"+path,summary,LocalDate.of(2026,10,31));}
 private static BigDecimal n(String s){return new BigDecimal(s);}
}
