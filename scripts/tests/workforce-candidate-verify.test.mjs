import test from 'node:test'
import assert from 'node:assert/strict'
import {mkdtempSync,writeFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {createHash} from 'node:crypto'
import {execFileSync} from 'node:child_process'
import {verifyFile,verifyCandidate} from '../validation/workforce-candidate-verify.mjs'

test('candidate verifier accepts captured bytes and rejects later drift',()=>{
 const root=mkdtempSync(resolve('target/workforce-candidate-verifier-'))
 const entry={path:'source.txt',sha256:createHash('sha256').update('captured').digest('hex')}
 writeFileSync(resolve(root,entry.path),'captured');verifyFile(root,entry)
 writeFileSync(resolve(root,entry.path),'changed')
 assert.throws(()=>verifyFile(root,entry),/Candidate drift/)
})
test('candidate verifier rejects invalid digests and paths outside its root',()=>{
 assert.throws(()=>verifyFile(process.cwd(),{path:'target/test.txt',sha256:'invalid'}),/Invalid SHA256/)
 assert.throws(()=>verifyFile(process.cwd(),{path:'../outside',sha256:'0'.repeat(64)}),/outside candidate root/)
})
test('integration allows approved descendant commits and rejects unrelated committed files',()=>{
 const root=mkdtempSync(resolve('target/workforce-candidate-integration-'))
 const git=(...args)=>execFileSync('git',['-c','user.name=RPB verifier test','-c','user.email=verifier@example.invalid',...args],{cwd:root,encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim()
 git('init');git('commit','--allow-empty','-m','baseline')
 const baselineGitSha=git('rev-parse','HEAD'),entry={path:'approved.txt',sha256:createHash('sha256').update('approved').digest('hex')}
 writeFileSync(resolve(root,entry.path),'approved');git('add','approved.txt');git('commit','-m','approved candidate')
 const manifest={baselineGitSha,candidate:'test',included:[entry],artifactFiles:[],verificationTools:[],integration:{allowDescendantOfBaseline:true,metadataPaths:[]}}
 assert.equal(verifyCandidate(root,manifest).verified,true)
 writeFileSync(resolve(root,'unrelated.txt'),'excluded');git('add','unrelated.txt');git('commit','-m','outside scope')
 assert.throws(()=>verifyCandidate(root,manifest),/Commit outside frozen scope/)
})
