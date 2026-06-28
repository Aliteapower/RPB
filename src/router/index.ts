import { createRouter, createWebHistory } from 'vue-router'

import CleaningCompletePage from '../pages/CleaningCompletePage.vue'
import LoginPage from '../pages/LoginPage.vue'
import PlatformCallScreenSeedPage from '../pages/PlatformCallScreenSeedPage.vue'
import PlatformProductLinesPage from '../pages/PlatformProductLinesPage.vue'
import PlatformProfilePage from '../pages/PlatformProfilePage.vue'
import PlatformReservationShareTemplateSeedPage from '../pages/PlatformReservationShareTemplateSeedPage.vue'
import PlatformTenantBillingPage from '../pages/PlatformTenantBillingPage.vue'
import PlatformTenantFormPage from '../pages/PlatformTenantFormPage.vue'
import PlatformTenantsPage from '../pages/PlatformTenantsPage.vue'
import QueueCallPage from '../pages/QueueCallPage.vue'
import QueueDisplayPage from '../pages/QueueDisplayPage.vue'
import QueueTicketListPage from '../pages/QueueTicketListPage.vue'
import ReservationArrivedDirectSeatingPage from '../pages/ReservationArrivedDirectSeatingPage.vue'
import ReservationArrivedToQueuePage from '../pages/ReservationArrivedToQueuePage.vue'
import ReservationCheckInPage from '../pages/ReservationCheckInPage.vue'
import ReservationPublicSharePage from '../pages/ReservationPublicSharePage.vue'
import ReservationTodayViewPage from '../pages/ReservationTodayViewPage.vue'
import SeatingFromCalledQueuePage from '../pages/SeatingFromCalledQueuePage.vue'
import StoreStaffHomePage from '../pages/StoreStaffHomePage.vue'
import TableResourceListPage from '../pages/TableResourceListPage.vue'
import TenantAdminCallScreenPage from '../pages/TenantAdminCallScreenPage.vue'
import TenantAdminProfilePage from '../pages/TenantAdminProfilePage.vue'
import TenantAdminReservationSharePage from '../pages/TenantAdminReservationSharePage.vue'
import TenantAdminSettingsPage from '../pages/TenantAdminSettingsPage.vue'
import TenantAdminStaffFormPage from '../pages/TenantAdminStaffFormPage.vue'
import TenantAdminStaffPage from '../pages/TenantAdminStaffPage.vue'
import TenantAdminTableFormPage from '../pages/TenantAdminTableFormPage.vue'
import TenantAdminTablesPage from '../pages/TenantAdminTablesPage.vue'
import WalkInDirectSeatingPage from '../pages/WalkInDirectSeatingPage.vue'
import WalkInQueuePage from '../pages/WalkInQueuePage.vue'
import { useAuthSessionStore } from '../stores/authSession'

