export function annotationSubmissionPath(
  taskId: string,
  action: 'review' | 'rereview',
): string {
  return `/tasks/${taskId}/${action === 'rereview' ? 'submit-rereview' : 'submit-review'}`
}
