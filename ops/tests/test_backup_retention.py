import importlib.util
import io
import os
from pathlib import Path
import shutil
import sys
import tarfile
import tempfile
import time
import unittest
import zipfile

SCRIPT=Path(__file__).resolve().parents[1]/'rpb_backup_retention.py'
spec=importlib.util.spec_from_file_location('rpb_backup_retention',SCRIPT)
retention=importlib.util.module_from_spec(spec);sys.modules[spec.name]=retention;spec.loader.exec_module(retention)

class RetentionTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve();self.backups=self.root/'backups';self.backups.mkdir();self.app=self.root/'app';self.app.mkdir();self.releases=self.root/'releases';self.releases.mkdir();self.now=time.time();self.backend='a'*40;self.frontend='b'*40
  (self.app/'RELEASE_SHA').write_text(self.backend);(self.app/'FRONTEND_RELEASE_SHA').write_text(self.frontend)
  self.backup('full-new',1,True);self.backup('full-second',2,True)
 def age(self,p,days):
  stamp=self.now-days*86400
  for f in sorted(p.rglob('*'),reverse=True) if p.is_dir() else []:os.utime(f,(stamp,stamp))
  os.utime(p,(stamp,stamp))
 def backup(self,name,days,full=False,frontend=True):
  p=self.backups/name;p.mkdir()
  if frontend:(p/'frontend').mkdir();(p/'frontend/index.html').write_text('<html>RPB</html>')
  if full:
   with zipfile.ZipFile(p/'reservation-platform.jar','w') as z:z.writestr('META-INF/MANIFEST.MF','Start-Class: com.rpb.reservation.ReservationPlatformApplication\n')
   (p/'database.dump').write_bytes(b'PGDMP'+b'test');(p/'RELEASE_SHA').write_text('c'*40)
  self.age(p,days);return p
 def plan(self,**options):return retention.plan_retention(self.backups,self.app,self.releases,retention.Policy(**options),self.now,db_validator=lambda p:p.read_bytes().startswith(b'PGDMP'))
 def test_recent_and_exact_15_day_boundary_are_preserved(self):
  self.backup('recent',14);self.backup('boundary',15);self.backup('old',16);self.backup('other-recent',1)
  p=self.plan();self.assertNotIn('recent',p.delete);self.assertNotIn('boundary',p.delete);self.assertIn('old',p.delete)
 def test_two_valid_full_and_frontend_snapshots_survive_beyond_retention(self):
  shutil.rmtree(self.backups/'full-new');shutil.rmtree(self.backups/'full-second')
  self.backup('full-older',40,True);self.backup('full-oldest',50,True);self.backup('full-expired',60,True);self.backup('front-new',35);self.backup('front-second',36);self.backup('front-expired',37)
  p=self.plan();self.assertNotIn('full-older',p.delete);self.assertNotIn('full-oldest',p.delete);self.assertNotIn('front-new',p.delete);self.assertNotIn('front-second',p.delete);self.assertIn('full-expired',p.delete);self.assertIn('front-expired',p.delete)
 def test_current_release_rollback_is_pinned_by_verified_release_marker(self):
  name='old-deploy-before';self.backup(name,40,True);r=self.releases/'old-deploy';r.mkdir();(r/'RELEASE_SHA').write_text(self.backend)
  self.assertNotIn(name,self.plan().delete)
 def test_manual_pin_and_unknown_originals_are_preserved(self):
  self.backup('pinned',45);self.backup('front-1',1);self.backup('front-2',2);p=self.backups/'originals';p.mkdir();(p/'family.pdf').write_bytes(b'original');self.age(p,45)
  p=self.plan(pinned_backups=('pinned',));self.assertNotIn('pinned',p.delete);self.assertNotIn('originals',p.delete)
 def test_unknown_and_recent_nested_files_prevent_deletion(self):
  p=self.backup('old-with-new-file',45);(p/'frontend/new.js').write_text('new');self.assertNotIn(p.name,self.plan().delete)
  p=self.backup('unknown',45);(p/'notes.txt').write_text('original');self.age(p,45);self.assertNotIn(p.name,self.plan().delete)
 def test_current_jar_or_index_hardlink_preserves_backup(self):
  p=self.backup('hardlinked-current',45,True);os.link(p/'reservation-platform.jar',self.app/'reservation-platform.jar');self.assertNotIn(p.name,self.plan().delete)
 def test_symlink_escape_never_enters_cleanup(self):
  outside=self.root/'outside';outside.mkdir();(outside/'keep.txt').write_text('keep');p=self.backup('link-backup',45)
  try:(p/'frontend/escape').symlink_to(outside,target_is_directory=True)
  except OSError:self.skipTest('symlink privilege unavailable')
  self.assertNotIn(p.name,self.plan().delete);self.assertTrue((outside/'keep.txt').exists())
 def test_missing_or_invalid_full_backups_fail_closed(self):
  (self.backups/'full-new/database.dump').write_bytes(b'broken');self.backup('old',45);p=self.plan();self.assertTrue(p.blocked)
  with self.assertRaises(retention.SafetyError):retention.apply_plan(p)
  self.assertTrue((self.backups/'old').exists())
 def test_default_plan_is_read_only_and_apply_deletes_only_manifest_candidates(self):
  self.backup('old',45);self.backup('front-1',1);self.backup('front-2',2);p=self.plan();self.assertTrue((self.backups/'old').exists());result=retention.apply_plan(p);self.assertIn('old',result['deleted']);self.assertFalse((self.backups/'old').exists());self.assertTrue((self.backups/'full-new').exists())
 def test_modified_candidate_aborts_before_any_deletion(self):
  self.backup('old-1',45);self.backup('old-2',46);self.backup('front-1',1);self.backup('front-2',2);p=self.plan();(self.backups/'old-2/frontend/index.html').write_text('<html>changed</html>')
  with self.assertRaises(retention.SafetyError):retention.apply_plan(p)
  self.assertTrue((self.backups/'old-1').exists())
 def test_release_change_aborts(self):
  self.backup('old',45);p=self.plan();(self.app/'RELEASE_SHA').write_text('d'*40)
  with self.assertRaises(retention.SafetyError):retention.apply_plan(p)
 def test_archive_traversal_is_never_a_cleanup_candidate(self):
  p=self.backup('bad-archive',45,frontend=False)
  with tarfile.open(p/'frontend.tgz','w:gz') as archive:
   info=tarfile.TarInfo('../../outside/index.html');data=b'<html>unsafe</html>';info.size=len(data);archive.addfile(info,io.BytesIO(data))
  self.age(p,45);self.assertNotIn(p.name,self.plan().delete)
 def test_frontend_marker_is_protected_regardless_of_age(self):
  p=self.backup('current-front-copy',45);(p/'FRONTEND_RELEASE_SHA').write_text(self.frontend);self.age(p,45);self.backup('front-1',1);self.backup('front-2',2)
  self.assertTrue(any(r.startswith('current_artifact_representative') for r in self.plan().keep[p.name]))
 def test_current_version_does_not_pin_every_historical_copy_forever(self):
  for name,days in [('current-copy-new',40),('current-copy-old',41)]:
   p=self.backup(name,days,True);(p/'RELEASE_SHA').write_text(self.backend);self.age(p,days)
  p=self.plan();self.assertNotIn('current-copy-new',p.delete);self.assertIn('current-copy-old',p.delete)
 def test_known_release_fingerprint_files_do_not_block_expiry(self):
  p=self.backup('old-with-evidence',40,True);(p/'business-after.jsonl').write_text('{"fingerprint":"x"}');self.age(p,40)
  self.assertIn(p.name,self.plan().delete)
 def test_policy_rejects_path_pins_and_unsafe_retention(self):
  for pins in [('../app',),('/etc',)]:
   with self.assertRaises(retention.SafetyError):self.plan(pinned_backups=pins)
  with self.assertRaises(retention.SafetyError):self.plan(retention_days=0)

if __name__=='__main__':unittest.main()
