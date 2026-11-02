import type {PayslipSummary,Payslip} from './payrollTypes'
import type {SegmentSlip} from './segmentPayroll'
import type {SupplementalPayslip} from './supplemental'
import type {Attendance,AttendanceDetail,Option} from './types'
import type {MonthPage} from './attendanceMonth'
export interface HistoryContext {employeeManage:boolean;self:boolean;companies:Option[]}
export interface HistoryBatch {id:string;companyName:string;month:string;status:string;employees:Option[]}
export interface HistoryData {slips:PayslipSummary[];segments:SegmentSlip[];supplements:SupplementalPayslip[];batches:HistoryBatch[];attendance:Attendance[];month:MonthPage|null;payslip:Payslip|null;detail:AttendanceDetail|null}
export function emptyHistory():HistoryData{return {slips:[],segments:[],supplements:[],batches:[],attendance:[],month:null,payslip:null,detail:null}}
export function defaultHistoryTab(context:HistoryContext|null){return context?.self?'mine':context?.employeeManage?'attendance':context?.companies.length?'managed':'attendance'}
/** UI routing only. The server independently enforces ownership and historical access. */
export function historyQuery(query:Record<string,unknown>,patch:Record<string,string|number|undefined>={}){return Object.fromEntries(Object.entries({...query,...patch,history:'1'}).filter(([,v])=>v!==undefined&&v!==null&&v!=='')) as Record<string,string>}
