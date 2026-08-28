export type ExportScope = 'WHOLE' | 'SELECTED'
export type ExportType = 'PLAIN' | 'FORMAL'
export type ExportFormat = 'CSV' | 'JSON'

export interface LawExportRequest {
  scope: ExportScope
  articleIds: string[]
  type: ExportType
  format: ExportFormat
}

export interface ExportDownload {
  blob: Blob
  filename: string
}
