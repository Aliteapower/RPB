/** Visible pages refresh without overlapping requests; owners decide whether editing permits it. */
export function startScheduleSync(canRefresh:()=>boolean,refresh:()=>Promise<void>,host:{win:Pick<Window,'addEventListener'|'removeEventListener'|'setInterval'|'clearInterval'>;doc:Pick<Document,'addEventListener'|'removeEventListener'|'visibilityState'>}={win:window,doc:document}){
 let stopped=false,running=false
 const tick=async()=>{if(stopped||running||host.doc.visibilityState==='hidden'||!canRefresh())return;running=true;try{await refresh()}finally{running=false}}
 const check=()=>{void tick().catch(()=>{/* The page owns its error state. */})}
 const interval=host.win.setInterval(check,15000)
 host.win.addEventListener('focus',check);host.doc.addEventListener('visibilitychange',check)
 return ()=>{stopped=true;host.win.clearInterval(interval);host.win.removeEventListener('focus',check);host.doc.removeEventListener('visibilitychange',check)}
}
