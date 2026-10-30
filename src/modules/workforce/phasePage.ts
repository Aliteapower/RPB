import {computed,onBeforeUnmount,ref} from 'vue'
import {useWorkforce} from './context'
import {useWorkforceText} from './text'
import {usePhaseText} from './phaseText'
import {displayTime} from './time'
/** A request belongs to the mounted page and captured store, never a later selection. */
export function usePhasePage(){const context=useWorkforce(),{t,error}=useWorkforceText(),{p,english,phaseError}=usePhaseText();const loading=ref(false),busy=ref(false),failure=ref(''),notice=ref('');let revision=0;onBeforeUnmount(()=>{revision++});const base=computed(()=>`/stores/${encodeURIComponent(context.store.value)}/workforce`)
 async function load<T>(request:()=>Promise<T>,accept:(data:T)=>void){const rev=++revision,store=context.store.value;loading.value=true;failure.value='';try{const data=await request();if(rev===revision&&context.current()&&store===context.store.value)accept(data)}catch(e){if(rev===revision&&context.current())failure.value=phaseError(e)||error(e)}finally{if(rev===revision&&context.current())loading.value=false}}
 async function write(request:()=>Promise<unknown>,done?:()=>void|Promise<void>){if(busy.value)return;busy.value=true;failure.value='';notice.value='';const store=context.store.value;try{await request();if(context.current()&&store===context.store.value){notice.value=p('applied');await done?.()}}catch(e){if(context.current()&&store===context.store.value)failure.value=phaseError(e)||error(e)}finally{if(context.current())busy.value=false}}
 return {...context,t,p,english,base,loading,busy,failure,notice,load,write,format:(v:string|null|undefined,zone:string)=>displayTime(v,zone,english())}
}
