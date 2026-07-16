import { translate } from '../i18n'

interface ApiErrorBody {
  code?: string
  messageKey?: string
}

const APP_GATE_ERROR_MESSAGES: Record<string, { titleKey: string; messageKey: string }> = {
  TENANT_APP_NOT_ENABLED: {
    titleKey: 'appGate.errors.tenantAppNotEnabled.title',
    messageKey: 'appGate.errors.tenantAppNotEnabled.message'
  },
  TENANT_APP_EXPIRED: {
    titleKey: 'appGate.errors.tenantAppExpired.title',
    messageKey: 'appGate.errors.tenantAppExpired.message'
  },
  STORE_APP_NOT_ENABLED: {
    titleKey: 'appGate.errors.storeAppNotEnabled.title',
    messageKey: 'appGate.errors.storeAppNotEnabled.message'
  },
  PERMISSION_DENIED: {
    titleKey: 'appGate.errors.permissionDenied.title',
    messageKey: 'appGate.errors.permissionDenied.message'
  },
  APP_DISABLED: {
    titleKey: 'appGate.errors.appDisabled.title',
    messageKey: 'appGate.errors.appDisabled.message'
  }
}

export function formatAppGateErrorTitle(
  error: ApiErrorBody | null | undefined,
  fallbackTitle = translate('appGate.errors.loadFailed.title')
): string {
  if (!error) {
    return fallbackTitle
  }

  const mapped = APP_GATE_ERROR_MESSAGES[error.code ?? '']
  return mapped ? translate(mapped.titleKey) : fallbackTitle
}

export function formatAppGateErrorMessage(
  error: ApiErrorBody | null | undefined,
  fallbackMessage = translate('appGate.errors.loadFailed.message')
): string {
  if (!error) {
    return fallbackMessage
  }

  const mapped = APP_GATE_ERROR_MESSAGES[error.code ?? '']
  if (mapped) {
    return translate(mapped.messageKey)
  }

  if (error.messageKey === 'appgate.tenant_app_not_enabled') {
    return translate(APP_GATE_ERROR_MESSAGES.TENANT_APP_NOT_ENABLED.messageKey)
  }
  if (error.messageKey === 'appgate.tenant_app_expired') {
    return translate(APP_GATE_ERROR_MESSAGES.TENANT_APP_EXPIRED.messageKey)
  }
  if (error.messageKey === 'appgate.permission_denied') {
    return translate(APP_GATE_ERROR_MESSAGES.PERMISSION_DENIED.messageKey)
  }

  return fallbackMessage
}
