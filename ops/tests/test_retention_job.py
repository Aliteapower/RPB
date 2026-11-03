import sys,unittest,os,tempfile,json,zipfile,time,contextlib,io,types
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import rpb_backup_retention as legacy
import rpb_retention_job as job
from rpb_artifact_registry import Paths,Catalog

@unittest.skipIf(os.name=='nt','Linux root/systemd orchestration verified on server fixtures')
class RetentionJobTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name).resolve();self.paths=Paths(*(self.root/n for n in ['backups','releases','staging','catalog']))
  for p in [self.paths.backups,self.paths.releases,self.paths.staging,self.paths.catalog]:p.mkdir()
  self.app=self.root/'app';self.app.mkdir();(self.app/'RELEASE_SHA').write_text('a'*40);(self.app/'FRONTEND_RELEASE_SHA').write_text('b'*40);self.catalog=Catalog(self.paths,secure=False);self.config=self.root/'policy.json';self.config.write_text('{}')
  for ident,age,full in [('full1',1,True),('full2',2,True),('front1',1,False),('front2',2,False),('old',30,False)]:
   p=self.paths.backups/ident;p.mkdir();(p/'frontend').mkdir();(p/'frontend/index.html').write_text('<html>')
   if full:
    with zipfile.ZipFile(p/'reservation-platform.jar','w') as z:z.writestr('META-INF/MANIFEST.MF','Start-Class: com.rpb.reservation.App\n')
    (p/'database.dump').write_bytes(b'PGDMPtest')
   for f in p.rglob('*'):os.utime(f,(time.time()-age*86400,)*2)
   os.utime(p,(time.time()-age*86400,)*2)
  for ident,age in [('recent1',1),('recent2',2),('old',30)]:
   p=self.paths.releases/ident;p.mkdir();(p/'reservation-platform.jar').write_bytes(b'jar');self.catalog.register('release',ident,'c'*40,'c'*40,now=time.time()-age*86400)
 def invoke(self,dry=False):
  space=types.SimpleNamespace(free=10*1024**3,total=40*1024**3,used=30*1024**3);stdout=io.StringIO()
  with patch.object(legacy,'BACKUPS',self.paths.backups),patch.object(legacy,'APP',self.app),patch.object(legacy,'RELEASES',self.paths.releases),patch.object(legacy,'LOCK',self.root/'lock'),patch.object(legacy,'validate_dump',return_value=True),patch.object(job,'Catalog',return_value=self.catalog),patch.object(job,'STATE',self.root/'state'),patch.object(job.shutil,'disk_usage',return_value=space),patch('sys.argv',['job','--apply','--record-dry-run','--config',str(self.config)]),patch.dict(os.environ,{'RPB_RETENTION_DRY_RUN':'1' if dry else '0'}),contextlib.redirect_stdout(stdout):
   code=job.main(legacy)
  return code,json.loads(stdout.getvalue())
 def test_service_dry_run_gate_then_real_apply_then_repeat(self):
  code,p=self.invoke(True);self.assertEqual(code,0);self.assertEqual(p['mode'],'dry-run');self.assertTrue((self.paths.releases/'old').exists());self.assertTrue((self.paths.backups/'old').exists())
  code,p=self.invoke();self.assertEqual(code,0);self.assertEqual(p['managed_deleted'],['release:old']);self.assertEqual(p['deleted'],['old']);self.assertFalse((self.paths.releases/'old').exists());self.assertTrue((self.paths.backups/'full1/database.dump').exists())
  code,p=self.invoke();self.assertEqual(code,0);self.assertFalse(p['managed_deleted']);self.assertFalse(p['deleted'])
 def test_publication_lock_skips_without_deleting(self):
  import fcntl
  with (self.root/'lock').open('w') as f:
   fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB);code,p=self.invoke();self.assertEqual(code,75);self.assertEqual(p['status'],'skipped_deployment_in_progress')
  self.assertTrue((self.paths.releases/'old').exists())

if __name__=='__main__':unittest.main()
