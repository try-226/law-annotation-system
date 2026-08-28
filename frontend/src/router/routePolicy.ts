import type { Role } from '../api/types'

export type LandingRouteName = 'dashboard' | 'my-tasks'

export function landingRouteName(role: Role): LandingRouteName {
  return role === 'ADMIN' ? 'dashboard' : 'my-tasks'
}
