import { readFile } from 'node:fs/promises'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
export async function resolve(specifier, context, nextResolve) {
 if (specifier.startsWith('.') && /\.(ts|vue)$/.test(specifier)) return {url:new URL(specifier,context.parentURL).href,shortCircuit:true}
 if (specifier.startsWith('.') && !/\.[a-z]+$/i.test(specifier)) {try{return await nextResolve(specifier+'.ts',context)}catch{}try{return await nextResolve(specifier+'/index.ts',context)}catch{}}
 return nextResolve(specifier,context)
}
export async function load(url,context,nextLoad) {
 if(url.endsWith('.css'))return {format:'module',source:'export default {}',shortCircuit:true}
 if (/\.(ts|vue)$/.test(url)) {
  let source=await readFile(new URL(url),'utf8')
  if(url.endsWith('.vue')){const {descriptor}=parse(source,{filename:url});source=compileScript(descriptor,{id:url,inlineTemplate:true}).content}
  return {format:'module',source:ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText,shortCircuit:true}
 }
 return nextLoad(url,context)
}
