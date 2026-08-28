import type { ParsedFailure } from './errors'

export function isRetryableFailure(failure: ParsedFailure): boolean {
  return failure.network || (failure.status !== undefined && failure.status >= 500)
}
