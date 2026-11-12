import importlib.util,sys,unittest,tempfile,time,json,hashlib,os,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from rpb_artifact_registry import Paths,Catalog,SafetyError
from rpb_artifact_retention import plan_managed,validate_managed,apply_managed

class ArtifactRetentionTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name).resolve();self.paths=Paths(self.root/'backups',self.root/'releases',self.root/'staging',self.root/'catalog');self.now=time.time();self.current={'RELEASE_SHA':'a'*40,'FRONTEND_RELEASE_SHA':'b'*40}
  for p in [self.paths.backups,self.paths.releases,self.paths.staging,self.paths.catalog]:p.mkdir()
  self.c=Catalog(self.paths,secure=False)
 def add(self,kind,name,days=30,sha='c',rollback=(),canonical=None):
  p=self.paths.root(kind)/name;p.mkdir();(p/'reservation-platform.jar').write_bytes(b'jar-'+sha.encode())
  if kind!='upload':
   (p/'frontend').mkdir();(p/'frontend/index.html').write_text('<html>'+sha+'</html>')
  self.c.register(kind,name,sha*40,sha*40,rollback,canonical,now=self.now-days*86400)
  return p
 def plan(self):return plan_managed(self.c,self.current,now=self.now)
 def seed(self):
  self.add('release','new-1',1,'d');self.add('release','new-2',2,'e')
 def test_default_plan_is_read_only_and_unknown_directory_is_held(self):
  self.seed();old=self.add('release','old');unknown=self.paths.releases/'unknown';unknown.mkdir();(unknown/'production.log').write_text('keep')
  p=self.plan();self.assertIn('release:old',p['delete']);self.assertTrue(old.exists());self.assertIn(str(unknown),p['unknown'])
 def test_keep_current_backend_and_frontend_even_when_different(self):
  self.seed();self.add('release','backend',40,'a');self.add('release','frontend',41,'b');p=self.plan();self.assertNotIn('release:backend',p['delete']);self.assertNotIn('release:frontend',p['delete'])
 def test_latest_two_successful_pairs_survive_even_after_retention(self):
  self.add('release','one',20,'d');self.add('release','two',21,'e');self.add('release','three',22,'f');self.assertEqual(self.plan()['delete'],['release:three'])
 def test_exact_fifteen_day_boundary_is_retained(self):
  self.seed();self.add('release','boundary',15);self.assertNotIn('release:boundary',self.plan()['delete'])
 def test_rollback_reference_is_protected(self):
  self.seed();self.add('backup','rollback',60);self.add('release','current',50,'a',rollback=('rollback',));self.assertIn('backup:rollback',self.plan()['protected'])
 def test_missing_rollback_aborts_cleanup(self):
  self.seed();self.add('release','old');self.add('release','current',50,'a',rollback=('missing',));self.assertTrue(self.plan()['blocked'])
 def test_upload_reclaims_only_after_day_and_keeps_canonical_release(self):
  self.seed();self.add('release','original',40);self.add('upload','copy',2,canonical={'reservation-platform.jar':'release:original/reservation-platform.jar'});p=self.plan();self.assertIn('upload:copy',p['delete']);self.assertNotIn('release:original',p['delete'])
 def test_recent_upload_and_changed_original_are_retained(self):
  self.seed();original=self.add('release','original',40);self.add('upload','copy',0.5,canonical={'reservation-platform.jar':'release:original/reservation-platform.jar'});self.assertNotIn('upload:copy',self.plan()['delete']);(original/'reservation-platform.jar').write_bytes(b'changed');self.assertNotIn('upload:copy',self.plan()['delete'])
 def test_unexpected_file_pdf_or_log_never_becomes_candidate(self):
  self.seed();old=self.add('release','old');(old/'unexpected.log').write_text('keep');p=self.plan();self.assertNotIn('release:old',p['delete'])
 def test_known_frontend_pdf_requires_trusted_exact_manifest(self):
  self.seed();p=self.paths.backups/'new-format';p.mkdir();(p/'frontend').mkdir();(p/'frontend/guide.pdf').write_bytes(b'%PDF-test');(p/'before.json').write_text('{}');self.c.register('backup','new-format','c'*40,'c'*40,now=self.now);self.assertTrue(self.c.valid(self.c.records()['backup:new-format']))
 def test_symlink_escape_is_retained(self):
  self.seed();p=self.add('release','old');outside=self.root/'keep';outside.write_text('keep')
  try:(p/'link').symlink_to(outside)
  except OSError:self.skipTest('symlink unavailable')
  self.assertNotIn('release:old',self.plan()['delete'])
 def test_invalid_registry_path_is_rejected(self):
  for name in ['../app','/etc','x/y','x\\y']:
   with self.assertRaises(SafetyError):self.c.register('release',name,'a'*40,'b'*40,now=self.now)
 def test_untrusted_canonical_path_cannot_register(self):
  p=self.paths.staging/'copy';p.mkdir();(p/'reservation-platform.jar').write_bytes(b'jar')
  with self.assertRaises(SafetyError):self.c.register('upload','copy','a'*40,'b'*40,canonical={'reservation-platform.jar':'../../etc/passwd'},now=self.now)
 def test_mutation_before_apply_aborts_all(self):
  self.seed();old=self.add('release','old');p=self.plan();(old/'reservation-platform.jar').write_bytes(b'changed')
  with self.assertRaises(SafetyError):validate_managed(p,self.c,self.current)
  self.assertTrue(old.exists())
 def test_version_drift_aborts(self):
  self.seed();self.add('release','old');p=self.plan()
  with self.assertRaises(SafetyError):validate_managed(p,self.c,dict(self.current,RELEASE_SHA='f'*40))
 def test_collected_record_is_not_reprocessed_and_orphan_is_not_guessed(self):
  self.seed();self.add('release','old');record=self.c.records()['release:old'];self.c.collected(record);self.assertNotIn('release:old',self.plan()['delete'])
 def test_re_registration_is_idempotent_not_a_retention_reset(self):
  self.add('release','old');before=self.c.records()['release:old'];self.c.register('release','old','c'*40,'c'*40,now=self.now);self.assertEqual(before['successful_at'],self.c.records()['release:old']['successful_at'])
 def test_no_two_successful_release_pairs_blocks_new_release_deletion(self):
  self.add('release','only',30);self.assertNotIn('release:only',self.plan()['delete'])
 def test_manual_keep_marker_is_respected(self):
  self.seed();p=self.add('release','old');(p/'.rpb-keep').write_text('keep');self.assertNotIn('release:old',self.plan()['delete'])
 def test_unchanged_backend_does_not_pin_all_old_frontend_releases(self):
  for ident,days,front in [('one',1,'b'),('two',2,'d'),('old',40,'e')]:
   p=self.paths.releases/ident;p.mkdir();(p/'reservation-platform.jar').write_bytes(b'jar');self.c.register('release',ident,'a'*40,front*40,now=self.now-days*86400)
  self.assertIn('release:old',self.plan()['delete'])
 def test_missing_registered_tree_blocks_cleanup_after_interruption(self):
  self.seed();p=self.add('release','old');(p/'reservation-platform.jar').unlink();(p/'frontend/index.html').unlink();(p/'frontend').rmdir();p.rmdir();self.assertTrue(self.plan()['blocked'])
 def test_original_release_rollback_is_protected_for_duplicate_upload(self):
  self.seed();self.add('backup','fallback',60);self.add('release','original',40,rollback=('fallback',));self.add('upload','copy',2,canonical={'reservation-platform.jar':'release:original/reservation-platform.jar'});self.assertTrue(any('release-rollback' in reason for reason in self.plan()['protected']['backup:fallback']))
 @unittest.skipIf(os.name=='nt','POSIX ownership check')
 def test_group_writable_registry_cannot_authorize_deletion(self):
  self.add('release','old');self.paths.catalog.chmod(0o777)
  with self.assertRaises(SafetyError):Catalog(self.paths).records()
 def test_manually_pinned_noncurrent_release_also_pins_rollback(self):
  self.seed();self.add('backup','fallback',60);p=self.add('release','manual',60,rollback=('fallback',));(p/'.rpb-keep').write_text('hold');self.assertTrue(any('rollback' in reason for reason in self.plan()['protected']['backup:fallback']))

if __name__=='__main__':unittest.main()
