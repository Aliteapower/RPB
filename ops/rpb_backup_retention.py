#!/usr/bin/env python3
"""RPB backup retention. Default is a read-only plan; production roots cannot be overridden."""
import argparse
from dataclasses import dataclass, field
import datetime as dt
import json
import hashlib
import math
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tarfile
import time
import zipfile

BACKUPS=Path('/opt/rpb/backups')
APP=Path('/opt/rpb/app')
RELEASES=Path('/opt/rpb/releases')
LOCK=Path('/home/ubuntu/rpb-ledger-release.lock')
STATE=Path('/var/lib/rpb-backup-retention/last-run.json')

class SafetyError(RuntimeError):
 pass

@dataclass(frozen=True)
class Policy:
 retention_days:int=15
 keep_full:int=2
 keep_frontend:int=2
 pinned_backups:tuple=()
 def validate(self):
  if any(type(v) is not int for v in (self.retention_days,self.keep_full,self.keep_frontend)) or not 1<=self.retention_days<=365 or self.keep_full<2 or self.keep_frontend<2:
   raise SafetyError('Keep at least 1 day, 2 full backups and 2 frontend snapshots')
  if not isinstance(self.pinned_backups,(tuple,list)):raise SafetyError('Pins must be a list of basenames')
  for name in self.pinned_backups:
   if not isinstance(name,str) or not name or name in ('.','..') or '/' in name or '\\' in name:
    raise SafetyError('Pins must be immediate backup basenames')

@dataclass
class Entry:
 path:Path
 signature:tuple
 newest:float
 size:int
 files:list=field(default_factory=list)
 backend:bool=False
 frontend:bool=False
 databases:list=field(default_factory=list)
 markers:dict=field(default_factory=dict)
 unsafe:str=''
 pin:bool=False

@dataclass
class Plan:
 base:Path
 app:Path
 base_inode:tuple
 identity:dict
 policy:Policy
 entries:dict
 delete:list
 keep:dict
 blocked:list
 warnings:list
 cutoff:float
 estimated_bytes:int
 def report(self):
  return {'mode':'dry-run','retention_days':self.policy.retention_days,
   'cutoff_utc':dt.datetime.fromtimestamp(self.cutoff,dt.timezone.utc).isoformat(),
   'current_versions':self.identity,'delete':self.delete,'retain':self.keep,
   'blocked':self.blocked,'warnings':self.warnings,'estimated_exclusive_bytes':self.estimated_bytes}

def marker(path):
 if path.is_symlink() or not path.is_file() or path.stat().st_size>128:
  raise SafetyError('Missing/unsafe release marker: '+str(path))
 value=path.read_text().strip()
 if not re.fullmatch('[0-9a-f]{40}',value):raise SafetyError('Invalid release marker: '+str(path))
 return value

def identity(app):return {n:marker(app/n) for n in ('RELEASE_SHA','FRONTEND_RELEASE_SHA')}

def safe_base(base):
 if base.is_symlink() or not base.is_dir() or base.resolve()!=base:
  raise SafetyError('Backup root must be a canonical directory')
 s=base.stat();return s.st_dev,s.st_ino

def rpb_jar(path):
 try:
  with zipfile.ZipFile(path) as jar:
   return jar.getinfo('META-INF/MANIFEST.MF').file_size<65536 and 'Start-Class: com.rpb.reservation.' in jar.read('META-INF/MANIFEST.MF').decode('utf-8')
 except (OSError,ValueError,KeyError,zipfile.BadZipFile,UnicodeError):return False

def html(path):
 try:return '<html' in path.read_bytes()[:65536].decode('utf-8',errors='ignore').lower()
 except OSError:return False

def frontend_tar(path):
 try:
  with tarfile.open(path,'r:gz') as archive:
   count=0;found=False
   for member in archive:
    count+=1
    if count>20000 or not(member.isfile() or member.isdir()):return False
    name=PurePosixPath(member.name)
    if name.is_absolute() or '..' in name.parts or name.suffix.lower() in ('.pdf','.docx','.xlsx','.ttf','.otf'):return False
    if name.as_posix()=='index.html' and member.isfile() and member.size<1048576:
     stream=archive.extractfile(member);found=stream is not None and b'<html' in stream.read().lower()
   return found
 except (OSError,ValueError,tarfile.TarError):return False

