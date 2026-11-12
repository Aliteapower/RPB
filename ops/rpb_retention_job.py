"""One locked decision over legacy backups and registered deployment artifacts."""
from pathlib import Path
import argparse,dataclasses,json,os,shutil,sys,time
from rpb_artifact_registry import Paths,Catalog,SafetyError
from rpb_artifact_retention import plan_managed,validate_managed,apply_managed

STATE=Path('/var/lib/rpb-backup-retention')
DEFAULTS={'managed_enabled':True,'release_retention_days':15,'keep_releases':2,'upload_retention_hours':24,'warning_free_bytes':5*1024**3,'critical_free_bytes':2*1024**3}

def atomic_json(path,data):
 path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
 if path.is_symlink() or path.parent.is_symlink():raise SafetyError('Unsafe state path')
 tmp=path.with_suffix('.next');fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 try:
  with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(data,f,sort_keys=True);f.flush();os.fsync(f.fileno())
  os.replace(tmp,path)
 finally:
  if tmp.exists():tmp.unlink()

def configuration(data,legacy):
 fields={f.name for f in dataclasses.fields(legacy.Policy)};extra=set(data)-fields-set(DEFAULTS)
 if extra:raise SafetyError('Unknown policy keys: '+','.join(sorted(extra)))
 settings=DEFAULTS|{k:v for k,v in data.items() if k in DEFAULTS}
 if type(settings['managed_enabled']) is not bool or any(type(settings[k]) is not int for k in DEFAULTS if k!='managed_enabled'):raise SafetyError('Invalid policy types')
 if settings['release_retention_days']<15 or settings['keep_releases']<2 or settings['upload_retention_hours']<24 or settings['critical_free_bytes']<2*1024**3 or settings['warning_free_bytes']<=settings['critical_free_bytes']:raise SafetyError('Unsafe retention/space thresholds')
 policy=legacy.Policy(**{k:v for k,v in data.items() if k in fields});policy.validate();return policy,settings

def alert(space,settings,blocked=()):
 level='critical' if blocked or space.free<settings['critical_free_bytes'] else 'warning' if space.free<settings['warning_free_bytes'] else 'ok'
 return {'level':level,'available_bytes':space.free,'reasons':list(blocked),'checked_at':time.time()}

def main(legacy):
 import fcntl,syslog
 p=argparse.ArgumentParser();p.add_argument('--apply',action='store_true');p.add_argument('--record-dry-run',action='store_true');p.add_argument('--config',type=Path,default=Path('/etc/rpb/backup-retention.json'));args=p.parse_args()
 if os.geteuid()!=0:print(json.dumps({'status':'failed_closed','error':'Root required'}));return 1
 fd=os.open(legacy.LOCK,os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
 try:
  try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:print(json.dumps({'status':'skipped_deployment_in_progress','retry_after_seconds':900}));return 75
  policy,settings=configuration(json.loads(args.config.read_text()),legacy);catalog=Catalog(Paths());versions=legacy.identity(legacy.APP);now=time.time()
  managed=plan_managed(catalog,versions,settings['release_retention_days'],settings['keep_releases'],settings['upload_retention_hours'],now=now)
  pins=set(policy.pinned_backups)
  for key,reasons in managed['protected'].items():
   if key.startswith('backup:') and any(r!='backup-policy-owned' for r in reasons):pins.add(key.split(':',1)[1])
  policy=dataclasses.replace(policy,pinned_backups=tuple(sorted(pins)))
  trusted={r['id']:r for k,r in managed['records'].items() if r['kind']=='backup' and k in managed['snapshots']}
  backups=legacy.plan_retention(legacy.BACKUPS,legacy.APP,legacy.RELEASES,policy,now=now,trusted_backups=trusted)
  blocked=backups.blocked+managed['blocked'];execute=args.apply and os.environ.get('RPB_RETENTION_DRY_RUN')!='1'
  out=backups.report();out['managed']={k:v for k,v in managed.items() if k not in ('records','snapshots')};out['managed_enabled']=settings['managed_enabled'];out['mode']='apply' if execute else 'dry-run';out['blocked']=blocked
  if execute:
   if not shutil.rmtree.avoids_symlink_attacks:raise SafetyError('Descriptor-safe legacy deletion unavailable')
   if blocked:raise SafetyError('; '.join(blocked))
   validate_managed(managed,catalog,legacy.identity(legacy.APP))
   # All legacy candidates validated before any destructive step.
   for n in backups.delete:
    fresh=legacy.scan(legacy.BACKUPS/n,legacy.BACKUPS,trusted.get(n))
    if fresh.unsafe or fresh.signature!=backups.entries[n].signature:raise SafetyError('Backup changed: '+n)
   before=shutil.disk_usage('/opt/rpb').free
   out['managed_deleted']=apply_managed(managed,catalog,legacy.identity(legacy.APP)) if settings['managed_enabled'] else []
   # Our own hardlink removals may change ctime/nlink. Replan without widening the approved deletion set.
   fresh=legacy.plan_retention(legacy.BACKUPS,legacy.APP,legacy.RELEASES,policy,now=now,trusted_backups=trusted)
   if fresh.delete!=backups.delete:raise SafetyError('Backup deletion set changed')
   result=legacy.apply_plan(fresh)
   for n in result['deleted']:
    r=managed['records'].get('backup:'+n)
    if r:catalog.collected(r)
   out.update({'deleted':result['deleted'],'deleted_count':len(result['deleted']),'available_bytes_before':before,'available_bytes_after':shutil.disk_usage('/opt/rpb').free})
  state=alert(shutil.disk_usage('/opt/rpb'),settings,blocked);out['capacity']=state
  if execute or args.record_dry_run:
   atomic_json(STATE/('last-run.json' if execute else 'last-dry-run.json'),out);atomic_json(STATE/'alert.json',state)
   if state['level']!='ok':syslog.syslog(syslog.LOG_ERR if state['level']=='critical' else syslog.LOG_WARNING,'RPB retention '+json.dumps(state))
  print(json.dumps(out));return 1 if blocked else 2 if state['level']!='ok' else 0
 except (OSError,ValueError,TypeError,KeyError,SafetyError,legacy.SafetyError) as e:
  failure={'status':'failed_closed','error':str(e),'checked_at':time.time()}
  if args.apply or args.record_dry_run:atomic_json(STATE/'alert.json',dict(failure,level='critical'))
  print(json.dumps(failure));return 1
 finally:os.close(fd)
