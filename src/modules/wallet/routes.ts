import type { RouteRecordRaw } from 'vue-router'
export const walletRoutes: RouteRecordRaw[] = [
  { path: '/stores/:storeId/wallet', component: () => import('./WalletPage.vue'), meta: { walletWorkspace: 'staff' }, children: [
    { path: '', name: 'wallet-workbench', component: () => import('./pages/WalletStaffHome.vue') },
    { path: 'collect', name: 'wallet-collect', component: () => import('./pages/WalletStaffCollect.vue') },
    { path: 'merchant-code', name: 'wallet-merchant-code', component: () => import('./pages/WalletMerchantCode.vue') },
    { path: 'topup', name: 'wallet-topup', component: () => import('./pages/WalletStaffRecharge.vue') },
    { path: 'customers', name: 'wallet-customers', component: () => import('./pages/WalletStaffCustomers.vue'), meta:{requiresTenantAdmin:true} },
    { path: 'points/:entryId/reverse', name: 'wallet-points-reverse', component: () => import('./pages/WalletPointsReversal.vue') },
    { path: 'transactions', name: 'wallet-staff-transactions', component: () => import('./pages/WalletTransactions.vue') },
    { path: 'transactions/:sourceType/:transactionId', name: 'wallet-staff-transaction', component: () => import('./pages/WalletTransactionDetail.vue') },
    { path: 'bills/:billId', name: 'wallet-staff-bill', component: () => import('./pages/WalletStaffBill.vue') },
    { path: 'topups/:topupId', name: 'wallet-staff-topup', component: () => import('./pages/WalletStaffTopupDetail.vue') },
    { path: 'marketing', name: 'wallet-marketing', component: () => import('./pages/WalletMarketing.vue') },
    { path: 'more', name: 'wallet-more', component: () => import('./pages/WalletStaffMore.vue') },
    { path: 'business-report', name: 'wallet-business-report', component: () => import('./pages/WalletBusinessReport.vue') },
    ...(['overview','channels','paynow'] as const).map(section => ({
      path: section==='overview'?'settings':`settings/${section}`, name: section==='overview'?'wallet-settings':`wallet-settings-${section}`,
      component: () => import('./pages/WalletStaffSettings.vue'), props: {section}
    })),
    ...['refund-create','refunds/:refundId','report','exceptions'].map((path, index) => ({
      path, name: ['wallet-refund-create','wallet-refund-detail','wallet-report','wallet-exceptions'][index],
      component: () => import('./pages/WalletStaffManagement.vue')
    }))
  ] },
  { path: '/wallet/:storeId', component: () => import('./WalletCustomerPage.vue'), meta: { public: true, walletWorkspace: 'customer' }, children: [
    { path: '', name: 'wallet-customer', component: () => import('./pages/WalletCustomerHome.vue') },
    { path: 'register', name: 'wallet-customer-register', component: () => import('./pages/WalletCustomerRegister.vue') },
    { path: 'code', name: 'wallet-customer-code', component: () => import('./pages/WalletCustomerCode.vue') },
    { path: 'merchant-orders/:orderId', name:'wallet-customer-order', component:()=>import('./pages/WalletCustomerOrder.vue') },
    { path: 'scan', name: 'wallet-customer-scan', component: () => import('./pages/WalletCustomerScan.vue') },
    { path: 'points-redemptions/:redemptionId', name: 'wallet-customer-redemption', component: () => import('./pages/WalletCustomerRedemption.vue') },
    { path: 'transactions', name: 'wallet-customer-transactions', component: () => import('./pages/WalletTransactions.vue') },
    { path: 'transactions/:sourceType/:transactionId', name: 'wallet-customer-transaction', component: () => import('./pages/WalletTransactionDetail.vue') },
    { path: 'bills/:billId', name: 'wallet-customer-bill', component: () => import('./pages/WalletCustomerBillDetail.vue') },
    { path: 'topup', name: 'wallet-customer-topup', component: () => import('./pages/WalletCustomerTopup.vue') }
  ] }
]