def signature_node(path,base):
 s=path.lstat()
 return (str(path.relative_to(base)),s.st_mode,s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns,s.st_nlink)

def scan(path,base):
 if path.parent!=base:raise SafetyError('Backup is not an immediate child')
 if path.is_symlink():return Entry(path,(),0,0,unsafe='symlink')
 try:
  nodes=[path]
  device=base.stat().st_dev
  if path.lstat().st_dev!=device:return Entry(path,(),0,0,unsafe='mount_boundary')
  if path.is_dir():
   for root,dirs,files in os.walk(path,followlinks=False):
    nodes.extend(Path(root)/name for name in dirs+files)
    for name in list(dirs):
     child=Path(root)/name
     if child.is_symlink() or child.lstat().st_dev!=device:dirs.remove(name)
  digest=hashlib.sha256()
  for p in sorted(nodes):digest.update(repr(signature_node(p,base)).encode('utf-8'))
  sig=(digest.hexdigest(),);files=[];unsafe='';device=base.stat().st_dev
  for p in nodes:
   s=p.lstat()
   if s.st_dev!=device:unsafe='mount_boundary'
   if stat.S_ISLNK(s.st_mode):unsafe='symlink'
   elif stat.S_ISREG(s.st_mode):files.append((p,s))
   elif not stat.S_ISDIR(s.st_mode):unsafe='special_file'
  e=Entry(path,sig,max(p.lstat().st_mtime for p in nodes),sum(s.st_size for p,s in files),files,unsafe=unsafe)
  if unsafe:return e
  if any(p.name in ('.git','.rpb-keep') for p in nodes):e.pin=True
  if any(p.suffix.lower() in ('.pdf','.docx','.xlsx','.ttf','.otf') for p,s in files):e.unsafe='unknown_originals';return e
  if path.is_file():
   if path.suffix=='.jar' and rpb_jar(path):e.backend=True
   else:e.unsafe='unknown_standalone_file'
   return e
  tops={p.relative_to(path).parts[0] for p in nodes if p!=path}
  allowed={'frontend','frontend.previous','assets','index.html','frontend.tgz','deployed-frontend.tgz',
   'reservation-platform.jar','reservation-platform.jar.sha256','backend.jar','RELEASE_SHA','FRONTEND_RELEASE_SHA','BACKEND_RELEASE_SHA','rpb-backend.env','.rpb-keep','source-export.json','preflight.jsonl','import.log'}
  def allowed_top(n):
   return n in allowed or bool(re.fullmatch(r'(business|other-tenants|wallet(?:-[a-z0-9]+)*)-(before|after|final)\.jsonl',n)) or n.endswith('.dump') or n in ('database.sql','queue_groups-before.sql','stores-without-queue-groups-before.txt')
  if not all(allowed_top(n) for n in tops):e.unsafe='unknown_content';return e
  jars=[path/n for n in ('reservation-platform.jar','backend.jar') if (path/n).is_file()]
  e.backend=bool(jars) and all(rpb_jar(p) for p in jars)
  if jars and not e.backend:e.unsafe='unknown_jar';return e
  indexes=[path/'frontend/index.html',path/'frontend.previous/index.html',path/'index.html']
  e.frontend=any(p.is_file() and html(p) for p in indexes)
  for n in ('frontend.tgz','deployed-frontend.tgz'):
   if (path/n).is_file():
    if not frontend_tar(path/n):e.unsafe='invalid_frontend_archive';return e
    e.frontend=True
  for p,s in files:
   if p.parent==path and p.suffix=='.dump':
    with p.open('rb') as f:magic=f.read(5)
    if magic!=b'PGDMP':e.unsafe='unknown_database_dump';return e
    e.databases.append(p)
  for n in ('RELEASE_SHA','FRONTEND_RELEASE_SHA','BACKEND_RELEASE_SHA'):
   if (path/n).exists():e.markers[n]=marker(path/n)
  if not (e.backend or e.frontend or e.databases):e.unsafe='unknown_content'
  return e
 except (OSError,SafetyError) as error:
  return Entry(path,(),0,0,unsafe='inspection_failed:'+str(error))

