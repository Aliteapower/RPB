export const tenantProductsZh = {
  title: '产品线管理', hint: '设置当前门店已开通产品的使用状态，不影响其他门店。',
  retention: '关闭后隐藏入口并停止访问，钱包余额、积分及营业记录保留，重新开启后可继续使用。酒水寄存关闭后停止新申请和收存，历史及有效物品受控领取保留。关闭不会取消订阅或停止计费。',
  enabled: '已开启', disabled: '已关闭', enable: '开启', disable: '关闭', saving: '保存中…', saved: '已保存',
  loadFailed: '加载失败，请刷新重试。', saveFailed: '保存失败，当前状态未更改，请刷新后重试。', accessDenied: '当前账号无权管理此门店产品线。',
  unavailable: '当前不可开启，请联系平台管理员检查开通状态。',
  names: { wallet: '钱包积分', daily_ledger: '每日营业记录', wine_storage:'酒水寄存' },
  reasons: {
    APP_DISABLED: '平台已停用此产品，暂不可开启。', TENANT_APP_NOT_ENABLED: '租户尚未开通或已暂停此产品。',
    TENANT_APP_EXPIRED: '租户授权已到期，请联系平台管理员。', STORE_APP_NOT_ENABLED: '当前门店尚未开通或订阅已暂停。',
    STORE_APP_EXPIRED: '当前门店订阅已到期，请联系平台管理员续费。', STORE_ACCESS_DENIED: '无此门店访问权限。', PERMISSION_DENIED: '无操作权限。'
  }
}
export const tenantProductsEn = {
  title: 'Product lines', hint: 'Manage purchased products for the current store. Other stores are unaffected.',
  retention: 'Turning a product off hides its entry and blocks access. Wallet balances, points and ledger records are retained for use when turned on again. Bottle storage blocks new intake while keeping history and controlled collection. This does not cancel the subscription or stop billing.',
  enabled: 'On', disabled: 'Off', enable: 'Turn on', disable: 'Turn off', saving: 'Saving…', saved: 'Saved',
  loadFailed: 'Could not load products. Refresh to try again.', saveFailed: 'Could not save. The current state has not changed. Refresh and try again.', accessDenied: 'Your account cannot manage products for this store.',
  unavailable: 'This product cannot be turned on. Contact the platform administrator to check access.',
  names: { wallet: 'Wallet and points', daily_ledger: 'Daily ledger', wine_storage:'Bottle storage' },
  reasons: {
    APP_DISABLED: 'The platform has disabled this product.', TENANT_APP_NOT_ENABLED: 'This product is not enabled for the tenant or has been suspended.',
    TENANT_APP_EXPIRED: 'Tenant access has expired. Contact the platform administrator.', STORE_APP_NOT_ENABLED: 'This store has not purchased the product or its subscription is suspended.',
    STORE_APP_EXPIRED: 'The store subscription has expired. Contact the platform administrator to renew.', STORE_ACCESS_DENIED: 'You do not have access to this store.', PERMISSION_DENIED: 'You do not have permission for this action.'
  }
}
