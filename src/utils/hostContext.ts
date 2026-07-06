export type LoginHostContext =
  | { kind: 'legacy'; tenantCode: ''; storageScope: string }
  | { kind: 'platform'; tenantCode: ''; storageScope: string }
  | { kind: 'tenant'; tenantCode: string; storageScope: string }

declare global {
  interface Window {
    __RPB_HOST_PREFIX_BASE_HOST__?: string
  }
}

const NUMERIC_TENANT_PREFIX_PATTERN = /^\d{4,20}$/
const TENANT_PREFIX_PATTERN = /^(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/

export function resolveLoginHostContext(
  hostname = window.location.hostname,
  baseHost = configuredBaseHost()
): LoginHostContext {
  const host = normalizedHostname(hostname)
  if (!host || isLocalHostname(host) || isIpHostname(host)) {
    return { kind: 'legacy', tenantCode: '', storageScope: 'legacy' }
  }
  if (baseHost) {
    return resolveConfiguredBaseHost(host, baseHost)
  }
  const labels = host.split('.')
  if (labels.length < 3) {
    return { kind: 'legacy', tenantCode: '', storageScope: host }
  }
  const prefix = labels[0]
  if (prefix === 'platform') {
    return { kind: 'platform', tenantCode: '', storageScope: `platform:${baseHostFromLabels(labels)}` }
  }
  if (isTenantPrefix(prefix, labels)) {
    return { kind: 'tenant', tenantCode: prefix, storageScope: `tenant:${prefix}:${baseHostFromLabels(labels)}` }
  }
  return { kind: 'legacy', tenantCode: '', storageScope: host }
}

export function publicBookingUrlForTenant(
  tenantCode: string,
  storeId: string,
  locationLike: Pick<Location, 'protocol' | 'hostname' | 'port' | 'origin'> = window.location
): string {
  const normalizedTenantCode = tenantCode.trim()
  const hostContext = resolveLoginHostContext(locationLike.hostname)
  if (!normalizedTenantCode) {
    return `${locationLike.origin}/book/${encodeURIComponent(storeId)}`
  }
  if (hostContext.kind === 'tenant' && hostContext.tenantCode === normalizedTenantCode) {
    return `${locationLike.origin}/book`
  }
  if (isLocalHostname(locationLike.hostname) || isIpHostname(locationLike.hostname)) {
    return `${locationLike.origin}/book/${encodeURIComponent(storeId)}`
  }
  const labels = normalizedHostname(locationLike.hostname).split('.')
  const rootHost = hostContext.kind === 'legacy' ? labels.join('.') : labels.slice(1).join('.')
  const port = locationLike.port ? `:${locationLike.port}` : ''
  return `${locationLike.protocol}//${normalizedTenantCode}.${rootHost}${port}/book`
}

function normalizedHostname(hostname: string): string {
  return hostname.trim().toLowerCase().replace(/\.$/, '')
}

function normalizedOptionalHostname(hostname: string | undefined): string {
  return hostname ? normalizedHostname(hostname) : ''
}

function configuredBaseHost(): string {
  return normalizedOptionalHostname(window.__RPB_HOST_PREFIX_BASE_HOST__) ||
    normalizedOptionalHostname(import.meta.env.VITE_RPB_HOST_PREFIX_BASE_HOST)
}

function resolveConfiguredBaseHost(host: string, baseHost: string): LoginHostContext {
  if (host === baseHost) {
    return { kind: 'legacy', tenantCode: '', storageScope: baseHost }
  }
  const suffix = `.${baseHost}`
  if (!host.endsWith(suffix)) {
    return { kind: 'legacy', tenantCode: '', storageScope: host }
  }
  const prefix = host.slice(0, -suffix.length)
  if (prefix.includes('.')) {
    return { kind: 'legacy', tenantCode: '', storageScope: host }
  }
  if (prefix === 'platform') {
    return { kind: 'platform', tenantCode: '', storageScope: `platform:${baseHost}` }
  }
  return TENANT_PREFIX_PATTERN.test(prefix)
    ? { kind: 'tenant', tenantCode: prefix, storageScope: `tenant:${prefix}:${baseHost}` }
    : { kind: 'legacy', tenantCode: '', storageScope: host }
}

function baseHostFromLabels(labels: string[]): string {
  return labels.slice(1).join('.')
}

function isTenantPrefix(prefix: string, labels: string[]): boolean {
  if (!TENANT_PREFIX_PATTERN.test(prefix)) {
    return false
  }
  return NUMERIC_TENANT_PREFIX_PATTERN.test(prefix) || labels.length > 3
}

function isLocalHostname(hostname: string): boolean {
  const host = normalizedHostname(hostname)
  return host === 'localhost' || host.endsWith('.localhost')
}

function isIpHostname(hostname: string): boolean {
  const host = normalizedHostname(hostname)
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(':')
}
