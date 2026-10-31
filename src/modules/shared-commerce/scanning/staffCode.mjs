import {cardCode} from '../../cards/qr.mjs'
/** Recognise credentials only; never follow arbitrary scanned URLs. */
export function staffScanCode(value,origin,store){
 const text=value.trim();if(text.length>4096)return null
 if(/^WS\.[A-Za-z0-9_-]{43}$/.test(text))return{kind:'storage',code:text}
 if(/^W[IP]\.[A-Za-z0-9_-]{43}$/.test(text))return{kind:'identity',code:text}
 if(/^WC\.[A-Za-z0-9_-]{43}$/.test(text))return{kind:'payment',code:text}
 const authorization=cardCode(text,origin,'CA')
 if(authorization){
  if(!text.startsWith('CA.')){const url=new URL(text);if(store&&url.pathname!==`/wallet/${store}/cards`)return null}
  return{kind:'card',code:authorization}
 }
 return null
}
