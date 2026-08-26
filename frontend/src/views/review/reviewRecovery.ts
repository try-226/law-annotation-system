const ASSIGNED_REVIEW_READ_ATTEMPTS = 3
const ASSIGNED_REVIEW_RETRY_DELAY_MS = 150

async function waitBeforeAssignedReviewRetry(): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, ASSIGNED_REVIEW_RETRY_DELAY_MS))
}

export async function readAssignedReviewWithRetry<T>(
  readReview: () => Promise<T>,
  isNotStarted: (error: unknown) => boolean,
  waitBeforeRetry: () => Promise<void> = waitBeforeAssignedReviewRetry,
): Promise<T> {
  for (let attempt = 1; attempt <= ASSIGNED_REVIEW_READ_ATTEMPTS; attempt += 1) {
    try {
      return await readReview()
    } catch (error: unknown) {
      if (!isNotStarted(error) || attempt === ASSIGNED_REVIEW_READ_ATTEMPTS) throw error
      await waitBeforeRetry()
    }
  }
  throw new Error('unreachable')
}
