import type {ClockSite,WorkPoint,ClockLocation} from './workMode'
export interface ClockSiteSettings extends ClockSite {storeId:string;storeCode:string;storeName:string;operatingEntityId:string|null;operatingEntityName:string|null;address:string|null;storeVersion:number;canEditAddress:boolean}
export interface ClockSiteWrite {expectedVersion:number;expectedStoreVersion:number;address:string;point:WorkPoint}
export interface ClockSiteCheck {distanceMeters:number|null;radiusMeters:number;accuracyMeters:number|null;allowed:boolean;code:string|null;siteVersion:number;checkedAt:string}
export function distanceMeters(a:Pick<ClockLocation,'latitude'|'longitude'>,b:WorkPoint){const rad=(n:number)=>n*Math.PI/180;const x=Math.sin(rad(b.latitude-a.latitude)/2)**2+Math.cos(rad(a.latitude))*Math.cos(rad(b.latitude))*Math.sin(rad(b.longitude-a.longitude)/2)**2;return 6371000*2*Math.atan2(Math.sqrt(Math.min(1,x)),Math.sqrt(Math.max(0,1-x)))}
