#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${RPB_BASE_URL:-}"
BASIC_USER="${RPB_BASIC_USER:-}"
BASIC_PASSWORD="${RPB_BASIC_PASSWORD:-}"
TENANT_LOGO_URL="${RPB_TENANT_LOGO_URL:-}"

if [[ -z "$BASE_URL" ]]; then
  echo "Set RPB_BASE_URL, for example: https://acceptance.example.com" >&2
  exit 2
fi

AUTH_ARGS=()
if [[ -n "$BASIC_USER" || -n "$BASIC_PASSWORD" ]]; then
  if [[ -z "$BASIC_USER" || -z "$BASIC_PASSWORD" ]]; then
    echo "Set both RPB_BASIC_USER and RPB_BASIC_PASSWORD, or neither." >&2
    exit 2
  fi
  AUTH_ARGS=(--user "$BASIC_USER:$BASIC_PASSWORD")
fi

curl_code() {
  local method="$1"
  local url="$2"
  shift 2
  curl --silent --show-error --location --max-time 20 \
    --request "$method" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$@" \
    "$url"
}

expect_code() {
  local label="$1"
  local expected_regex="$2"
  local method="$3"
  local url="$4"
  shift 4
  local code
  code="$(curl_code "$method" "$url" "$@")"
  if [[ ! "$code" =~ $expected_regex ]]; then
    echo "FAIL $label: expected $expected_regex, got $code ($url)" >&2
    exit 1
  fi
  echo "PASS $label: $code"
}

echo "Checking front door protection without credentials..."
unauth_code="$(curl_code GET "$BASE_URL/")"
if [[ "$unauth_code" =~ ^(401|403)$ ]]; then
  echo "PASS front door protection: $unauth_code"
else
  echo "FAIL front door protection: expected 401 or 403 without credentials, got $unauth_code" >&2
  exit 1
fi

if [[ "${#AUTH_ARGS[@]}" -eq 0 ]]; then
  echo "No Basic Auth credentials provided. Front door protection check passed; authenticated checks skipped."
  exit 0
fi

expect_code "frontend shell" "^(200)$" GET "$BASE_URL/login" "${AUTH_ARGS[@]}"
expect_code "auth me unauthenticated application state" "^(401)$" GET "$BASE_URL/api/v1/auth/me" "${AUTH_ARGS[@]}"
expect_code "captcha endpoint" "^(200)$" POST "$BASE_URL/api/v1/auth/captcha/slider" "${AUTH_ARGS[@]}"

if [[ -n "$TENANT_LOGO_URL" ]]; then
  expect_code "tenant logo media" "^(200)$" GET "$BASE_URL$TENANT_LOGO_URL" "${AUTH_ARGS[@]}"
else
  echo "SKIP tenant logo media: set RPB_TENANT_LOGO_URL after uploading an acceptance-only tenant logo."
fi

echo "Smoke check completed."

