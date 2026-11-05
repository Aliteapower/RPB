import {moduleRequest as httpRequest} from '../shared-commerce/http'
export {ModuleApiError} from '../shared-commerce/http'
export function moduleRequest<T>(base:string,path:string,method='GET',body?:unknown){return httpRequest<T>(base,path,method,body,{'X-RPB-Catalog':'1'})}
