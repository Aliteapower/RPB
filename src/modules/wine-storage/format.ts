export function dateTime(value:string,zone:string){return new Intl.DateTimeFormat('en-GB',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(new Date(value))}

/** Use frozen receipt names; legacy records retain their original free-text location. */
export function storageLocationText(value:{location:string;storageLocation?:{name:string}|null;snapshot?:{storageLocation?:{name:string}|null}}){return [value.storageLocation?.name??value.snapshot?.storageLocation?.name,value.location].filter(part=>part&&part.trim()).join(' · ')||'—'}
