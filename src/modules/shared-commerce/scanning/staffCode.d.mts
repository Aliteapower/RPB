export type StaffScanCode={kind:'card'|'storage'|'identity'|'payment';code:string}
export function staffScanCode(value:string,origin:string,store?:string):StaffScanCode|null;
