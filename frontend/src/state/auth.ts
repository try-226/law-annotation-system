import { computed, reactive, readonly } from 'vue'

import {
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  type LoginPayload,
} from '../api/auth'
import { clearCsrfToken } from '../api/csrf'
import type { CurrentUser } from '../api/types'
import { parseFailure } from '../utils/errors'

export type AuthStatus = 'unknown' | 'restoring' | 'authenticated' | 'anonymous' | 'restore-error'

interface AuthState {
  user: CurrentUser | null
  status: AuthStatus
}

const state = reactive<AuthState>({
  user: null,
  status: 'unknown',
})

let restorePromise: Promise<void> | null = null

export const authState = readonly(state)
export const isAuthenticated = computed(() => state.status === 'authenticated' && state.user !== null)

export async function restoreSession(force = false): Promise<void> {
  if (restorePromise) {
    return restorePromise
  }
  if (!force && state.status !== 'unknown' && state.status !== 'restore-error') {
    return
  }

  state.status = 'restoring'
  restorePromise = fetchCurrentUser()
    .then((user) => {
      state.user = user
      state.status = 'authenticated'
    })
    .catch((error: unknown) => {
      const failure = parseFailure(error)
      if (failure.status === 401) {
        clearAuthentication()
        return
      }
      state.status = 'restore-error'
    })
    .finally(() => {
      restorePromise = null
    })

  return restorePromise
}

export async function login(payload: LoginPayload): Promise<CurrentUser> {
  const user = await loginRequest(payload)
  state.user = user
  state.status = 'authenticated'
  return user
}

export async function logout(): Promise<void> {
  try {
    await logoutRequest()
  } catch (error: unknown) {
    if (parseFailure(error).status !== 401) {
      throw error
    }
  }
  clearAuthentication()
}

export function setCurrentUser(user: CurrentUser): void {
  state.user = user
  state.status = 'authenticated'
}

export function clearAuthentication(): void {
  state.user = null
  state.status = 'anonymous'
  clearCsrfToken()
}
