import {buildTenantEntries} from '../../tenant-entry-links/catalog'
import type {AuthStoreAccess} from '../../../types/auth'
export interface GuideEntries {storeId:string;storeName:string;staff:string;customer:string}
/** Identity is supplied by the authenticated store directory; commercial access by App Gate. */
export function guideEntries(store:AuthStoreAccess|undefined,tenantId:string|null|undefined,businessAllowed:boolean,location:Pick<Location,'protocol'|'hostname'|'port'|'origin'>):GuideEntries|null{
 if(!businessAllowed||!store||store.status!=='active'||!tenantId||store.tenantId!==tenantId)return null
 const entries=buildTenantEntries({success:true,identity:{tenantId:store.tenantId,tenantCode:store.tenantCode,tenantName:'',tenantStatus:'active',storeId:store.storeId,storeCode:store.storeCode,storeName:store.storeName,storeStatus:store.status},publicBookingEnabled:false,products:[{appKey:'wine_storage',available:true,denialReason:null}]},location)
 const staff=entries.find(e=>e.key==='wineStaff'&&e.available),customer=entries.find(e=>e.key==='wineCustomer'&&e.available)
 if(!staff||!customer)return null
 return {storeId:store.storeId,storeName:store.storeName,staff:staff.url,customer:customer.url}
}
