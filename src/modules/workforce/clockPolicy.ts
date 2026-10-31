import type {ClockResult} from './types'

/** Resolve the policy of the action's business day, retaining active-session snapshots. */
export function resolveClockPolicy(data:ClockResult|null, selected:string) {
  if (!data) return undefined
  if (data.active) return data.active.clockPolicy
  if (selected) return data.policies?.[selected] ?? data.policy
  // Eligible shifts can belong to an earlier business day with a different policy.
  // Wait for selection instead of treating today's free policy as an exemption.
  if (data.schedules.length) return undefined
  return data.policy
}
