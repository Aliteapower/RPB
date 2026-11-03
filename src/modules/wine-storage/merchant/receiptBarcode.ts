import type {Product,ReceiptLine} from '../types'

/** Only identical, untouched whole-bottle drafts can be combined. No inventory command. */
export function addScannedBottle(lines:ReceiptLine[],product:Product,ruleId:string,locationId:string|null){
  const merge=lines.find(line=>line.productId===product.id&&!line.opened&&line.source==='customer'&&line.ruleId===ruleId&&!line.photos.length&&!line.note&&!line.location&&!line.label&&(line.storageLocationId??null)===locationId)
  if(merge){
    if(!Number.isInteger(merge.quantity)||merge.quantity<1||merge.quantity>=10000)return false
    merge.quantity++;return true
  }
  const blank=lines.find(line=>!line.productId&&!line.nameZh&&!line.nameEn&&!line.categoryId&&!line.opened&&line.source==='customer'&&line.quantity===1&&!line.photos.length&&!line.note&&!line.location&&!line.label&&(line.storageLocationId??null)===locationId)
  if(!blank&&lines.length>=50)return false
  const value:ReceiptLine={lineId:blank?.lineId??crypto.randomUUID(),productId:product.id,categoryId:product.categoryId,nameZh:product.nameZh,nameEn:product.nameEn,quantity:1,opened:false,source:'customer',ruleId,photos:[],note:'',location:'',label:'',storageLocationId:locationId}
  if(blank)Object.assign(blank,value);else lines.push(value)
  return true
}
