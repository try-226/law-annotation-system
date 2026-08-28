import request from './request'
import type { ApiResponse } from './types'
import {
  annotationVersionHistoryPath,
  contentVersionHistoryPath,
  lawAuditHistoryPath,
  lawHistoryPath,
  taskHistoryPath,
} from './historyPath'
import type {
  AnnotationVersionHistory,
  ContentVersionHistory,
  LawAuditHistory,
  LawHistory,
  TaskHistory,
} from '../types/history'

export async function getLawHistory(lawId: string): Promise<LawHistory> {
  const { data } = await request.get<ApiResponse<LawHistory>>(lawHistoryPath(lawId))
  return data.data
}

export async function getContentVersionHistory(
  lawId: string,
  contentVersionId: string,
): Promise<ContentVersionHistory> {
  const { data } = await request.get<ApiResponse<ContentVersionHistory>>(
    contentVersionHistoryPath(lawId, contentVersionId),
  )
  return data.data
}

export async function getAnnotationVersionHistory(
  lawId: string,
  annotationVersionId: string,
): Promise<AnnotationVersionHistory> {
  const { data } = await request.get<ApiResponse<AnnotationVersionHistory>>(
    annotationVersionHistoryPath(lawId, annotationVersionId),
  )
  return data.data
}

export async function getLawAuditHistory(lawId: string, auditId: string): Promise<LawAuditHistory> {
  const { data } = await request.get<ApiResponse<LawAuditHistory>>(
    lawAuditHistoryPath(lawId, auditId),
  )
  return data.data
}

export async function getTaskHistory(lawId: string, taskId: string): Promise<TaskHistory> {
  const { data } = await request.get<ApiResponse<TaskHistory>>(taskHistoryPath(lawId, taskId))
  return data.data
}
