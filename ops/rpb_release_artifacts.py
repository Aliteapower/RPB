#!/usr/bin/env python3
"""Deployment artifact lifecycle. Does not switch or restart the business application."""
from pathlib import Path,PurePosixPath
import argparse,json,os,re,shutil,stat,subprocess,tarfile,time
from rpb_artifact_registry import Paths,Catalog,SafetyError,name,sha,inventory,permitted,canonical_directory

APP=Path('/opt/rpb/app');LIVE=Path('/opt/rpb/frontend');RESERVE=2*1024**3

def budget(required,root=Path('/opt/rpb'),available=None):
 if type(required) is not int or required<0:raise SafetyError('Invalid release budget')
 free=shutil.disk_usage(root).free if available is None else available
 if free<required+RESERVE:raise SafetyError('Release budget requires artifact bytes plus 2 GiB reserve')
 return {'available_bytes':free,'required_bytes':required,'reserved_bytes':RESERVE}

def clone(source,destination,catalog):
 """Reuse an exact immutable registered copy; do not chown/chmod a shared inode."""
 expected=sha(source)
 for r in catalog.records().values():
  if r['status']!='success' or r['kind']=='upload':continue
  for rel,data in r['files'].items():
   p=catalog.location(r)/rel
   if data['sha256']==expected and p.is_file() and not p.is_symlink() and sha(p)==expected:
    os.link(p,destination);return
 shutil.copyfile(source,destination)
 if sha(destination)!=expected:raise SafetyError('Source changed while copying')

def frontend_archive(archive):
 with tarfile.open(archive,'r:gz') as t:
  members=t.getmembers();total=sum(m.size for m in members)
  if len(members)>20000 or total>256*1024**2:raise SafetyError('Frontend archive too large')
  names=[]
  for m in members:
   rel=m.name
   if rel.startswith('./'):rel=rel[2:]
   p=PurePosixPath(rel)
   if p.is_absolute() or '..' in p.parts or '\\' in rel or not(m.isfile() or m.isdir()):raise SafetyError('Unsafe frontend archive member')
   if m.isfile():
    if not permitted('release','frontend/'+rel):raise SafetyError('Unknown frontend file')
    names.append(rel)
  if len(names)!=len(set(names)) or 'index.html' not in names or 'frontend-version.json' not in names:raise SafetyError('Incomplete frontend archive')
 return total

def extract_frontend(archive,dest):
 frontend_archive(archive);dest.mkdir()
 with tarfile.open(archive,'r:gz') as t:
  for m in t.getmembers():
   if not m.isfile():continue
   rel=m.name[2:] if m.name.startswith('./') else m.name;p=dest/rel;p.parent.mkdir(parents=True,exist_ok=True)
   with t.extractfile(m) as src,p.open('xb') as target:shutil.copyfileobj(src,target)

def prepare(catalog,ident,upload,backend_sha,frontend_sha):
 if not all(re.fullmatch('[a-f0-9]{40}',s) for s in (backend_sha,frontend_sha)):raise SafetyError('Invalid revision')
 name(ident);name(upload);source=canonical_directory(catalog.paths.staging/upload);archive=source/'frontend.tgz'
 allowed={'backend.jar','frontend.tgz'}
 if any(p.name not in allowed or not p.is_file() or p.is_symlink() for p in source.iterdir()):raise SafetyError('Unknown upload content')
 from rpb_backup_retention import rpb_jar
 backend=source/'backend.jar' if (source/'backend.jar').exists() else APP/'reservation-platform.jar'
 if backend.parent==APP and backend_sha!=(APP/'RELEASE_SHA').read_text().strip():raise SafetyError('Unchanged backend revision mismatch')
 if not rpb_jar(backend):raise SafetyError('Invalid RPB backend')
 size=frontend_archive(archive);budget(backend.stat().st_size+archive.stat().st_size+2*size)
 dest=catalog.paths.releases/ident
 if dest.exists():raise SafetyError('Release directory already exists')
 dest.mkdir(mode=0o755);clone(backend,dest/'reservation-platform.jar',catalog);clone(archive,dest/'frontend.tgz',catalog);extract_frontend(archive,dest/'frontend')
 if json.loads((dest/'frontend/frontend-version.json').read_text())['version']!=frontend_sha:raise SafetyError('Frontend revision mismatch')
 (dest/'RELEASE_SHA').write_text(backend_sha+'\n');(dest/'FRONTEND_RELEASE_SHA').write_text(frontend_sha+'\n')
 return {'prepared':str(dest),'status':'not-registered-until-live-verification'}

def register_release(catalog,ident,rollback=()):
 if not rollback:raise SafetyError('At least one explicit rollback reference required')
 p=catalog.paths.releases/name(ident)
 backend=(APP/'RELEASE_SHA').read_text().strip();frontend=(APP/'FRONTEND_RELEASE_SHA').read_text().strip()
 if (p/'RELEASE_SHA').read_text().strip()!=backend or (p/'FRONTEND_RELEASE_SHA').read_text().strip()!=frontend:raise SafetyError('Live revisions differ')
 if sha(p/'reservation-platform.jar')!=sha(APP/'reservation-platform.jar'):raise SafetyError('Live backend differs')
 if json.loads((p/'frontend/frontend-version.json').read_text())['version']!=frontend:raise SafetyError('Frontend metadata revision mismatch')
 for rel,data in inventory(p/'frontend').items():
  if sha(LIVE/rel)!=data['sha256']:raise SafetyError('Live frontend differs: '+rel)
 if subprocess.check_output(['systemctl','is-active','rpb-backend'],text=True).strip()!='active':raise SafetyError('Backend not active')
 import urllib.request,urllib.error
 try:urllib.request.urlopen('http://127.0.0.1:8080/api/v1/auth/me',timeout=5);raise SafetyError('Unexpected readiness response')
 except urllib.error.HTTPError as e:
  if e.code!=401:raise SafetyError('Backend not ready')
 for ref in rollback:canonical_directory(catalog.paths.backups/name(ref))
 return catalog.register('release',ident,backend,frontend,rollback)

