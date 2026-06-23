import { createRouter, createWebHistory } from 'vue-router'

import CleaningCompletePage from '../pages/CleaningCompletePage.vue'
import QueueCallPage from '../pages/QueueCallPage.vue'
import QueueTicketListPage from '../pages/QueueTicketListPage.vue'
import ReservationArrivedDirectSeatingPage from '../pages/ReservationArrivedDirectSeatingPage.vue'
import ReservationArrivedToQueuePage from '../pages/ReservationArrivedToQueuePage.vue'
import ReservationCheckInPage from '../pages/ReservationCheckInPage.vue'
import ReservationCreatePage from '../pages/ReservationCreatePage.vue'
import ReservationTodayViewPage from '../pages/ReservationTodayViewPage.vue'
import SeatingFromCalledQueuePage from '../pages/SeatingFromCalledQueuePage.vue'
import StoreStaffHomePage from '../pages/StoreStaffHomePage.vue'
import TableResourceListPage from '../pages/TableResourceListPage.vue'
import WalkInDirectSeatingPage from '../pages/WalkInDirectSeatingPage.vue'

const localValidationStoreId = '20000000-0000-0000-0000-000000000983'
const defaultStoreId = import.meta.env.VITE_DEFAULT_STORE_ID || localValidationStoreId

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: `/stores/${defaultStoreId}/staff`
    },
    {
      path: '/stores/:storeId/staff',
      name: 'store-staff-home',
      component: StoreStaffHomePage
    },
    {
      path: '/stores/:storeId/walk-ins/direct-seating',
      name: 'walk-in-direct-seating',
      component: WalkInDirectSeatingPage
    },
    {
      path: '/stores/:storeId/cleaning',
      name: 'cleaning-complete',
      component: CleaningCompletePage
    },
    {
      path: '/stores/:storeId/reservations/create',
      name: 'reservation-create',
      component: ReservationCreatePage
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
