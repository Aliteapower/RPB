export function normalizeGtin(value:string):string|null {
 const v=value.trim();if(!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(v))return null
 let sum=0,weight=3;for(let i=v.length-2;i>=0;i--){sum+=Number(v[i])*weight;weight=4-weight}
 return (10-sum%10)%10===Number(v.at(-1))?v.padStart(14,'0'):null
}
