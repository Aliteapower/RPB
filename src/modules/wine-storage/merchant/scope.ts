import type {Home,Item,Selection} from '../types'
export interface StorageCustomer {id:string;displayName:string;phone:string|null}
export function customerStorage(home:Home,customerId:string):Home{
 const applications=home.applications.filter(a=>a.customerId===customerId),items=home.items.filter(i=>i.customerId===customerId),intents=home.intents.filter(i=>i.customerId===customerId)
 const ids=new Set([...applications,...items,...intents].map(value=>value.id))
 return{...home,applications,items,intents,events:home.events.filter(e=>ids.has(e.sourceId))}
}
export function merchantSelection(customerId:string,items:Item[],values:Record<string,number>,now=Date.now()):{customerId:string;selections:Selection[]}|null{
 const entries=Object.entries(values);if(!customerId||!entries.length||entries.length>50)return null
 const available=new Map(items.filter(i=>i.customerId===customerId&&i.status==='stored'&&i.remaining>0&&Date.parse(i.expiresAt)>now).map(i=>[i.id,i]))
 const selections:Selection[]=[]
 for(const [itemId,quantity]of entries){const item=available.get(itemId);if(!item||!Number.isInteger(quantity)||quantity<1||quantity>item.remaining||item.opened&&quantity!==1)return null;selections.push({itemId,quantity})}
 return{customerId,selections}
}
