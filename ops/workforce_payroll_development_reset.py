"""Explicit, all-tenant legacy payroll development reset; preview unless --apply.

Requires psycopg 3. Does not migrate schemas or disable immutable-history guards.
"""
import argparse
import json
import re

import psycopg
from psycopg import sql


# Reviewed against V091, V103, V105, V107-V112. Never discover reset scope by prefix.
TABLES = (
    'workforce_pay_templates', 'workforce_pay_profiles', 'workforce_pay_batches',
    'workforce_pay_inputs', 'workforce_pay_commands', 'workforce_pay_payments',
    'workforce_cdac_bulk_commands', 'workforce_pay_computations', 'workforce_pay_bulk_operations',
    'workforce_position_salary_defaults', 'workforce_pay_template_masters',
    'workforce_pay_template_releases', 'workforce_pay_template_release_companies',
    'workforce_pay_template_release_stores', 'workforce_pay_template_adoptions',
    'workforce_pay_template_commands', 'workforce_pay_change_requests',
    'workforce_pay_change_revisions', 'workforce_pay_change_events',
    'workforce_pay_change_commands', 'workforce_pay_change_proofs',
    'workforce_pay_approved_months', 'workforce_pay_effective_evidence',
    'workforce_pay_legacy_months', 'workforce_cdac_bulk_attempts', 'workforce_cdac_bulk_items',
    'workforce_statutory_year_subjects', 'workforce_statutory_year_revisions',
    'workforce_statutory_year_commands', 'workforce_aw_reconciliations',
    'workforce_aw_assessments', 'workforce_wage_events', 'workforce_wage_event_consumptions',
    'workforce_supplemental_batches', 'workforce_statutory_evidence',
    'workforce_statutory_liability_entries', 'workforce_statutory_settlements',
    'workforce_statutory_remittances', 'workforce_statutory_remittance_allocations',
    'workforce_statutory_company_evidence', 'workforce_supplemental_payments',
    'workforce_supplemental_commands',
)


def run(conn, schema, expected_database, apply=False):
    """Own one transaction; return pre-reset counts only after successful commit.

    Caller must supply an idle autocommit connection. Any validation/lock/SQL failure
    rolls back the whole transaction, including TRUNCATE.
    """
    if not conn.autocommit or conn.info.transaction_status != psycopg.pq.TransactionStatus.IDLE:
        raise ValueError('requires idle autocommit connection')
    if not re.fullmatch(r'[a-z_][a-z0-9_]{0,62}', schema) or schema.startswith('pg_') or schema == 'information_schema':
        raise ValueError('invalid application schema')
    with conn.transaction():
        conn.execute("SET LOCAL lock_timeout = '5s'")
        conn.execute("SET LOCAL statement_timeout = '60s'")
        actual = conn.execute('SELECT current_database()').fetchone()[0]
        if not expected_database or actual != expected_database:
            raise ValueError(f'database mismatch: expected {expected_database!r}, actual {actual!r}')
        if not conn.execute('SELECT 1 FROM pg_namespace WHERE nspname=%s', (schema,)).fetchone():
            raise ValueError(f'missing schema: {schema}')
        relations = sql.SQL(', ').join(sql.Identifier(schema, t) for t in TABLES)
        found = conn.execute('''SELECT c.relname,c.oid,c.relkind FROM pg_class c
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=%s AND c.relname=ANY(%s)''', (schema, list(TABLES))).fetchall()
        if {r[0] for r in found} != set(TABLES):
            raise ValueError('missing payroll tables: ' + ', '.join(sorted(set(TABLES) - {r[0] for r in found})))
        if any(r[2] != 'r' for r in found):
            raise ValueError('only ordinary nonpartitioned payroll tables are supported')
        # Lock before catalog dependency checks to prevent concurrent FK/trigger DDL.
        mode = sql.SQL('ACCESS EXCLUSIVE' if apply else 'ACCESS SHARE')
        conn.execute(sql.SQL('LOCK TABLE {} IN {} MODE').format(relations, mode))
        locked = conn.execute('''SELECT c.relname,c.oid,c.relkind FROM pg_class c
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=%s AND c.relname=ANY(%s)''', (schema, list(TABLES))).fetchall()
        if set(locked) != set(found):
            raise ValueError('schema tables changed while acquiring locks; retry after stopping DDL')
        oids = [r[1] for r in found]
        external = conn.execute('''SELECT conrelid::regclass::text,conname
            FROM pg_constraint WHERE contype='f' AND confrelid=ANY(%s::oid[])
            AND NOT (conrelid=ANY(%s::oid[]))''', (oids, oids)).fetchall()
        if external:
            raise ValueError(f'external foreign key dependencies: {external}')
        inheritance = conn.execute('''SELECT inhrelid::regclass::text,inhparent::regclass::text
            FROM pg_inherits WHERE inhrelid=ANY(%s::oid[]) OR inhparent=ANY(%s::oid[])''', (oids, oids)).fetchall()
        if inheritance:
            raise ValueError(f'unsupported inheritance dependencies: {inheritance}')
        triggers = conn.execute('''SELECT tgrelid::regclass::text,tgname FROM pg_trigger
            WHERE tgrelid=ANY(%s::oid[]) AND (tgtype::integer & 32)<>0''', (oids,)).fetchall()
        if triggers:
            raise ValueError(f'unsupported TRUNCATE trigger: {triggers}')
        views = conn.execute('''SELECT DISTINCT r.ev_class::regclass::text
            FROM pg_depend d JOIN pg_rewrite r ON d.classid='pg_rewrite'::regclass AND d.objid=r.oid
            WHERE d.refclassid='pg_class'::regclass AND d.refobjid=ANY(%s::oid[])
            AND NOT (r.ev_class=ANY(%s::oid[]))''', (oids, oids)).fetchall()
        if views:
            raise ValueError(f'unsupported external view dependencies: {views}')
        counts = {t: conn.execute(sql.SQL('SELECT count(*) FROM {}').format(sql.Identifier(schema, t))).fetchone()[0] for t in TABLES}
        if apply:
            # No CASCADE, ONLY, RESTART IDENTITY, trigger disabling, or DELETE fallback.
            conn.execute(sql.SQL('TRUNCATE TABLE {} CONTINUE IDENTITY RESTRICT').format(relations))
        return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', required=True)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--database', required=True, help='Connection database and expected current_database()')
    parser.add_argument('--user', required=True)
    parser.add_argument('--schema', required=True, help='Explicit application schema; no search_path inference')
    parser.add_argument('--apply', action='store_true', help='Execute reset in one transaction; otherwise preview')
    args = parser.parse_args()
    try:
        # Password is libpq PGPASSWORD/PGPASSFILE, never a command-line argument.
        with psycopg.connect(host=args.host, port=args.port, dbname=args.database,
                             user=args.user, autocommit=True, connect_timeout=5) as conn:
            counts = run(conn, args.schema, args.database, args.apply)
        print(json.dumps({'mode': 'applied' if args.apply else 'preview', 'database': args.database,
                          'schema': args.schema, 'rows_before': counts}, indent=2))
    except (ValueError, psycopg.Error) as error:
        parser.exit(1, f'Reset refused/rolled back: {error}\n')


if __name__ == '__main__':
    main()
