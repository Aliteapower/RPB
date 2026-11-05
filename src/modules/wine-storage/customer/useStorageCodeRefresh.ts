import {watch,onUnmounted} from 'vue'
/** Poll only while a pickup code is displayed; pause background tabs and never overlap requests. */
export function useStorageCodeRefresh(enabled:()=>boolean,refresh:()=>Promise<void>){
 let timer:ReturnType<typeof setInterval>|undefined,running=false,closed=false
 async function pulse(){if(closed||running||!enabled()||(typeof document!=='undefined'&&document.visibilityState==='hidden'))return;running=true;try{await refresh()}finally{running=false}}
 watch(enabled,value=>{if(timer){clearInterval(timer);timer=undefined}if(value)timer=setInterval(()=>{void pulse()},2500)},{immediate:true})
 if(typeof document!=='undefined')document.addEventListener?.('visibilitychange',pulse)
 onUnmounted(()=>{closed=true;if(timer)clearInterval(timer);if(typeof document!=='undefined')document.removeEventListener?.('visibilitychange',pulse)})
}
