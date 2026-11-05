import type {Event,Item} from './types'

/** Historical snapshots stay immutable; later photos are read from this item's own events. */
export function itemPhotos(item:Item,events:Event[]=[]):string[] {
 const followups=events.filter(event=>event.sourceId===item.id&&event.action==='photos.added')
  .flatMap(event=>Array.isArray(event.details?.photos)?event.details.photos:[])
 return [...new Set([...(item.snapshot.receiptPhotos??[]),...(item.snapshot.applicationPhotos??[]),...followups]
  .filter((photo):photo is string=>typeof photo==='string'&&photo.trim().length>0))]
}
