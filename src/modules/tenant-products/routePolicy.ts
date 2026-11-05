import entries from '../../main/resources/product-line-entries.json'
import type { ProductRouteAccess } from '../../types/meApps'

type Route = {path:string;query:Record<string,unknown>;params:Record<string,unknown>;meta:Record<string,unknown>}
function patternMatches(pattern:string,path:string,prefix=false) {
  const parts=pattern.split('?')[0]!.split('/')
  const actual=path.split('/')
  return (prefix?actual.length>=parts.length:actual.length===parts.length)&&parts.every((part,i)=>part.startsWith(':')?!!actual[i]:part===actual[i])
}
export function productForPath(path:string) {
  return entries.find(entry=>entry.appKey&&entry.routePrefixes.some(prefix=>patternMatches(prefix,path,true)))
}
function matchesRoute(pattern:string,route:Route) {
  if(!patternMatches(pattern,route.path))return false
  const query=pattern.split('?')[1]
  return !query||Array.from(new URLSearchParams(query)).every(([key,value])=>route.query[key]===value)
}
export function routeAccessDecision(route:Route,access:ProductRouteAccess[],loaded:boolean,failed=false): {mode:string;historyRoute?:string} {
  const product=productForPath(route.path)
  if(route.meta.public||!product)return {mode:'unmanaged'}
  if(!loaded)return {mode:'loading'}
  if(failed)return {mode:'failed'}
  const state=access.find(item=>item.appKey===product.appKey)
  let historyRoute:string|undefined
  if(state?.historyAllowed&&state.historyRoute?.startsWith('/')&&!state.historyRoute.startsWith('//')) {
    const url=new URL(state.historyRoute,'https://local.invalid')
    const target={...route,path:url.pathname,query:Object.fromEntries(url.searchParams)}
    if(url.pathname.split('/')[2]===route.path.split('/')[2]&&productForPath(url.pathname)?.appKey===product.appKey&&url.searchParams.get('history')==='1'&&product.historyReadRoutes.some(p=>matchesRoute(p,target)))historyRoute=state.historyRoute
  }
  const result=(mode:string)=>({mode,historyRoute})
  const readOnly=product.historyReadRoutes.some(pattern=>matchesRoute(pattern,route))
  if(route.query.history==='1')return result(state?.historyAllowed&&readOnly?'history':'denied')
  if(state?.businessAllowed)return result('business')
  if(state?.historyAllowed&&readOnly)return result('historyRedirect')
  if(state?.historyAllowed&&product.recoveryRoutes.some(pattern=>matchesRoute(pattern,route)))return result('recovery')
  return result('denied')
}
