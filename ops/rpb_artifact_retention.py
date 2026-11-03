"""Retention planning for registered releases and successful duplicate uploads."""
import time,os
from pathlib import Path
from rpb_artifact_registry import SafetyError,inventory,signature,safe_remove_tree

def runtime_paths():
 result=set()
 for p in Path('/proc').glob('[0-9]*/cmdline'):
  try:
   args=[a.decode(errors='replace') for a in p.read_bytes().split(b'\0') if a];cwd=(p.parent/'cwd').resolve();result.add(str(cwd))
   for a in args:
    if a and not a.startswith('-') and ('/' in a or a.endswith('.jar')):result.add(str((cwd/a).resolve()))
  except OSError:pass
 return result

def plan_managed(catalog,versions,days=15,keep_releases=2,hours=24,now=None,active_paths=None):
 if days<15 or keep_releases<2 or hours<24:raise SafetyError('Unsafe artifact retention limits')
 now=time.time() if now is None else now;records=catalog.records();protected={};warnings=[];blocked=[];unknown=[];eligible={};snapshots={};active=runtime_paths() if active_paths is None else active_paths
 def protect(key,reason):protected.setdefault(key,[]).append(reason)
 for kind in ('release','upload'):
  root=catalog.paths.root(kind)
  if root.exists():unknown.extend(str(p) for p in root.iterdir() if kind+':'+p.name not in records)
 for key,r in records.items():
  path=catalog.location(r)
  if r['status']=='collected':
   if path.exists():unknown.append(str(path));warnings.append('Collected record still has files: '+key)
   continue
  if not path.exists():blocked.append('Missing registered artifact: '+key);continue
  if (path/'.rpb-keep').exists():protect(key,'manual-pin');continue
  if not catalog.valid(r):protect(key,'manifest-mismatch-or-unknown-content');warnings.append(key);continue
  snapshots[key]={rel:signature(path/rel) for rel in r['files']};eligible[key]=r
  if any(p==str(path) or p.startswith(str(path)+'/') for p in active):protect(key,'runtime-reference')
  if r['kind']=='backup':protect(key,'backup-policy-owned')
 # Quota is across valid successful pairs, never partial/unknown directories.
 releases=sorted([(k,r) for k,r in eligible.items() if r['kind']=='release'],key=lambda v:(v[1]['successful_at'],v[0]),reverse=True)
 current_keys=set()
 for field,marker in [('backend_sha','RELEASE_SHA'),('frontend_sha','FRONTEND_RELEASE_SHA')]:
  matches=[(k,r) for k,r in releases if r[field]==versions[marker]]
  if matches:current_keys.add(matches[0][0]);protect(matches[0][0],'current-'+field)
 for key,r in releases[:keep_releases]:protect(key,'recent-successful-pair')
 for key,r in eligible.items():
  if r['kind']=='release' and key in protected:
   for ref in r['rollback']:
    p=catalog.paths.backups/ref
    if not p.exists() or p.is_symlink():blocked.append('Missing rollback: '+ref)
    protect('backup:'+ref,'release-rollback:'+key)
  if r['kind']=='upload':
   # A successful upload does not count as a necessary current-version copy itself.
   if protected.get(key)==['current-version']:protected.pop(key,None)
   if not r['canonical'] or set(r['canonical'])!=set(r['files']):protect(key,'missing-original-references');continue
   for rel,ref in r['canonical'].items():
    original,file=ref.split('/',1);keep=eligible.get(original)
    if not keep or keep['files'].get(file)!=r['files'][rel]:protect(key,'original-missing-or-changed')
    else:protect(original,'duplicate-original:'+key)
 for key,r in eligible.items():
  if r['kind']=='release' and key in protected:
   for ref in r['rollback']:
    if not (catalog.paths.backups/ref).is_dir():blocked.append('Missing protected rollback: '+ref)
    protect('backup:'+ref,'release-rollback:'+key)
 delete=[]
 for key,r in eligible.items():
  age=now-r['successful_at'];limit=hours*3600 if r['kind']=='upload' else days*86400
  if r['kind']!='backup' and key not in protected and age>limit:delete.append(key)
 # Unknown current records cannot authorize deleting any guessed rollback dependencies.
 for key,r in records.items():
  if r['status']=='success' and r['kind']=='release' and (key in protected or r['backend_sha']==versions['RELEASE_SHA'] and r['frontend_sha']==versions['FRONTEND_RELEASE_SHA']):
   for ref in r['rollback']:
    protect('backup:'+ref,'current-release-rollback')
    p=catalog.paths.backups/ref
    if not p.is_dir() or p.is_symlink() or p.resolve()!=p:blocked.append('Missing/unsafe protected rollback: '+ref)
 inodes={}
 for key in delete:
  for rel in records[key]['files']:
   s=(catalog.location(records[key])/rel).stat();v=inodes.setdefault((s.st_dev,s.st_ino),{'links':s.st_nlink,'count':0,'bytes':getattr(s,'st_blocks',0)*512});v['count']+=1
 return {'delete':sorted(delete),'protected':protected,'blocked':blocked,'warnings':warnings,'unknown':sorted(unknown),'versions':dict(versions),'snapshots':snapshots,'records':records,'estimated_exclusive_bytes':sum(v['bytes'] for v in inodes.values() if v['count']==v['links'])}

def validate_managed(plan,catalog,versions):
 if plan['blocked'] or versions!=plan['versions']:raise SafetyError('Blocked or changed production versions')
 if catalog.records()!=plan['records']:raise SafetyError('Registry changed')
 active=runtime_paths()
 for key in plan['delete']:
  path=str(catalog.location(plan['records'][key]))
  if any(p==path or p.startswith(path+'/') for p in active):raise SafetyError('Artifact entered runtime use')
 # Include canonical keepers, not just deletions, before any unlink.
 for key,r in plan['records'].items():
  if key not in plan['snapshots']:continue
  if not catalog.valid(r):raise SafetyError('Artifact/original changed: '+key)
  if {rel:signature(catalog.location(r)/rel) for rel in r['files']}!=plan['snapshots'][key]:raise SafetyError('Artifact metadata changed: '+key)

def apply_managed(plan,catalog,versions):
 validate_managed(plan,catalog,versions);deleted=[]
 for key in plan['delete']:
  r=plan['records'][key]
  if not catalog.valid(r):raise SafetyError('Candidate changed: '+key)
  safe_remove_tree(catalog.location(r),r['files']);catalog.collected(r);deleted.append(key)
 return deleted
