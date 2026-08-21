import { reactive, readonly } from 'vue'

export type NoticeType = 'success' | 'error' | 'info'

interface NoticeState {
  visible: boolean
  message: string
  type: NoticeType
}

const state = reactive<NoticeState>({ visible: false, message: '', type: 'info' })
let dismissTimer: ReturnType<typeof setTimeout> | undefined

export const noticeState = readonly(state)

export function notify(message: string, type: NoticeType = 'info'): void {
  if (dismissTimer) {
    clearTimeout(dismissTimer)
  }
  state.message = message
  state.type = type
  state.visible = true
  dismissTimer = setTimeout(dismissNotice, 3500)
}

export function dismissNotice(): void {
  state.visible = false
  dismissTimer = undefined
}
