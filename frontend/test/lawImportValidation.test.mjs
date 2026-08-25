import assert from 'node:assert/strict'
import test from 'node:test'

import { validateLawImportPreview } from '../src/views/law/lawImportValidation.ts'

function preview() {
  return {
    baseInfo: {
      name: '测试法',
      issuingAuthority: '制定机关',
      publicationDate: '2026-08-25',
      validityStatus: 'ACTIVE',
    },
    structure: [
      { nodeId: 'chapter-1', type: 'CHAPTER', title: '第一章', parentNodeId: null, order: 0, articleRefs: ['article-a'] },
    ],
    articles: [
      { clientKey: 'article-a', number: '第一条', body: '正文 A', order: 0 },
      { clientKey: 'article-b', number: '第二条', body: '正文 B', order: 1 },
    ],
    warnings: [],
    validationIssues: [{ code: 'OLD', field: null, articleIndex: null, articleNumber: null, structurePath: null, message: '已修正的解析诊断' }],
  }
}

test('rejects a fractional article order', () => {
  const value = preview()
  value.articles[0].order = 1.5
  assert.ok(validateLawImportPreview(value).includes('法条顺序必须是非负整数'))
})

test('rejects a negative article order', () => {
  const value = preview()
  value.articles[0].order = -1
  assert.ok(validateLawImportPreview(value).includes('法条顺序必须是非负整数'))
})

test('rejects duplicate article orders', () => {
  const value = preview()
  value.articles[1].order = 0
  assert.ok(validateLawImportPreview(value).includes('法条顺序不能重复：0'))
})

test('rejects a fractional structure order without requiring structure order uniqueness', () => {
  const value = preview()
  value.structure[0].order = 2.5
  assert.ok(validateLawImportPreview(value).includes('结构顺序必须是非负整数'))

  const duplicateAllowed = preview()
  duplicateAllowed.structure.push({
    nodeId: 'chapter-2', type: 'CHAPTER', title: '第二章', parentNodeId: null, order: 0, articleRefs: [],
  })
  assert.deepEqual(validateLawImportPreview(duplicateAllowed), [])
})

test('old parse validation issues do not block a currently valid preview', () => {
  assert.deepEqual(validateLawImportPreview(preview()), [])
})