def register_upload(catalog,ident,release):
 records=catalog.records();r=records.get('release:'+name(release))
 if not r or r['status']!='success' or not catalog.valid(r):raise SafetyError('Canonical successful release unavailable')
 path=canonical_directory(catalog.paths.staging/name(ident));files=inventory(path);canonical={}
 for rel,data in files.items():
  if not permitted('upload',rel):raise SafetyError('Unknown upload file')
  match=next((file for file,v in r['files'].items() if v==data),None)
  if not match:raise SafetyError('Upload has no exact retained original')
  canonical[rel]='release:'+release+'/'+match
 # Seal only the managed, exact upload directory. Never change shared inode ownership.
 for rel in files:
  p=path/rel
  if p.stat().st_nlink!=1:raise SafetyError('Upload must not have external hardlinks')
  os.chown(p,0,0);os.chmod(p,0o644)
 os.chown(path,0,0);os.chmod(path,0o755)
 return catalog.register('upload',ident,r['backend_sha'],r['frontend_sha'],canonical=canonical)

def snapshot(catalog,ident,release,database=False):
 r=catalog.records().get('release:'+name(release))
 if not r or r['status']!='success' or not catalog.valid(r):raise SafetyError('Current registered release required')
 if r['backend_sha']!=(APP/'RELEASE_SHA').read_text().strip() or r['frontend_sha']!=(APP/'FRONTEND_RELEASE_SHA').read_text().strip():raise SafetyError('Snapshot is not current')
 size=sum(v['size'] for rel,v in r['files'].items() if rel.startswith('frontend/'))
 estimate=int(subprocess.check_output(['sudo','-n','-u','postgres','psql','-AtXq','-d','rpb_acceptance','-c','select pg_database_size(current_database())'],text=True)) if database else 0
 budget(2*size+estimate)
 dest=catalog.paths.backups/name(ident)
 if dest.exists():raise SafetyError('Backup already exists')
 dest.mkdir(mode=0o700);pin=dest/'.rpb-keep';pin.write_text('Unfinished snapshot; inspect before release\n')
 try:
  for rel in ['reservation-platform.jar','RELEASE_SHA','FRONTEND_RELEASE_SHA']+[f for f in r['files'] if f.startswith('frontend/')]:
   target=dest/rel;target.parent.mkdir(parents=True,exist_ok=True);clone(catalog.location(r)/rel,target,catalog)
  if database:
   with (dest/'database.dump').open('xb') as f:subprocess.run(['sudo','-n','-u','postgres','pg_dump','-Fc','--no-owner','--no-acl','-d','rpb_acceptance'],stdout=f,check=True)
   from rpb_backup_retention import validate_dump
   if not validate_dump(dest/'database.dump'):raise SafetyError('Backup dump invalid')
  pin.unlink()
  return catalog.register('backup',ident,r['backend_sha'],r['frontend_sha'])
 except BaseException:
  pin.write_text('Incomplete or failed snapshot; inspect before release\n');raise

def main():
 import fcntl
 parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
 q=sub.add_parser('incoming');q.add_argument('--id',required=True)
 q=sub.add_parser('budget');q.add_argument('--required-bytes',type=int,required=True)
 q=sub.add_parser('prepare');q.add_argument('--id',required=True);q.add_argument('--upload-id',required=True);q.add_argument('--backend-sha',required=True);q.add_argument('--frontend-sha',required=True)
 q=sub.add_parser('complete');q.add_argument('--id',required=True);q.add_argument('--rollback',action='append',default=[]);q.add_argument('--upload-id')
 q=sub.add_parser('snapshot');q.add_argument('--id',required=True);q.add_argument('--release-id',required=True);q.add_argument('--database',action='store_true')
 args=parser.parse_args()
 if os.geteuid()!=0:raise SafetyError('Root required')
 catalog=Catalog();catalog.paths.staging.mkdir(mode=0o755,exist_ok=True);canonical_directory(catalog.paths.staging)
 fd=os.open('/home/ubuntu/rpb-ledger-release.lock',os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
 try:
  fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
  if args.command=='incoming':
   path=catalog.paths.staging/name(args.id);path.mkdir(mode=0o700);os.chown(path,int(os.environ.get('SUDO_UID','0')),int(os.environ.get('SUDO_GID','0')));result={'upload':str(path)}
  elif args.command=='budget':result=budget(args.required_bytes)
  elif args.command=='prepare':result=prepare(catalog,args.id,args.upload_id,args.backend_sha,args.frontend_sha)
  elif args.command=='complete':
   result=register_release(catalog,args.id,args.rollback)
   if args.upload_id:register_upload(catalog,args.upload_id,args.id)
  else:result=snapshot(catalog,args.id,args.release_id,args.database)
  print(json.dumps(result))
 finally:os.close(fd)

if __name__=='__main__':
 try:main()
 except (OSError,ValueError,KeyError,SafetyError) as e:print(json.dumps({'status':'failed_closed','error':str(e)}));raise SystemExit(1)
