import type {Item,Selection} from './types'
import {withdrawalSelection} from './customer/selection'
export function cartSelection(items:Item[],quantities:Record<string,number>,now=Date.now()):Selection[]|null {
 return withdrawalSelection(items,Object.fromEntries(Object.entries(quantities).filter(([,n])=>n!==0)),now)
}
export function cartTotals(quantities:Record<string,number>){const values=Object.values(quantities).filter(n=>Number.isInteger(n)&&n>0);return{items:values.length,bottles:values.reduce((a,b)=>a+b,0)}}
