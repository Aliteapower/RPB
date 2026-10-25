import type {Item,Selection} from '../types'
export function collectibleItems(items:Item[],now=Date.now()):Item[]{return items.filter(i=>i.status==='stored'&&i.remaining>0&&Date.parse(i.expiresAt)>now)}
export function withdrawalSelection(items:Item[],selected:Record<string,number>,now=Date.now()):Selection[]|null{
 const entries=Object.entries(selected),available=new Map(collectibleItems(items,now).map(i=>[i.id,i]))
 if(!entries.length||entries.length>50)return null
 const result:Selection[]=[]
 for(const [itemId,quantity] of entries){const item=available.get(itemId);if(!item||!Number.isInteger(quantity)||quantity<1||quantity>item.remaining||(item.opened&&quantity!==1))return null;result.push({itemId,quantity})}
 return result
}
