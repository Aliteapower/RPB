import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readdirSync,readFileSync} from 'node:fs'
import {join} from 'node:path'
test('shared product catalog does not depend on wallet or cards frontend business modules',()=>{const walk=dir=>readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(join(dir,e.name)):[join(dir,e.name)]);for(const file of walk('src/modules/catalog').filter(f=>/\.(ts|vue|css)$/.test(f))){const source=readFileSync(file,'utf8');assert.doesNotMatch(source,/(?:from\s*|@import\s*)['"][^'"]*\/cards\//,file);assert.doesNotMatch(source,/from\s*['"][^'"]*\/wallet\//,file)}})
