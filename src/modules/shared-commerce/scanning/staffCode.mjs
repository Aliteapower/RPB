import {cardCode} from '../../cards/qr.mjs'

const credentialFormats = [
 ['voucher', /^VC\.[0-9a-f]{64}$/],
 ['storage', /^WS\.[A-Za-z0-9_-]{43}$/],
 ['identity', /^W[IP]\.[A-Za-z0-9_-]{43}$/],
 ['payment', /^WC\.[A-Za-z0-9_-]{43}$/],
]

/** Recognise credentials only; never follow arbitrary scanned URLs. */
export function staffScanCode(value,origin,store){
 const text=value.trim();if(text.length>4096)return null
 for(const [kind,pattern] of credentialFormats){
  if(pattern.test(text))return{kind,code:text}
 }
 const authorization=cardCode(text,origin,'CA')
 if(authorization){
  if(!text.startsWith('CA.')){const url=new URL(text);if(store&&url.pathname!==`/wallet/${store}/cards`)return null}
  return{kind:'card',code:authorization}
 }
 return null
}
