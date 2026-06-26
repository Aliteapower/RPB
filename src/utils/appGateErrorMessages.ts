interface ApiErrorBody {
  code?: string
  messageKey?: string
}

const PRODUCT_LINE_NAME = '预约排队叫号系统'

const APP_GATE_ERROR_MESSAGES: Record<string, { title: string; message: string }> = {
  TENANT_APP_NOT_ENABLED: {
    title: '预约排队叫号系统未开通',
    message: '请联系平台管理员在租户计费中勾选产品线后再使用。'
  },
  TENANT_APP_EXPIRED: {
    title: '产品线订阅已到期',
    message: `请联系平台管理员续费${PRODUCT_LINE_NAME}后再使用。`
  },
  STORE_APP_NOT_ENABLED: {
    title: '当前门店未启用',
    message: `请联系管理员检查门店的${PRODUCT_LINE_NAME}配置。`
  },
  PERMISSION_DENIED: {
    title: '当前账号没有此功能权限',
    message: '请联系管理员调整员工权限后再使用。'
  },
  APP_DISABLED: {
    title: '产品线暂不可用',
    message: '请联系平台管理员检查产品线状态。'
  }
}

export function formatAppGateErrorTitle(error: ApiErrorBody | null | undefined, fallbackTitle = '加载失败'): string {
  if (!error) {
    return fallbackTitle
  }
  return APP_GATE_ERROR_MESSAGES[error.code ?? '']?.title ?? fallbackTitle
}

export function formatAppGateErrorMessage(error: ApiErrorBody | null | undefined, fallbackMessage = '请稍后重试。'): string {
  if (!error) {
    return fallbackMessage
  }

  const mapped = APP_GATE_ERROR_MESSAGES[error.code ?? '']
  if (mapped) {
    return mapped.message
  }

  if (error.messageKey === 'appgate.tenant_app_not_enabled') {
    return APP_GATE_ERROR_MESSAGES.TENANT_APP_NOT_ENABLED.message
  }
  if (error.messageKey === 'appgate.tenant_app_expired') {
    return APP_GATE_ERROR_MESSAGES.TENANT_APP_EXPIRED.message
  }
  if (error.messageKey === 'appgate.permission_denied') {
    return APP_GATE_ERROR_MESSAGES.PERMISSION_DENIED.message
  }

  return fallbackMessage
}
