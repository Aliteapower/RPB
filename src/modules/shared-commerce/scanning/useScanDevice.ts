import {computed,onMounted,onUnmounted,ref,toValue,type MaybeRefOrGetter} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthSessionStore} from '../../../stores/authSession'
import {readScanDevice,scanDeviceKey,writeScanDevice,type ScanDevice} from './devicePreference'

const revision=ref(0),sessionPreferences=new Map<string,ScanDevice>()
const unavailable={getItem(){return null},setItem(){throw Error('Storage unavailable')}}
function storage(){try{return localStorage}catch{return unavailable}}

/** A device preference never grants product access or employee permissions. */
export function useScanDevice(store?:MaybeRefOrGetter<string|undefined>){
  const route=useRoute(),auth=useAuthSessionStore()
  const scope=computed(()=>({tenantId:auth.user?.tenantId??'',storeId:toValue(store)??String(route?.params.storeId??'')}))
  const key=computed(()=>scanDeviceKey(scope.value))
  const mode=computed(()=>{void revision.value;return key.value?(sessionPreferences.get(key.value)??readScanDevice(scope.value,storage())):'camera'})
  const identity=computed(()=>`${key.value??''}:${auth.user?.accountId??''}`)
  function setMode(value:ScanDevice){
    if(!key.value||!['camera','hardware'].includes(value))return false
    const saved=writeScanDevice(scope.value,value,storage())
    if(saved)sessionPreferences.delete(key.value);else sessionPreferences.set(key.value,value)
    revision.value++;return saved
  }
  function changed(event:StorageEvent){if(event.key===null||event.key===key.value){if(key.value)sessionPreferences.delete(key.value);revision.value++}}
  onMounted(()=>window.addEventListener('storage',changed))
  onUnmounted(()=>window.removeEventListener('storage',changed))
  return{mode,setMode,identity,available:computed(()=>!!key.value)}
}
