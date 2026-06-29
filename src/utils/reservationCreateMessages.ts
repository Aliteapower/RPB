const RESERVATION_CREATE_ERROR_MESSAGES: Record<string, string> = {
  'reservation.start_in_past': '预约开始时间不能早于当前时间，请选择稍后的时间。',
  'reservation.invalid_party_size': '人数必须大于 0。',
  'reservation.invalid_time_range': '预约时间不正确，请重新选择时间。',
  'reservation.time_slot_unavailable': '请选择门店餐段内的可预约时间。',
  'reservation.invalid_phone_e164': '手机号必须是 8 位新加坡号码。',
  'reservation.request_failed': '预约创建失败，请稍后重试。',
  'reservation.network_failure': '网络连接失败，请检查后重试。',
  'reservation.forbidden': '当前账号没有创建预约权限。',
  'reservation.customer_not_found': '未找到对应顾客，请检查手机号或重新填写。',
  'reservation.duplicate_active': '该顾客已有进行中的预约。',
  'reservation.capacity_insufficient': '当前时段容量不足，请调整时间或人数。'
}

export function formatReservationCreateErrorMessage(messageKey: string | undefined): string {
  if (!messageKey) {
    return '预约创建失败，请稍后重试。'
  }

  return RESERVATION_CREATE_ERROR_MESSAGES[messageKey] ?? messageKey
}
