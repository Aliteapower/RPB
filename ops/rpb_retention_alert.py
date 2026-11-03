#!/usr/bin/env python3
"""Local systemd failure signal. No external messages or log deletion."""
import json,subprocess,time,syslog
from pathlib import Path
from rpb_retention_job import atomic_json

def main():
 state=subprocess.check_output(['systemctl','show','rpb-backup-retention.service','-p','Result','-p','ExecMainStatus'],text=True).strip()
 data={'level':'warning' if 'ExecMainStatus=2' in state else 'critical','unit':'rpb-backup-retention.service','unit_state':state,'checked_at':time.time()}
 atomic_json(Path('/var/lib/rpb-backup-retention/alert.json'),data)
 syslog.syslog(syslog.LOG_ERR,'RPB artifact retention requires attention: '+json.dumps(data));print(json.dumps(data))

if __name__=='__main__':main()
