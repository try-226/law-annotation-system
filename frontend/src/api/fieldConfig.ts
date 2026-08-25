import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse } from './types'

export type FieldConfigScope = 'OVERALL' | 'ARTICLE'
export type FieldValueKind = 'SELECT' | 'TEXT' | 'TEXTAREA'

export interface FieldConfigItem {
  fieldKey: string
  displayName: string
  type: FieldValueKind
  scope: FieldConfigScope
  required: boolean
  configurable: boolean
}

export interface FieldConfig {
  fields: FieldConfigItem[]
}

export async function getFieldConfig(): Promise<FieldConfig> {
  const response = await request.get<ApiResponse<FieldConfig>>('/field-config')
  return response.data.data
}

export async function updateFieldRequired(
  fieldKey: string,
  required: boolean,
): Promise<FieldConfig> {
  const { data } = await csrfRequest<ApiResponse<FieldConfig>>({
    method: 'PATCH',
    url: '/field-config',
    data: { fieldKey, required },
  })
  return data.data
}
