import request from './request'
import type { ApiResponse } from './types'
import type { DashboardSummary, DashboardTodos } from '../types/dashboard'

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await request.get<ApiResponse<DashboardSummary>>('/dashboard/summary')
  return data.data
}

export async function getDashboardTodos(): Promise<DashboardTodos> {
  const { data } = await request.get<ApiResponse<DashboardTodos>>('/dashboard/todos')
  return data.data
}
