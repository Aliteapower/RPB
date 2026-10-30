import {WorkforceApiError} from './api'
export async function fetchReportCsv(store:string,query:string):Promise<Blob>{
 const response=await fetch(`/api/v1/stores/${encodeURIComponent(store)}/workforce/payroll-report/export?${query}`,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/csv'}})
 if(!response.ok){const error=await response.json().catch(()=>({}));throw new WorkforceApiError(response.status,error.code||'WORKFORCE_PERSISTENCE_ERROR')}
 if(!response.headers.get('Content-Type')?.startsWith('text/csv'))throw new WorkforceApiError(500,'WORKFORCE_PERSISTENCE_ERROR')
 return response.blob()
}
export function saveReportCsv(blob:Blob,filename:string){
 const url=URL.createObjectURL(blob),anchor=document.createElement('a')
 try{anchor.href=url;anchor.download=filename;document.body.appendChild(anchor);anchor.click()}
 finally{anchor.remove();URL.revokeObjectURL(url)}
}
