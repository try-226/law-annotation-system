import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildLawExportRequest,
  ExportSelectionError,
  filenameFromContentDisposition,
  formalAvailability,
} from '../src/views/export/exportDownload.ts'

test('WHOLE 请求不携带 articleIds', () => {
  assert.deepEqual(buildLawExportRequest('WHOLE', ['article-1'], 'PLAIN', 'CSV'), {
    scope: 'WHOLE', articleIds: [], type: 'PLAIN', format: 'CSV',
  })
  assert.deepEqual(buildLawExportRequest('WHOLE', [], 'PLAIN', 'JSON'), {
    scope: 'WHOLE', articleIds: [], type: 'PLAIN', format: 'JSON',
  })
})

test('SELECTED 请求要求至少一条并消除重复 ID', () => {
  assert.throws(
    () => buildLawExportRequest('SELECTED', [], 'FORMAL', 'JSON'),
    ExportSelectionError,
  )
  assert.deepEqual(buildLawExportRequest(
    'SELECTED', ['article-2', 'article-1', 'article-2'], 'FORMAL', 'JSON',
  ), {
    scope: 'SELECTED', articleIds: ['article-2', 'article-1'], type: 'FORMAL', format: 'JSON',
  })
  assert.deepEqual(buildLawExportRequest('SELECTED', ['article-1'], 'FORMAL', 'CSV'), {
    scope: 'SELECTED', articleIds: ['article-1'], type: 'FORMAL', format: 'CSV',
  })
})

test('Content-Disposition 优先解析 UTF-8 文件名并清理非法路径字符', () => {
  assert.equal(filenameFromContentDisposition(
    "attachment; filename*=UTF-8''%E6%B3%95%E5%BE%8B%E7%BB%93%E6%9E%9C.json",
    'fallback.json',
  ), '法律结果.json')
  assert.equal(filenameFromContentDisposition('attachment; filename="../law.csv"', 'fallback.csv'), '.._law.csv')
  assert.equal(filenameFromContentDisposition(undefined, 'fallback.csv'), 'fallback.csv')
})

test('FORMAL 可用性使用 current A 与 semantic C 事实配对', () => {
  const baseLaw = {
    id: 'law-1',
    currentAnnotationVersionId: 'annotation-1',
    currentContentVersionId: 'content-1',
    pendingRevision: false,
  }
  const annotation = { lawId: 'law-1', annotationVersionId: 'annotation-1', contentVersionId: 'content-1' }
  assert.equal(formalAvailability(baseLaw, annotation).available, true)
  assert.equal(formalAvailability({ ...baseLaw, pendingRevision: true }, annotation).available, true)
  assert.equal(formalAvailability(
    { ...baseLaw, currentContentVersionId: 'content-2', pendingRevision: true },
    annotation,
  ).available, false)
  assert.equal(formalAvailability({ ...baseLaw, currentAnnotationVersionId: null }, null).available, false)
})
