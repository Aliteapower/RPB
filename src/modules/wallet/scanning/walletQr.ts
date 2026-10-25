import {cardCode} from '../../cards/qr.mjs'
/** Merchant QR codes represent intents: only the subsequent confirmation can debit. */
export function merchantCode(value:string,origin:string):{store?:string;code:string;kind:'bill'|'redeem'|'order'}{
 const text=value.trim()
 if(/^W[BRM]\.[A-Za-z0-9_-]{43}$/.test(text))return{code:text,kind:text.startsWith('WM.')?'order':text.startsWith('WR.')?'redeem':'bill'}
 const url=new URL(text),match=/^\/wallet\/([0-9a-f-]{36})$/i.exec(url.pathname),params=new URLSearchParams(url.hash.slice(1))
 if(url.origin!==origin||!match)throw new Error('Invalid merchant wallet code')
 for(const [kind,prefix] of [['bill','WB'],['redeem','WR'],['order','WM']] as const){const code=params.get(kind)??'';if(new RegExp('^'+prefix+'\\.[A-Za-z0-9_-]{43}$').test(code))return{store:match[1],code,kind}}
 throw new Error('Invalid merchant wallet code')
}

export type WalletQrPurpose = 'customer' | 'bill' | 'identity' | 'personal' | 'cardMerchant' | 'cardAuthorization'

/** Shape/type checks only. Expiry, ownership and replay remain server decisions. */
export function normalizeWalletQr(value: string, purpose: WalletQrPurpose, origin: string): string | null {
  const text = value.trim()
  if (text.length > 4096) return null
  if(purpose==='cardMerchant')return cardCode(text,origin)?text:null
  if(purpose==='cardAuthorization')return cardCode(text,origin,'CA')
  if(purpose==='bill'&&cardCode(text,origin))return text
  if (purpose === 'identity') return /^W[IP]\.[A-Za-z0-9_-]{43}$/.test(text) ? text : null
  if (purpose === 'personal') return /^WI\.[A-Za-z0-9_-]{43}$/.test(text) ? text : null
  if (purpose === 'customer') return /^W[IPC]\.[A-Za-z0-9_-]{43}$/.test(text) ? text : null
  try {merchantCode(text,origin);return text} catch { /* Unrelated QR. Keep scanning. */ }
  return null
}