def validate_dump(path):
 try:return subprocess.run(['/usr/bin/pg_restore','-l',str(path)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=30).returncode==0
 except (OSError,subprocess.TimeoutExpired):return False

def plan_retention(base,app,releases,policy,now=None,db_validator=None):
 base=Path(base);app=Path(app);releases=Path(releases);policy.validate();inode=safe_base(base);versions=identity(app)
 now=time.time() if now is None else now;cutoff=math.floor(now)-policy.retention_days*86400
 entries={}
 for p in sorted(base.iterdir()):
  entry=scan(p,base)
  # Recent/protected trees need only live-artifact identities; avoid holding every asset's stat in memory.
  if entry.unsafe or entry.newest>=cutoff:
   entry.files=[(f,s) for f,s in entry.files if f==p or f.relative_to(p).as_posix() in ('reservation-platform.jar','backend.jar','frontend/index.html','frontend.previous/index.html','index.html')]
  entries[p.name]=entry
 keep={};warnings=[];blocked=[]
 def protect(name,reason):keep.setdefault(name,[]).append(reason)
 ordered=sorted(entries.values(),key=lambda e:(e.newest,e.path.name),reverse=True)
 active={};active_paths=[];copies={'backend':[],'frontend':[]};rollbacks={'backend':[],'frontend':[]}
 for kind,p in (('backend',app/'reservation-platform.jar'),('frontend',app.parent/'frontend/index.html')):
  if p.is_file():
   v=p.stat();active[kind]=(v.st_dev,v.st_ino);active_paths.append(p.resolve())
 for n,e in entries.items():
  if e.unsafe:protect(n,e.unsafe);warnings.append(n+':'+e.unsafe)
  if e.newest>=cutoff:protect(n,'within_retention')
  if e.pin or n in policy.pinned_backups:protect(n,'manual_pin')
  if any(p==e.path or e.path in p.parents for p in active_paths):protect(n,'active_artifact_path')
  backend_inodes={(v.st_dev,v.st_ino) for p,v in e.files if p==e.path or (p.parent==e.path and p.name in ('reservation-platform.jar','backend.jar'))}
  front_inodes={(v.st_dev,v.st_ino) for p,v in e.files if e.path.is_dir() and p.relative_to(e.path).as_posix() in ('frontend/index.html','frontend.previous/index.html','index.html')}
  if not e.unsafe and e.backend and (e.markers.get('RELEASE_SHA')==versions['RELEASE_SHA'] or e.markers.get('BACKEND_RELEASE_SHA')==versions['RELEASE_SHA'] or active.get('backend') in backend_inodes):copies['backend'].append(n)
  front_version=e.markers.get('FRONTEND_RELEASE_SHA') or (e.markers.get('RELEASE_SHA') if not e.backend else None)
  if not e.unsafe and e.frontend and (front_version==versions['FRONTEND_RELEASE_SHA'] or active.get('frontend') in front_inodes):copies['frontend'].append(n)
  if n.endswith('-before'):
   release=releases/n[:-7]
   if not release.is_symlink() and (release/'RELEASE_SHA').is_file():
    try:
     version=marker(release/'RELEASE_SHA')
     if version==versions['RELEASE_SHA'] and e.backend:rollbacks['backend'].append(n)
     if version==versions['FRONTEND_RELEASE_SHA'] and e.frontend:rollbacks['frontend'].append(n)
    except SafetyError:protect(n,'unreadable_release_reference')
 # Protect representative copies, not every old backup that shares an unchanged current JAR.
 for kind in copies:
  for candidates,reason in ((copies[kind],'current_artifact_representative'),(rollbacks[kind],'current_release_rollback')):
   if candidates:protect(max(candidates,key=lambda n:(entries[n].newest,n)),reason+':'+kind)
 validator=validate_dump if db_validator is None else db_validator;full=[]
 for e in ordered:
  if e.unsafe or not(e.backend and e.frontend and e.databases):continue
  if all(validator(p) for p in e.databases):full.append(e.path.name);protect(e.path.name,'validated_full_rollback')
  else:protect(e.path.name,'full_validation_failed');warnings.append(e.path.name+':full_validation_failed')
  if len(full)>=policy.keep_full:break
 if len(full)<policy.keep_full:blocked.append('Fewer than '+str(policy.keep_full)+' valid complete rollback backups')
 front=[e.path.name for e in ordered if not e.unsafe and e.frontend and not e.databases][:policy.keep_frontend]
 for n in front:protect(n,'frontend_rollback')
 delete=[n for n,e in entries.items() if n not in keep and e.newest<cutoff and not e.unsafe]
 inodes={}
 for name in delete:
  for p,s in entries[name].files:
   key=(s.st_dev,s.st_ino);v=inodes.setdefault(key,{'links':s.st_nlink,'removed':0,'bytes':getattr(s,'st_blocks',0)*512});v['removed']+=1
 estimate=sum(v['bytes'] for v in inodes.values() if v['links']==v['removed'])
 return Plan(base,app,inode,versions,policy,entries,delete,keep,blocked,warnings,cutoff,estimate)

def apply_plan(plan):
 if plan.blocked:raise SafetyError('; '.join(plan.blocked))
 if safe_base(plan.base)!=plan.base_inode or identity(plan.app)!=plan.identity:raise SafetyError('Backup root or current release changed')
 # Validate every candidate before the first unlink. Hardlink counts/ctime can then change due to our own removals.
 for name in plan.delete:
  if not name or Path(name).name!=name or name in ('.','..'):raise SafetyError('Unsafe backup name')
  p=plan.base/name;fresh=scan(p,plan.base)
  if fresh.unsafe or fresh.signature!=plan.entries[name].signature:raise SafetyError('Candidate changed: '+name)
 before=shutil.disk_usage(plan.base);deleted=[]
 for name in plan.delete:
  p=plan.base/name
  if p.is_symlink() or p.resolve().parent!=plan.base:raise SafetyError('Path boundary changed')
  if p.is_dir():shutil.rmtree(p)
  else:p.unlink()
  deleted.append(name)
 after=shutil.disk_usage(plan.base)
 out=plan.report();out.update({'mode':'apply','deleted':deleted,'deleted_count':len(deleted),'available_bytes_before':before.free,
  'available_bytes_after':after.free,'delta_available_bytes':after.free-before.free,'space_warning':after.free<max(2*1024**3,after.total//10)})
 return out

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');parser.add_argument('--config',type=Path,default=Path('/etc/rpb/backup-retention.json'));args=parser.parse_args()
 import fcntl
 fd=os.open(LOCK,os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
 try:
  try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:print(json.dumps({'status':'skipped_deployment_in_progress','retry_after_seconds':900}));return 75
  config=json.loads(args.config.read_text());policy=Policy(**config)
  if args.apply and not shutil.rmtree.avoids_symlink_attacks:raise SafetyError('Safe descriptor-based recursive deletion is unavailable')
  plan=plan_retention(BACKUPS,APP,RELEASES,policy)
  out=apply_plan(plan) if args.apply else plan.report()
  if args.apply:
   STATE.parent.mkdir(mode=0o700,parents=True,exist_ok=True);temp=STATE.with_suffix('.tmp');temp.write_text(json.dumps(out,indent=2));os.replace(temp,STATE)
  print(json.dumps(out));return 1 if plan.blocked else 2 if out.get('space_warning') else 0
 except (SafetyError,OSError,ValueError,TypeError) as error:
  print(json.dumps({'status':'failed_closed','error':str(error)}));return 1
 finally:os.close(fd)

if __name__=='__main__':raise SystemExit(main())
