#!/usr/bin/env bash
set -euo pipefail

env_file="${RPB_BACKEND_ENV_FILE:-/etc/rpb/rpb-backend.env}"
if [ ! -r "$env_file" ]; then
  echo "env_file_not_readable=$env_file" >&2
  exit 1
fi

set -a
. "$env_file"
set +a

base_host="${RPB_PUBLIC_HOST_BASE_HOST:-${RPB_HOST_PREFIX_BASE_HOST:-}}"
cert_name="${RPB_PUBLIC_HOST_CERT_NAME:-booking.yumstone.sg}"
cert_live_dir="${RPB_PUBLIC_HOST_CERT_LIVE_DIR:-/etc/letsencrypt/live/${cert_name}}"

if [ -z "$base_host" ]; then
  echo "missing_base_host=RPB_HOST_PREFIX_BASE_HOST" >&2
  exit 1
fi

base_host="$(printf '%s' "$base_host" | tr '[:upper:]' '[:lower:]')"
if ! printf '%s' "$base_host" | grep -Eq '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$'; then
  echo "invalid_base_host=$base_host" >&2
  exit 1
fi

if [ -z "${DB_HOST:-}" ] || [ -z "${DB_USERNAME:-}" ] || [ -z "${DB_NAME:-}" ]; then
  echo "missing_database_environment" >&2
  exit 1
fi

export PGPASSWORD="${DB_PASSWORD:-}"
psql_cmd=(
  psql
  -h "$DB_HOST"
  -p "${DB_PORT:-5432}"
  -U "$DB_USERNAME"
  -d "$DB_NAME"
  -v ON_ERROR_STOP=1
  -At
)

mark_failed() {
  local error_message="$1"
  "${psql_cmd[@]}" -v last_error="$error_message" <<'SQL' >/dev/null
    update public_host_bindings
    set tls_status = 'failed',
        last_checked_at = now(),
        last_error = left(:'last_error', 1000),
        updated_at = now(),
        version = version + 1
    where tls_status in ('pending', 'failed')
      and deleted_at is null;
SQL
}

"${psql_cmd[@]}" -v base_host="$base_host" <<'SQL' >/dev/null
  insert into public_host_bindings (
      host_alias_id, tenant_id, host_prefix, host_type, hostname, tls_status
  )
  select
      alias.id,
      alias.tenant_id,
      lower(alias.alias_code),
      alias.alias_type,
      lower(alias.alias_code || '.' || :'base_host'),
      'pending'
  from tenant_host_aliases alias
  where alias.status = 'active'
    and alias.deleted_at is null
    and alias.alias_type in ('tenant', 'store')
    and not exists (
        select 1
        from public_host_bindings binding
        where binding.host_alias_id = alias.id
          and binding.deleted_at is null
    )
  on conflict (host_alias_id) where deleted_at is null do nothing;
SQL

mapfile -t pending_hosts < <("${psql_cmd[@]}" <<'SQL'
  select hostname
  from public_host_bindings
  where tls_status in ('pending', 'failed')
    and deleted_at is null
  order by hostname;
SQL
)

if [ "${#pending_hosts[@]}" -eq 0 ]; then
  echo "public_host_bindings_pending=0"
  exit 0
fi

domains=()
domains+=("$base_host")
domains+=("platform.$base_host")

if [ -f "$cert_live_dir/fullchain.pem" ]; then
  while IFS= read -r domain; do
    [ -n "$domain" ] && domains+=("$domain")
  done < <(
    openssl x509 -in "$cert_live_dir/fullchain.pem" -noout -ext subjectAltName |
      tr ',' '\n' |
      sed -n 's/.*DNS:\([^[:space:]]*\).*/\1/p'
  )
fi

domains+=("${pending_hosts[@]}")

declare -A seen_domains=()
certbot_domains=()
for domain in "${domains[@]}"; do
  domain="$(printf '%s' "$domain" | tr '[:upper:]' '[:lower:]')"
  if ! printf '%s' "$domain" | grep -Eq '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$'; then
    continue
  fi
  if [ -z "${seen_domains[$domain]+x}" ]; then
    seen_domains[$domain]=1
    certbot_domains+=("-d" "$domain")
  fi
done

if [ "${#certbot_domains[@]}" -eq 0 ]; then
  mark_failed "no_valid_domains_for_certbot"
  echo "public_host_bindings_failed=no_valid_domains_for_certbot" >&2
  exit 1
fi

error_file="$(mktemp)"
if ! certbot --nginx --cert-name "$cert_name" --expand --non-interactive "${certbot_domains[@]}" 2>"$error_file"; then
  error_message="$(tr '\n' ' ' < "$error_file" | cut -c1-1000)"
  rm -f "$error_file"
  mark_failed "$error_message"
  echo "public_host_bindings_failed=certbot" >&2
  exit 1
fi
rm -f "$error_file"

if ! nginx -t; then
  mark_failed "nginx_config_test_failed"
  echo "public_host_bindings_failed=nginx_test" >&2
  exit 1
fi

if ! systemctl reload nginx; then
  mark_failed "nginx_reload_failed"
  echo "public_host_bindings_failed=nginx_reload" >&2
  exit 1
fi

"${psql_cmd[@]}" -v cert_name="$cert_name" <<'SQL' >/dev/null
  update public_host_bindings
  set tls_status = 'covered',
      certificate_name = :'cert_name',
      covered_at = coalesce(covered_at, now()),
      last_checked_at = now(),
      last_error = null,
      updated_at = now(),
      version = version + 1
  where tls_status in ('pending', 'failed')
    and deleted_at is null;
SQL

echo "public_host_bindings_covered=${#pending_hosts[@]}"
