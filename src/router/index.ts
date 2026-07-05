import { createRouter, createWebHistory } from 'vue-router'

import { useAuthSessionStore } from '../stores/authSession'

const CleaningCompletePage = () => import('../pages/CleaningCompletePage.vue')
const LoginPage = () => import('../pages/LoginPage.vue')
const PlatformCallScreenSeedPage = () => import('../pages/PlatformCallScreenSeedPage.vue')
const PlatformI18nCatalogPage = () => import('../pages/PlatformI18nCatalogPage.vue')
const PlatformProductLinesPage = () => import('../pages/PlatformProductLinesPage.vue')
const PlatformProfilePage = () => import('../pages/PlatformProfilePage.vue')
const PlatformReservationMealPeriodSeedPage = () => import('../pages/PlatformReservationMealPeriodSeedPage.vue')
const PlatformReservationShareTemplateSeedPage = () => import('../pages/PlatformReservationShareTemplateSeedPage.vue')
const PlatformTenantBillingPage = () => import('../pages/PlatformTenantBillingPage.vue')
const PlatformTenantFormPage = () => import('../pages/PlatformTenantFormPage.vue')
const PlatformTenantsPage = () => import('../pages/PlatformTenantsPage.vue')
const PublicBookingPage = () => import('../pages/PublicBookingPage.vue')
const QueueCallPage = () => import('../pages/QueueCallPage.vue')
const QueueDisplayPage = () => import('../pages/QueueDisplayPage.vue')
const QueueTicketListPage = () => import('../pages/QueueTicketListPage.vue')
const ReservationArrivedDirectSeatingPage = () => import('../pages/ReservationArrivedDirectSeatingPage.vue')
const ReservationArrivedToQueuePage = () => import('../pages/ReservationArrivedToQueuePage.vue')
const ReservationCheckInPage = () => import('../pages/ReservationCheckInPage.vue')
const ReservationPublicSharePage = () => import('../pages/ReservationPublicSharePage.vue')
const ReservationTodayViewPage = () => import('../pages/ReservationTodayViewPage.vue')
const SeatingFromCalledQueuePage = () => import('../pages/SeatingFromCalledQueuePage.vue')
const StoreStaffHomePage = () => import('../pages/StoreStaffHomePage.vue')
const TableResourceListPage = () => import('../pages/TableResourceListPage.vue')
const TenantAdminCallScreenPage = () => import('../pages/TenantAdminCallScreenPage.vue')
const TenantAdminI18nCatalogPage = () => import('../pages/TenantAdminI18nCatalogPage.vue')
const TenantAdminProfilePage = () => import('../pages/TenantAdminProfilePage.vue')
const TenantAdminPublicBookingPage = () => import('../pages/TenantAdminPublicBookingPage.vue')
const TenantAdminReservationSharePage = () => import('../pages/TenantAdminReservationSharePage.vue')
const TenantAdminSettingsPage = () => import('../pages/TenantAdminSettingsPage.vue')
const TenantAdminStaffFormPage = () => import('../pages/TenantAdminStaffFormPage.vue')
const TenantAdminStaffPage = () => import('../pages/TenantAdminStaffPage.vue')
const TenantAdminTableFormPage = () => import('../pages/TenantAdminTableFormPage.vue')
const TenantAdminTablesPage = () => import('../pages/TenantAdminTablesPage.vue')
const WalkInDirectSeatingPage = () => import('../pages/WalkInDirectSeatingPage.vue')
const WalkInQueuePage = () => import('../pages/WalkInQueuePage.vue')

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
      path: '/book/:storeId',
      name: 'public-booking',
      component: PublicBookingPage,
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
      path: '/platform/i18n/catalog',
      name: 'platform-i18n-catalog',
      component: PlatformI18nCatalogPage,
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
      path: '/platform/reservation/meal-period-seed',
      name: 'platform-reservation-meal-period-seed',
      component: PlatformReservationMealPeriodSeedPage,
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
      path: '/stores/:storeId/admin/staff/me/edit',
      name: 'tenant-admin-staff-self-edit',
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
      path: '/stores/:storeId/admin/i18n-catalog',
      name: 'tenant-admin-i18n-catalog',
      component: TenantAdminI18nCatalogPage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/share-template',
      name: 'tenant-admin-reservation-share',
      component: TenantAdminReservationSharePage,
      meta: { requiresTenantAdmin: true }
    },
    {
      path: '/stores/:storeId/admin/public-booking',
      name: 'tenant-admin-public-booking',
      component: TenantAdminPublicBookingPage,
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
