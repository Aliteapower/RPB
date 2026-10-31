// Loopback-only test browser host. Bootstrap cookies belong exclusively to the fresh wallet fixture.
import { createServer, request } from 'node:http'
import { readFileSync, existsSync, statSync } from 'node:fs'
import { resolve, extname, sep } from 'node:path'
const fixture = JSON.parse(readFileSync('target/wallet-validation/fixture.json', 'utf8'))
if (!/^wallet_browser_[a-f0-9]{32}$/.test(fixture.schema) || fixture.backendPort !== 8083 || fixture.frontendPort !== 5183) throw new Error('Unexpected fixture')
const dist = resolve('dist')
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' }
createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1:5183')
  if (url.pathname.startsWith('/__wallet-test/')) {
    const role = url.pathname.split('/').pop()
    if (!['staff','customer','platform'].includes(role)) { res.writeHead(404);res.end();return }
    const target = role === 'customer' ? `/wallet/${fixture.stores[0]}` : role === 'platform' ? `/platform/tenants/${fixture.tenant}/billing` : `/stores/${fixture.stores[0]}/wallet`
    const cookies = [`RPB_SESSION=${role === 'platform' ? fixture.platformToken : role === 'staff' ? fixture.staffToken : ''}; HttpOnly; SameSite=Lax; Path=/`, `RPB_CUSTOMER_SESSION=${role === 'customer' ? fixture.customerToken : ''}; HttpOnly; SameSite=Lax; Path=/`]
    res.writeHead(302, {'Set-Cookie':cookies,Location:target,'Cache-Control':'no-store'});res.end();return
  }
  if (url.pathname.startsWith('/api/')) {
    const proxy = request({ hostname:'127.0.0.1',port:fixture.backendPort,path:req.url,method:req.method,headers:{...req.headers,host:`127.0.0.1:${fixture.backendPort}`} }, upstream => {res.writeHead(upstream.statusCode,upstream.headers);upstream.pipe(res)})
    proxy.on('error',()=>{res.writeHead(502);res.end('Local validation backend unavailable')});req.pipe(proxy);return
  }
  let file=resolve(dist, '.'+decodeURIComponent(url.pathname))
  if (file !== dist && !file.startsWith(dist+sep)) {res.writeHead(403);res.end();return}
  if (!existsSync(file) || !statSync(file).isFile()) file=resolve(dist,'index.html')
  res.writeHead(200,{'Content-Type':mime[extname(file)]||'application/octet-stream','Cache-Control':'no-store'});res.end(readFileSync(file))
}).listen(5183,'127.0.0.1',()=>console.log('Wallet isolated browser host: http://127.0.0.1:5183'))
