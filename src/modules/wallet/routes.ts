import type { RouteRecordRaw } from 'vue-router'
export const walletRoutes: RouteRecordRaw[] = [
  { path: '/stores/:storeId/wallet', component: () => import('./WalletPage.vue'), meta: { walletWorkspace: 'staff' }, children: [
    { path: '', name: 'wallet-workbench', component: () => import('./pages/WalletStaffHome.vue') },
    { path: 'collect', name: 'wallet-collect', component: () => import('./pages/WalletStaffCollect.vue') },
    { path: 'topup', name: 'wallet-topup', component: () => import('./pages/WalletStaffTopup.vue') },
    { path: 'bills', name: 'wallet-staff-bills', component: () => import('./pages/WalletStaffRecords.vue') },
    { path: 'bills/:billId', name: 'wallet-staff-bill', component: () => import('./pages/WalletStaffBill.vue') },
    { path: 'topups/:topupId', name: 'wallet-staff-topup', component: () => import('./pages/WalletStaffTopupDetail.vue') },
    { path: 'more', name: 'wallet-more', component: () => import('./pages/WalletStaffMore.vue') },
    ...['history','refunds','refund-create','refunds/:refundId','settings','report','exceptions'].map((path, index) => ({
      path, name: ['wallet-staff-history','wallet-refunds','wallet-refund-create','wallet-refund-detail','wallet-settings','wallet-report','wallet-exceptions'][index],
      component: () => import('./pages/WalletStaffManagement.vue')
    }))
  ] },
  { path: '/wallet/:storeId', component: () => import('./WalletCustomerPage.vue'), meta: { public: true, walletWorkspace: 'customer' }, children: [
    { path: '', name: 'wallet-customer', component: () => import('./pages/WalletCustomerHome.vue') },
    { path: 'code', name: 'wallet-customer-code', component: () => import('./pages/WalletCustomerCode.vue') },
    { path: 'scan', name: 'wallet-customer-scan', component: () => import('./pages/WalletCustomerScan.vue') },
    { path: 'bills', name: 'wallet-customer-bills', component: () => import('./pages/WalletCustomerBills.vue') },
    { path: 'bills/:billId', name: 'wallet-customer-bill', component: () => import('./pages/WalletCustomerBillDetail.vue') },
    { path: 'history', name: 'wallet-customer-history', component: () => import('./pages/WalletCustomerHistory.vue') }
  ] }
]
