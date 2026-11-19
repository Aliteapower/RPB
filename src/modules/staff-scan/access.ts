import type {AuthUser} from '../../types/auth'

export function canRedeemVoucher(user: AuthUser | null) {
  return user?.actorType === 'tenant_admin' && user.permissions.includes('wallet.cards.redeem')
}

export function canOpenStaffScan(user: AuthUser | null) {
  return canRedeemVoucher(user) || user?.permissions.some(permission => [
    'wallet.view', 'wallet.cards.view', 'wallet.charge.create', 'wallet.topup.create',
    'wallet.points.view', 'wine_storage.view',
  ].includes(permission)) === true
}
