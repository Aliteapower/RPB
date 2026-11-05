import sys,unittest,tempfile,tarfile,io,json,zipfile,os,time
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from rpb_artifact_registry import Paths,Catalog,SafetyError
import rpb_release_artifacts as tools
import rpb_artifact_retention as managed
import rpb_backup_retention as legacy
from rpb_retention_job import configuration,alert

class ReleaseArtifactTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name).resolve();self.paths=Paths(*(self.root/n for n in ['backups','releases','staging','catalog']))
  for p in [self.paths.backups,self.paths.releases,self.paths.staging,self.paths.catalog]:p.mkdir()
  self.c=Catalog(self.paths,secure=False);self.sha='a'*40;self.app=self.root/'app';self.app.mkdir();(self.app/'RELEASE_SHA').write_text(self.sha);(self.app/'FRONTEND_RELEASE_SHA').write_text(self.sha);self.jar(self.app/'reservation-platform.jar')
 def jar(self,p):
  with zipfile.ZipFile(p,'w') as z:z.writestr('META-INF/MANIFEST.MF','Start-Class: com.rpb.reservation.ReservationPlatformApplication\n')
 def archive(self,p,items=None):
  with tarfile.open(p,'w:gz') as t:
   for name,data in (items or {'index.html':b'<html>app</html>','frontend-version.json':json.dumps({'version':self.sha}).encode()}).items():
    m=tarfile.TarInfo(name);m.size=len(data);t.addfile(m,io.BytesIO(data))
 def release(self,name='current'):
  p=self.paths.releases/name;p.mkdir();self.jar(p/'reservation-platform.jar');(p/'RELEASE_SHA').write_text(self.sha);(p/'FRONTEND_RELEASE_SHA').write_text(self.sha);(p/'frontend').mkdir();(p/'frontend/index.html').write_text('<html>app</html>');(p/'frontend/frontend-version.json').write_text(json.dumps({'version':self.sha}));return self.c.register('release',name,self.sha,self.sha)
 def test_budget_enforces_reserve_and_rejects_negative(self):
  with self.assertRaises(SafetyError):tools.budget(1,available=2*1024**3)
  with self.assertRaises(SafetyError):tools.budget(-1,available=10*1024**3)
  self.assertEqual(tools.budget(1,available=3*1024**3)['reserved_bytes'],2*1024**3)
 def test_archive_traversal_special_files_and_unknown_originals_rejected(self):
  for name in ['../../outside.js','/outside.js','C:/outside.js','a/../outside.js','secret.env','customer.docx','assets//a.js']:
   p=self.root/'f.tgz';self.archive(p,{'index.html':b'<html>','frontend-version.json':b'{}',name:b'x'})
   with self.assertRaises(SafetyError,msg=name):tools.frontend_archive(p)
 def test_preparation_copies_only_supplied_frontend_and_does_not_register_success(self):
  incoming=self.paths.staging/'in';incoming.mkdir();self.archive(incoming/'frontend.tgz')
  with patch.object(tools,'APP',self.app),patch.object(tools,'budget',return_value={}):tools.prepare(self.c,'new','in',self.sha,self.sha)
  self.assertTrue((self.paths.releases/'new/frontend/index.html').exists());self.assertNotIn('release:new',self.c.records())
 def test_preparation_rejects_unknown_upload_and_backend_revision_lie(self):
  incoming=self.paths.staging/'in';incoming.mkdir();self.archive(incoming/'frontend.tgz');(incoming/'notes.log').write_text('keep')
  with self.assertRaises(SafetyError):tools.prepare(self.c,'new','in',self.sha,self.sha)
  (incoming/'notes.log').unlink()
  with patch.object(tools,'APP',self.app),self.assertRaises(SafetyError):tools.prepare(self.c,'new','in','b'*40,self.sha)
 def test_snapshot_uses_version_inventory_not_live_historical_assets(self):
  self.release();live=self.root/'live';live.mkdir();(live/'historical.js').write_text('keep')
  with patch.object(tools,'APP',self.app),patch.object(tools,'LIVE',live),patch.object(tools,'budget',return_value={}):tools.snapshot(self.c,'before','current')
  backup=self.paths.backups/'before';self.assertTrue((backup/'frontend/index.html').exists());self.assertFalse((backup/'frontend/historical.js').exists());self.assertTrue((live/'historical.js').exists())
 def test_clone_reuses_registered_inode_without_invalidating_manifest(self):
  r=self.release();original=self.c.location(r)/'reservation-platform.jar';copy=self.root/'copy.jar';tools.clone(original,copy,self.c);self.assertEqual(original.stat().st_ino,copy.stat().st_ino);self.assertTrue(self.c.valid(r))
 def test_new_backup_pdf_and_metadata_are_only_trusted_with_exact_registration(self):
  r=self.release();p=self.paths.backups/'new';p.mkdir();self.jar(p/'reservation-platform.jar');(p/'frontend').mkdir();(p/'frontend/index.html').write_text('<html>');(p/'frontend/guide.pdf').write_bytes(b'%PDF-');(p/'before.json').write_text('{}');(p/'database.dump').write_bytes(b'PGDMPtest')
  self.assertTrue(legacy.scan(p,self.paths.backups).unsafe)
  record=self.c.register('backup','new',self.sha,self.sha);entry=legacy.scan(p,self.paths.backups,record);self.assertFalse(entry.unsafe);self.assertTrue(entry.backend and entry.frontend and entry.databases)
  (p/'original.log').write_text('keep')
  self.assertTrue(legacy.scan(p,self.paths.backups,record).unsafe)
 def test_configuration_keeps_legacy_defaults_and_rejects_unsafe_extensions(self):
  p,s=configuration({'retention_days':15,'keep_full':2,'keep_frontend':2},legacy);self.assertEqual(p.keep_full,2);self.assertTrue(s['managed_enabled'])
  for setting in [{'release_retention_days':1},{'upload_retention_hours':1},{'keep_releases':1},{'unknown':True}]:
   with self.assertRaises(SafetyError):configuration(setting,legacy)
 def test_capacity_alert_does_not_override_protection(self):
  class Space:free=1024
  _,s=configuration({},legacy);self.assertEqual(alert(Space(),s)['level'],'critical')
 def test_registry_rejects_unknown_frontend_logs_and_forged_paths(self):
  p=self.paths.releases/'bad';p.mkdir();(p/'debug.log').write_text('keep')
  with self.assertRaises(SafetyError):self.c.register('release','bad',self.sha,self.sha)
 @unittest.skipIf(os.name=='nt','POSIX descriptor unlink validated on Linux')
 def test_real_apply_only_removes_expired_registered_tree_and_is_idempotent(self):
  for ident,age in [('one',1),('two',2),('old',30)]:
   p=self.paths.releases/ident;p.mkdir();self.jar(p/'reservation-platform.jar');self.c.register('release',ident,'c'*40,'c'*40,now=time.time()-age*86400)
  versions={'RELEASE_SHA':self.sha,'FRONTEND_RELEASE_SHA':self.sha};plan=managed.plan_managed(self.c,versions);self.assertEqual(managed.apply_managed(plan,self.c,versions),['release:old']);self.assertFalse((self.paths.releases/'old').exists());self.assertFalse(managed.plan_managed(self.c,versions)['delete'])
 def test_runtime_path_protects_old_registered_release(self):
  r=self.release('old');now=time.time();self.c.records();self.c.write(dict(r,successful_at=now-40*86400))
  for n in ['one','two']:self.release(n)
  p=managed.plan_managed(self.c,{'RELEASE_SHA':'c'*40,'FRONTEND_RELEASE_SHA':'d'*40},active_paths=[str(self.paths.releases/'old')]);self.assertNotIn('release:old',p['delete'])
 def test_success_registration_requires_explicit_rollback(self):
  with self.assertRaises(SafetyError):tools.register_release(self.c,'not-yet-live')
 def test_failed_database_snapshot_keeps_manual_pin_and_is_not_registered(self):
  self.release()
  def dump(*args,**kwargs):kwargs['stdout'].write(b'PGDMPpartial')
  with patch.object(tools,'APP',self.app),patch.object(tools,'budget',return_value={}),patch.object(tools.subprocess,'check_output',return_value='1024'),patch.object(tools.subprocess,'run',side_effect=dump),patch.object(legacy,'validate_dump',return_value=False),self.assertRaises(SafetyError):tools.snapshot(self.c,'failed','current',database=True)
  path=self.paths.backups/'failed';self.assertTrue((path/'.rpb-keep').exists());self.assertNotIn('backup:failed',self.c.records());self.assertTrue(legacy.scan(path,self.paths.backups).pin)
 def test_snapshot_registration_failure_restores_pending_pin(self):
  self.release();original=self.c.register
  def register(kind,*args,**kwargs):
   if kind=='backup':raise SafetyError('Registry unavailable')
   return original(kind,*args,**kwargs)
  with patch.object(tools,'APP',self.app),patch.object(tools,'budget',return_value={}),patch.object(self.c,'register',side_effect=register),self.assertRaises(SafetyError):tools.snapshot(self.c,'failed','current')
  self.assertTrue((self.paths.backups/'failed/.rpb-keep').exists())

if __name__=='__main__':unittest.main()
