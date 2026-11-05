import type {Application,Intent,Item,Event} from '../types'
export interface ReportRow {id:string;kind:'deposit'|'pickup';customerId:string;customerName:string|null;customerPhone:string|null;status:string;occurredAt:string;quantity:number;winesZh:string|null;winesEn:string|null;locations:string|null}
export interface ReportPage {items:ReportRow[];summary:{recordCount:number;completedCount:number;bottleCount:number};page:number;size:number;hasMore:boolean}
export interface ReportDetail {application:Application|null;intent:Intent|null;items:Item[];events:Event[]}
