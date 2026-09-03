export interface PublicBookingLoginFlow {
  canEnterContact: boolean
  nextStepAfterSelection: 2 | 3
  previousStepFromContact: 1 | 2
  contactStepNumber: 2 | 3
}

export function resolvePublicBookingLoginFlow(
  requireCustomerLogin: boolean | undefined,
  hasCustomer: boolean
): PublicBookingLoginFlow {
  const loginRequired = requireCustomerLogin !== false

  return {
    canEnterContact: !loginRequired || hasCustomer,
    nextStepAfterSelection: loginRequired && !hasCustomer ? 2 : 3,
    previousStepFromContact: loginRequired ? 2 : 1,
    contactStepNumber: loginRequired ? 3 : 2
  }
}
