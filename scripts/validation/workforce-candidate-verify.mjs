import {readFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import {resolve,sep} from 'node:path'
import {fileURLToPath} from 'node:url'
import {execFileSync} from 'node:child_process'

export function verifyFile(root,entry) {
 if (!/^[a-f0-9]{64}$/i.test(entry.sha256)) throw new Error('Invalid SHA256: '+entry.path)
 const base=resolve(root),path=resolve(base,entry.path)
 if (!path.startsWith(base+sep)) throw new Error('Path outside candidate root: '+entry.path)
 const actual=createHash('sha256').update(readFileSync(path)).digest('hex')
 if (actual!==entry.sha256.toLowerCase()) throw new Error('Candidate drift: '+entry.path)
}

export function verifyCandidate(root,manifest) {
 const head=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim()
 if (head!==manifest.baselineGitSha) {
  if (!manifest.integration?.allowDescendantOfBaseline) throw new Error('Candidate Git baseline changed')
  execFileSync('git',['merge-base','--is-ancestor',manifest.baselineGitSha,head],{cwd:root})
  const paths=execFileSync('git',['diff','--name-only','-z',manifest.baselineGitSha,head],{cwd:root,encoding:'utf8'}).split('\0').filter(Boolean)
  const allowed=new Set([...manifest.included.map(e=>e.path),...manifest.integration.metadataPaths])
  for (const path of paths) if (!allowed.has(path)) throw new Error('Commit outside frozen scope: '+path)
 }
 for (const entry of [...manifest.included,...manifest.artifactFiles,...manifest.verificationTools]) verifyFile(root,entry)
 return {candidate:manifest.candidate,sourceFiles:manifest.included.length,artifactFiles:manifest.artifactFiles.length,verified:true,readOnly:true}
}

if (process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 try {
  const root=resolve(fileURLToPath(new URL('../..',import.meta.url)))
  const manifest=JSON.parse(readFileSync(resolve(root,process.argv[2]||'docs/release-candidates/2026-10-30-workforce-rc2.json'),'utf8'))
  console.log(JSON.stringify(verifyCandidate(root,manifest)))
 } catch (error) {console.error(error.message);process.exitCode=1}
}
