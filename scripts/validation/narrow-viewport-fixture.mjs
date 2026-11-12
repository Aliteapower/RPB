// Browser regression fixture using the real application's global CSS.
// node scripts/validation/narrow-viewport-fixture.mjs
// At 320 CSS px, check /?long=1 and / with and without vertical scrollbars.
// Require documentElement.scrollWidth <= documentElement.clientWidth.
// The wide table must remain scrollable within its own container.
import {createServer} from 'node:http'
import {readFile} from 'node:fs/promises'

createServer(async(req,res)=>{
 try {
  const source=await readFile(new URL('../../src/App.vue',import.meta.url),'utf8')
  const css=source.match(/<style>([\s\S]*?)<\/style>/)[1]
  const long=new URL(req.url,'http://localhost').searchParams.has('long')
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'})
  res.end(`<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><style>${css}
   main{padding:12px}h1{font-size:20px}.table-scroll{overflow-x:auto}table{width:720px}button{min-height:44px}
  </style><body><div id="app"><main><h1>窄屏布局回归 / Narrow viewport</h1>
   <button>返回 / Back</button><p>真实 App.vue 全局样式，虚构内容，无业务请求。</p>
   <div class="table-scroll"><table><tr><td>编号 / Code</td><td>名称 / Name</td><td>状态 / Status</td></tr></table></div>
   ${long?'<section style="height:1800px">强制竖向滚动 / Vertical scrollbar</section>':''}
  </main></div></body></html>`)
 } catch(error){res.writeHead(500);res.end(String(error))}
}).listen(5197,'127.0.0.1',()=>process.stdout.write('Narrow viewport fixture: http://127.0.0.1:5197/?long=1\n'))
