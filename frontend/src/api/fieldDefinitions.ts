import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, PageResponse } from './types'

export type FieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'MULTI_SELECT' | 'BOOLEAN'
export type FieldDefinitionStatus = 'ACTIVE' | 'INACTIVE'

export interface FieldDefinition {
  id: string
  name: string
  displayName: string
  description: string | null
  fieldType: FieldType
  required: boolean
  options: string[]
  status: FieldDefinitionStatus
  createdAt: string
  updatedAt: string
}

export interface CreateFieldDefinitionPayload {
  name: string
  displayName: string
  description: string | null
  fieldType: FieldType
  required: boolean
  options: string[]
}

export interface UpdateFieldDefinitionPayload {
  displayName: string
  description: string | null
  required: boolean
  options: string[]
  status: FieldDefinitionStatus
}

export async function listFieldDefinitions(page = 0, size = 20): Promise<PageResponse<FieldDefinition>> {
  const { data } = await request.get<ApiResponse<PageResponse<FieldDefinition>>>('/field-definitions', {
    params: { page, size },
  })
  return data.data
}

export async function getFieldDefinition(id: string): Promise<FieldDefinition> {
  const { data } = await request.get<ApiResponse<FieldDefinition>>(`/field-definitions/${id}`)
  return data.data
}

export async function createFieldDefinition(
  payload: CreateFieldDefinitionPayload,
): Promise<FieldDefinition> {
  const { data } = await csrfRequest<ApiResponse<FieldDefinition>>({
    method: 'POST',
    url: '/field-definitions',
    data: payload,
  })
  return data.data
}

export async function updateFieldDefinition(
  id: string,
  payload: UpdateFieldDefinitionPayload,
): Promise<FieldDefinition> {
  const { data } = await csrfRequest<ApiResponse<FieldDefinition>>({
    method: 'PUT',
    url: `/field-definitions/${id}`,
    data: payload,
  })
  return data.data
}

export async function deactivateFieldDefinition(id: string): Promise<FieldDefinition> {
  const { data } = await csrfRequest<ApiResponse<FieldDefinition>>({
    method: 'DELETE',
    url: `/field-definitions/${id}`,
  })
  return data.data
}