const localValidationStoreId = '20000000-0000-0000-0000-000000000983'
const defaultStoreId = import.meta.env.VITE_DEFAULT_STORE_ID || localValidationStoreId

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginPage,
      meta: { public: true }
    },
    {
      path: '/reservation-share/:token',
      name: 'reservation-public-share',
      component: ReservationPublicSharePage,
      meta: { public: true }
    },
    {
      path: '/',
      redirect: () => {
        const auth = useAuthSessionStore()
        return auth.loaded && auth.isPlatformAdmin ? auth.platformHomeRoute : `/stores/${defaultStoreId}/staff`
      }
    },
    {
      path: '/platform/tenants',
      name: 'platform-tenants',
      component: PlatformTenantsPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/billing/subscriptions',
      name: 'platform-billing-subscriptions',
      component: PlatformTenantsPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/tenants/new',
      name: 'platform-tenant-create',
      component: PlatformTenantFormPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/tenants/:tenantId/edit',
      name: 'platform-tenant-edit',
      component: PlatformTenantFormPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/tenants/:tenantId/billing',
      name: 'platform-tenant-billing',
      component: PlatformTenantBillingPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/settings/profile',
      name: 'platform-profile',
      component: PlatformProfilePage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/settings/product-lines',
      name: 'platform-product-lines',
      component: PlatformProductLinesPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/call-screen/text-seed',
      name: 'platform-call-screen-text-seed',
      component: PlatformCallScreenSeedPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/platform/reservation/share-template-seed',
      name: 'platform-reservation-share-template-seed',
      component: PlatformReservationShareTemplateSeedPage,
      meta: { requiresPlatformAdmin: true }
    },
    {
      path: '/stores/:storeId/staff',
      name: 'store-staff-home',
      component: StoreStaffHomePage
    },
    {
      path: '/stores/:storeId/admin',
      redirect: to => ({
        name: 'tenant-admin-profile',
        params: { storeId: to.params.storeId }
      })
    },
    {
      path: '/stores/:storeId/admin/profile',
      name: 'tenant-admin-profile',
      component: TenantAdminProfilePage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/staff',
      name: 'tenant-admin-staff',
      component: TenantAdminStaffPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/staff/new',
      name: 'tenant-admin-staff-create',
      component: TenantAdminStaffFormPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/staff/:staffId/edit',
      name: 'tenant-admin-staff-edit',
      component: TenantAdminStaffFormPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/tables',
      name: 'tenant-admin-tables',
      component: TenantAdminTablesPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/tables/new',
      name: 'tenant-admin-table-create',
      component: TenantAdminTableFormPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/tables/:tableId/edit',
      name: 'tenant-admin-table-edit',
      component: TenantAdminTableFormPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/settings',
      name: 'tenant-admin-settings',
      component: TenantAdminSettingsPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/share-template',
      name: 'tenant-admin-reservation-share',
      component: TenantAdminReservationSharePage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/call-screen',
      name: 'tenant-admin-call-screen',
      component: TenantAdminCallScreenPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/walk-ins/direct-seating',
      name: 'walk-in-direct-seating',
      component: WalkInDirectSeatingPage
    },
    {
      path: '/stores/:storeId/walk-ins/queue',
      name: 'walk-in-queue',
      component: WalkInQueuePage
    },
    {
      path: '/stores/:storeId/cleaning',
      name: 'cleaning-complete',
      component: CleaningCompletePage
    },
    {
      path: '/stores/:storeId/reservations/create',
      name: 'reservation-create',
      redirect: to => ({
        name: 'reservation-today-view',
        params: {
          storeId: to.params.storeId
        },
        query: { create: '1' }
      })
    },
    {
      path: '/stores/:storeId/reservations/today',
      name: 'reservation-today-view',
      component: ReservationTodayViewPage
    },
    {
      path: '/stores/:storeId/reservations/check-in',
      name: 'reservation-check-in',
      component: ReservationCheckInPage
    },
    {
      path: '/stores/:storeId/reservations/queue',
      name: 'reservation-arrived-to-queue',
      component: ReservationArrivedToQueuePage
    },
    {
      path: '/stores/:storeId/queue-tickets',
      name: 'queue-ticket-list',
      component: QueueTicketListPage
    },
    {
      path: '/stores/:storeId/queue-tickets/call',
      name: 'queue-call',
      component: QueueCallPage
    },
    {
      path: '/stores/:storeId/queue-display',
      name: 'queue-display',
      component: QueueDisplayPage
    },
    {
      path: '/stores/:storeId/queue-tickets/seating/direct',
      name: 'seating-from-called-queue',
      component: SeatingFromCalledQueuePage
    },
    {
      path: '/stores/:storeId/tables',
      name: 'table-resource-list',
      component: TableResourceListPage
    },
    {
      path: '/stores/:storeId/reservations/seating/direct',
      name: 'reservation-arrived-direct-seating',
      component: ReservationArrivedDirectSeatingPage
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: `/stores/${defaultStoreId}/staff`
    }
  ]
})

router.beforeEach(async to => {
  const auth = useAuthSessionStore()

  if (to.meta.public) {
    return true
  }

  await auth.ensureCurrentUser()
  if (!auth.isAuthenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.requiresPlatformAdmin && !auth.user?.roles.includes('platform_admin')) {
    return auth.defaultStoreRoute
  }

  if (to.meta.requiresTenantAdmin) {
    if (!auth.isTenantAdmin) {
      return auth.defaultHomeRoute
    }
    const routeStoreId = String(to.params.storeId || '')
    if (routeStoreId && !auth.user?.storeIds.includes(routeStoreId)) {
      return auth.tenantAdminHomeRoute
    }
  }

  return true
})
