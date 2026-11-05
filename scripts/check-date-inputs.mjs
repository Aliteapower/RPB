import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {parse as sfc} from '@vue/compiler-sfc'
import {parse} from '@vue/compiler-dom'
export function bareDateInputs(root='src'){
 const failures=[]
 function walk(dir){for(const item of fs.readdirSync(dir,{withFileTypes:true})){
  const file=path.join(dir,item.name);if(item.isDirectory()){walk(file);continue}if(!file.endsWith('.vue')||file.replaceAll('\\','/').endsWith('/components/common/DateInput.vue'))continue
  const template=sfc(fs.readFileSync(file,'utf8'),{filename:file}).descriptor.template;if(!template)continue
  function visit(node){if(node.type===1&&node.tag==='input'){
   const prop=node.props.find(p=>p.type===6&&p.name==='type'||p.type===7&&p.name==='bind'&&p.arg?.content==='type')
   const raw=prop?.type===6?prop.value?.content:prop?.exp?.content
   if(raw&&(/^(date|month|datetime-local)$/.test(raw)||/['"](?:date|month|datetime-local)['"]/.test(raw)))failures.push(file+':'+(template.loc.start.line+node.loc.start.line-1))
  }for(const child of node.children||[])visit(child)}visit(parse(template.content))
 }}walk(root);return failures
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const failures=bareDateInputs();if(failures.length){console.error('Use components/common/DateInput.vue instead of bare date inputs:\n'+failures.join('\n'));process.exitCode=1}else console.log('Shared date input check passed')
}
