"""Root-owned inventory for deployment artifacts; uploaded files never authorize their own removal."""
from dataclasses import dataclass
from pathlib import Path,PurePosixPath
import hashlib,json,os,re,stat,time,math

class SafetyError(RuntimeError):pass

@dataclass(frozen=True)
class Paths:
 backups:Path=Path('/opt/rpb/backups')
 releases:Path=Path('/opt/rpb/releases')
 staging:Path=Path('/opt/rpb/staging')
 catalog:Path=Path('/var/lib/rpb-backup-retention/catalog')
 def root(self,kind):
  if kind not in ('backup','release','upload'):raise SafetyError('Invalid artifact kind')
  return {'backup':self.backups,'release':self.releases,'upload':self.staging}[kind]

def name(value):
 if not isinstance(value,str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,119}',value) or value in ('.','..'):raise SafetyError('Unsafe artifact identifier')
 return value

def relative(value):
 p=PurePosixPath(value)
 if not isinstance(value,str) or '\\' in value or ':' in value or not value or p.is_absolute() or any(x in ('','.', '..') for x in value.split('/')):raise SafetyError('Unsafe artifact file path')
 return p

def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()

def signature(path):
 s=Path(path).lstat();return [s.st_dev,s.st_ino,s.st_mode,s.st_size,s.st_mtime_ns,s.st_ctime_ns,s.st_nlink]

def canonical_directory(path):
 path=Path(path)
 if not path.is_dir() or path.is_symlink() or path.resolve()!=path:raise SafetyError('Noncanonical directory: '+str(path))
 return path

def inventory(path):
 canonical_directory(path);device=path.stat().st_dev;result={}
 for root,dirs,files in os.walk(path,followlinks=False):
  for item in dirs+files:
   p=Path(root)/item;s=p.lstat()
   if s.st_dev!=device or stat.S_ISLNK(s.st_mode) or not(stat.S_ISDIR(s.st_mode) or stat.S_ISREG(s.st_mode)):raise SafetyError('Symlink, mount or special file')
   if stat.S_ISREG(s.st_mode):
    sig=signature(p);digest=sha(p)
    if signature(p)!=sig:raise SafetyError('Artifact changed while hashing')
    result[p.relative_to(path).as_posix()]={'sha256':digest,'size':s.st_size}
 return result

def permitted(kind,rel):
 p=relative(rel)
 if rel=='.rpb-keep':return True
 if kind=='upload':return rel in ('backend.jar','reservation-platform.jar','frontend.tgz')
 if rel in ('reservation-platform.jar','backend.jar','frontend.tgz','index.html','frontend-version.json','RELEASE_SHA','BACKEND_RELEASE_SHA','FRONTEND_RELEASE_SHA'):return True
 if p.parts[0]=='frontend' and p.suffix.lower() in ('.html','.js','.css','.json','.pdf','.svg','.png','.jpg','.jpeg','.ico','.woff','.woff2','.ttf','.map','.txt'):return True
 return kind=='backup' and rel in ('database.dump','database-at-switch.dump','database-backup.json','before.json','rpb-backend.env')

