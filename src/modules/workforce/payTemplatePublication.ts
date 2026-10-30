import type {PayRule} from './payrollTypes'
export interface TemplateMaster {id:string;version:number;name:string;rule:PayRule;companyIds:string[];storeIds:string[];includeFutureStores:boolean}
export interface TemplateRelease {masterId:string;version:number;draftVersion:number;name:string;rule:PayRule;companyIds:string[];storeIds:string[];includeFutureStores:boolean;existingStoreIds:string[];publishedAt:string;publishedBy:string}
export interface TemplateAdoption {id:string;masterId:string;releaseVersion:number;companyId:string;storeId:string;storeVersion:number;materializedTemplateId:string;name:string;rule:PayRule;enabledAt:string;enabledBy:string}
export interface ScheduledAdoption {adoption:TemplateAdoption;effectiveDate:string}
export interface TemplateLibraryEntry {masterId:string;name:string;latestReleaseVersion:number;adoption:TemplateAdoption|null;state:'available'|'enabled'|'updateAvailable';scheduledAdoptions?:ScheduledAdoption[]}
export interface TemplatePreview {release:TemplateRelease;adoption:TemplateAdoption|null;updateAvailable:boolean;scheduledAdoptions?:ScheduledAdoption[]}
export interface TemplateDraftForm {id:string;expectedVersion:number;name:string;rule:PayRule;companyIds:string[];storeIds:string[];includeFutureStores:boolean}
