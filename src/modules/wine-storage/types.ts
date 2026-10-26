export interface Rule {id:string;categoryId:string;name:string;months:number;version:number}
export interface Product {id:string;categoryId:string;code:string;nameZh:string;nameEn:string}
export interface Category {id:string;nameZh:string;nameEn:string}
export interface Line {id:string;productId:string|null;nameZh:string;nameEn:string;quantity:number;opened:boolean;source:string;photos:string[];note:string}
export interface Application {id:string;customerId:string;status:string;lines:Line[];confirmation:{lines:ReceiptLine[];reason:string}|null;reason:string;createdAt:string}
export interface ReceiptLine {lineId:string;productId:string|null;categoryId:string;nameZh:string;nameEn:string;quantity:number;opened:boolean;source:string;ruleId:string;photos:string[];note:string;location:string;label:string;storageLocationId?:string|null;storageLocation?:StorageLocationRef|null}
export interface Item {id:string;applicationId:string;customerId:string;snapshot:{productId:string;code:string;nameZh:string;nameEn:string;categoryId:string;rule:Rule;timezone:string;note:string;applicationPhotos:string[];receiptPhotos:string[];custodian?:CustodianRef|null;storageLocation?:StorageLocationRef|null};quantity:number;remaining:number;opened:boolean;source:string;location:string;label:string;receivedAt:string;expiresAt:string;status:string}
export interface Selection {itemId:string;quantity:number}
export interface Intent {id:string;customerId:string;origin:string;status:string;expiresAt:string;completedAt:string|null;selections:Selection[];code:string|null}
export interface Event {id:string;sourceId:string;action:string;actorId:string;details:Record<string,unknown>;createdAt:string}
export interface Home {timezone:string;storeName:string;stores:{id:string;name:string}[];newBusinessEnabled:boolean;permissions:string[];rules:Rule[];categories:Category[];applications:Application[];items:Item[];intents:Intent[];events:Event[]}

export interface CustodianRef {id:string;name:string;kind:string}
export interface Custodian extends CustodianRef {enabled:boolean;version:number}
export interface PendingApplication {application:Application;customer:{id:string;displayName:string;phone:string}}

export interface StorageLocationRef {id:string;name:string}
export interface StorageLocation extends StorageLocationRef {defaultLocation:boolean;version:number}