class Catalog:
 def __init__(self,paths=Paths(),secure=True):self.paths=paths;self.secure=secure
 def _trusted(self,p):
  if p.is_symlink() or p.resolve()!=p:raise SafetyError('Unsafe registry path')
  s=p.stat()
  if self.secure and (s.st_uid!=0 or s.st_mode&0o022):raise SafetyError('Registry must be root-owned and not group/world writable')
 def location(self,r):return self.paths.root(r['kind'])/name(r['id'])
 def records(self):
  if not self.paths.catalog.exists():return {}
  self._trusted(self.paths.catalog);result={}
  for p in sorted(self.paths.catalog.iterdir()):
   self._trusted(p)
   if not p.is_file() or p.suffix!='.json' or p.stat().st_size>4*1024**2:raise SafetyError('Unknown registry content')
   r=json.loads(p.read_text(encoding='utf-8'));self.check(r)
   if p.name!=r['kind']+'--'+r['id']+'.json':raise SafetyError('Registry name mismatch')
   result[r['kind']+':'+r['id']]=r
  return result
 def check(self,r):
  if r.get('version')!=1 or r.get('status') not in ('success','collected'):raise SafetyError('Unsupported registry record')
  self.paths.root(r['kind']);name(r['id'])
  for field in ('backend_sha','frontend_sha'):
   if not re.fullmatch('[a-f0-9]{40}',r[field]):raise SafetyError('Invalid source revision')
  if type(r['successful_at']) not in (int,float) or not math.isfinite(r['successful_at']) or r['successful_at']<=0:raise SafetyError('Invalid success time')
  if not r['files'] or len(r['files'])>20000:raise SafetyError('Invalid artifact inventory')
  for rel,data in r['files'].items():
   if not permitted(r['kind'],rel) or not re.fullmatch('[a-f0-9]{64}',data['sha256']) or type(data['size']) is not int or data['size']<0:raise SafetyError('Invalid artifact manifest')
  for ref in r['rollback']:name(ref)
  for rel,ref in r['canonical'].items():
   relative(rel)
   try:key,file=ref.split('/',1);kind,ident=key.split(':',1)
   except ValueError:raise SafetyError('Invalid original reference')
   if kind not in ('backup','release') or rel not in r['files']:raise SafetyError('Untrusted original')
   name(ident);relative(file)
 def valid(self,r):
  try:return inventory(self.location(r))==r['files']
  except (OSError,SafetyError):return False
 def write(self,r):
  self.check(r);self.paths.catalog.mkdir(mode=0o700,parents=True,exist_ok=True);self._trusted(self.paths.catalog)
  p=self.paths.catalog/(r['kind']+'--'+r['id']+'.json');temp=p.with_suffix('.next')
  fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  try:
   with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(r,f,sort_keys=True);f.flush();os.fsync(f.fileno())
   os.replace(temp,p)
  finally:
   if temp.exists():temp.unlink()
 def register(self,kind,ident,backend_sha,frontend_sha,rollback=(),canonical=None,now=None):
  name(ident);root=canonical_directory(self.paths.root(kind));path=root/ident
  r={'version':1,'kind':kind,'id':ident,'backend_sha':backend_sha,'frontend_sha':frontend_sha,'rollback':list(rollback),'canonical':canonical or {},'successful_at':time.time() if now is None else now,'status':'success','files':inventory(path)}
  self.check(r);old=self.records().get(kind+':'+ident)
  if old:
   if old['status']!='success' or any(old[k]!=r[k] for k in r if k!='successful_at'):raise SafetyError('Immutable registration differs')
   return old
  self.write(r);return r
 def collected(self,r):self.write(dict(r,status='collected',collected_at=time.time()))

def safe_remove_tree(path,expected):
 """Only exact manifest files, with no-follow parent descriptors; never recursive rmtree."""
 canonical_directory(path);dirs=[]
 for root,children,files in os.walk(path,followlinks=False):
  dirs.append(Path(root))
 for rel,data in expected.items():
  p=path/relative(rel);parent=canonical_directory(p.parent)
  fd=os.open('/',os.O_RDONLY|os.O_DIRECTORY)
  try:
   for component in parent.parts[1:]:
    nxt=os.open(component,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW,dir_fd=fd);os.close(fd);fd=nxt
  except BaseException:os.close(fd);raise
  try:
   s=os.stat(p.name,dir_fd=fd,follow_symlinks=False)
   if not stat.S_ISREG(s.st_mode) or sha(p)!=data['sha256']:raise SafetyError('File changed at unlink')
   os.unlink(p.name,dir_fd=fd)
  finally:os.close(fd)
 for p in sorted(dirs,key=lambda p:len(p.parts),reverse=True):p.rmdir()
