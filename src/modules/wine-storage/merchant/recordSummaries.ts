import type {Application,Event,Item,Intent} from '../types'

export function receiptRows(application:Application){
 if(!application.confirmation)return application.lines
 return application.confirmation.lines.map((line,index)=>{const original=application.lines.find(l=>l.id===line.lineId),named=!!line.nameZh?.trim()||!!line.nameEn?.trim();return {...line,id:`${line.lineId||'receipt'}:${index}`,nameZh:named?(line.nameZh||''):(original?.nameZh||''),nameEn:named?(line.nameEn||''):(original?.nameEn||'')}})
}
export function depositFacts(application:Application,items:Item[],events:Event[]){
 const related=events.filter(e=>e.sourceId===application.id),held=items.filter(i=>i.applicationId===application.id)
 const receipt=related.find(e=>e.action==='received'),registration=related.find(e=>e.action==='staff.registered')
 const at=application.confirmation?(receipt?.createdAt||held.find(i=>i.receivedAt)?.receivedAt):null
 const requested=application.lines.reduce((n,l)=>n+l.quantity,0),received=application.confirmation?application.confirmation.lines.reduce((n,l)=>n+l.quantity,0):null
 return{requested,received,different:received!==null&&received!==requested,staff:!!registration,at:at||application.createdAt,timeKind:at?'received':'registered',operator:registration?.actorName?.trim()||receipt?.actorName?.trim()||'',custodians:[...new Set(held.map(i=>i.snapshot.custodian?.name?.trim()).filter((v):v is string=>!!v))]}
}
export function collectionTime(intent:Intent,events:Event[]):{kind:'completed'|'cancelled'|'created'|'unknown';at:string|null}{
 if(intent.status==='completed'&&intent.completedAt)return{kind:'completed',at:intent.completedAt}
 if(intent.status==='cancelled'){const event=events.find(e=>e.sourceId===intent.id&&e.action==='intent.cancelled');if(event)return{kind:'cancelled',at:event.createdAt}}
 const created=events.find(e=>e.sourceId===intent.id&&e.action==='intent.created')
 return created?{kind:'created',at:created.createdAt}:{kind:'unknown',at:null}
}
export function collectionStatus(intent:Pick<Intent,'status'|'expiresAt'>,now=Date.now()){
 return ['authorized','awaiting_authorization'].includes(intent.status)&&Date.parse(intent.expiresAt)<=now?'expired':intent.status
}
