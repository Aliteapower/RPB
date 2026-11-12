export type ScanDevice = 'camera' | 'hardware'
export interface ScanDeviceScope {tenantId:string;storeId:string}
type Reader = Pick<Storage,'getItem'>
type Writer = Pick<Storage,'setItem'>
export function scanDeviceKey(scope:ScanDeviceScope):string|null {
  return scope.tenantId&&scope.storeId?`rpb.scan-device.v1:${encodeURIComponent(scope.tenantId)}:${encodeURIComponent(scope.storeId)}`:null
}
export function readScanDevice(scope:ScanDeviceScope,storage:Reader):ScanDevice {
  const key=scanDeviceKey(scope)
  try{return key&&storage.getItem(key)==='hardware'?'hardware':'camera'}catch{return 'camera'}
}
export function writeScanDevice(scope:ScanDeviceScope,mode:ScanDevice,storage:Writer):boolean {
  const key=scanDeviceKey(scope)
  if(!key||!['camera','hardware'].includes(mode))return false
  try{storage.setItem(key,mode);return true}catch{return false}
}
