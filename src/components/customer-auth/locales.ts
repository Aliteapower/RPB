export const customerAuthZh = {
  register:'注册顾客',
  login: '顾客登录', phone: '手机号', password: '密码', loginHint: '使用门店登记的手机号登录，初始密码为手机号后 6 位。',
  changePassword: '修改登录密码', currentPassword: '当前密码', newPassword: '新密码', confirmPassword: '确认新密码',
  passwordHint: '新密码至少 8 个字符。修改后请使用新密码登录。', save: '保存新密码', changed: '密码已修改，其他登录会话已退出。',
  mismatch: '两次输入的新密码不一致。', busy: '处理中…', cancel: '取消',
  sharedHint: '本租户所有产品共用同一份顾客资料，手机号是唯一识别依据。',
  errors: { invalid_credentials: '手机号或密码不正确，请核对门店登记资料。', rate_limited: '尝试次数过多，请 15 分钟后重试。',
    account_disabled: '顾客资料或账号已停用，请联系门店。', unauthenticated: '登录已失效，请重新登录。',
    request_invalid: '请检查手机号及密码；新密码至少 8 个字符，不能使用初始密码或原密码。',
    network_failure: '网络连接失败，请重试。', request_failed: '操作失败，请稍后重试。' }
}
export const customerAuthEn = {
  register:'Register customer',
  login: 'Customer sign in', phone: 'Phone number', password: 'Password', loginHint: 'Use the phone registered with the store. Your initial password is the last 6 digits of your phone.',
  changePassword: 'Change password', currentPassword: 'Current password', newPassword: 'New password', confirmPassword: 'Confirm new password',
  passwordHint: 'Use at least 8 characters. Sign in with your new password after changing it.', save: 'Save password', changed: 'Password changed. Other sessions have been signed out.',
  mismatch: 'The new passwords do not match.', busy: 'Processing…', cancel: 'Cancel',
  sharedHint: 'All products within this tenant share one customer profile, identified by phone number.',
  errors: { invalid_credentials: 'Incorrect phone or password. Check the details registered with the store.', rate_limited: 'Too many attempts. Try again in 15 minutes.',
    account_disabled: 'This customer profile or account is disabled. Contact the store.', unauthenticated: 'Your session expired. Please sign in again.',
    request_invalid: 'Check the phone and passwords. A new password needs at least 8 characters and must differ from the initial and current passwords.',
    network_failure: 'Unable to connect. Please try again.', request_failed: 'Unable to complete the request. Please try again.' }
}
