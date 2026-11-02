# Storage records as query reports

Replace merchant/employee records workflow with a read-only report independent of customer picker. Query all accessible store records with date/keyword/status/type, full-filter summary,20-row server pages. Desktop table and compact mobile rows, one bill per result; details retain original evidence/photos without mutation controls. Existing customer-facing records are unchanged. Preserve authorized history access and tenant/store isolation. API contract docs/api/WINE_STORAGE_REPORTS.md.

Implementation: isolated report DTO/repository/query/controller; indexed scope/date existing tables, no migration; metadata-only Home for report shell; owning layout; query and request lifecycle tests; real pointer Postgres tests; browser responsive validation; scoped clean archive and authorized production release with backend backup/rollback and release note.
