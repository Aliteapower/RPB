import { translate } from '../i18n'

const RESERVATION_CREATE_ERROR_MESSAGES: Record<string, string> = {
  'reservation.start_in_past': 'reservationCreate.errors.startInPast',
  'reservation.invalid_party_size': 'reservationCreate.errors.invalidPartySize',
  'reservation.invalid_time_range': 'reservationCreate.errors.invalidTimeRange',
  'reservation.time_slot_unavailable': 'reservationCreate.errors.timeSlotUnavailable',
  'reservation.invalid_phone_e164': 'reservationCreate.errors.invalidPhoneE164',
  'reservation.request_failed': 'reservationCreate.errors.requestFailed',
  'reservation.network_failure': 'reservationCreate.errors.networkFailure',
  'reservation.forbidden': 'reservationCreate.errors.forbidden',
  'reservation.customer_not_found': 'reservationCreate.errors.customerNotFound',
  'reservation.duplicate_active': 'reservationCreate.errors.duplicateActive',
  'reservation.capacity_insufficient': 'reservationCreate.errors.capacityInsufficient'
}

export function formatReservationCreateErrorMessage(messageKey: string | undefined): string {
  if (!messageKey) {
    return translate('reservationCreate.errors.requestFailed')
  }

  const frontendMessageKey = RESERVATION_CREATE_ERROR_MESSAGES[messageKey]
  return frontendMessageKey ? translate(frontendMessageKey) : messageKey
}
