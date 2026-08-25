import assert from 'node:assert/strict'
import test from 'node:test'

import * as lawValidation from '../src/views/law/lawImportValidation.ts'

const { validateLawImportPreview } = lawValidation

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

test('accepts legal Chinese and Arabic article numbers', () => {
  const value = preview()
  value.articles[0].number = '第一百零二条'
  value.articles[1].number = '第102条'
  assert.deepEqual(validateLawImportPreview(value), [])
})

test('rejects article numbers outside the V1.5 pattern', () => {
  for (const number of ['Article 1', '第一百零二条之一', '第 102 条']) {
    const value = preview()
    value.articles[0].number = number
    assert.ok(validateLawImportPreview(value).includes(`条号格式不合法：${number}`))
  }
})

test('rejects an article number over 20 code points', () => {
  const value = preview()
  value.articles[0].number = `第${'一'.repeat(19)}条`
  assert.ok(validateLawImportPreview(value).some((issue) => issue.startsWith('条号格式不合法：')))
})

test('rejects duplicate article numbers', () => {
  const value = preview()
  value.articles[1].number = '第一条'
  assert.ok(validateLawImportPreview(value).includes('条号不能重复：第一条'))
})

test('rejects an article body containing only whitespace', () => {
  const value = preview()
  value.articles[0].body = ' \n\t '
  assert.ok(validateLawImportPreview(value).includes('条文正文须为1至20000个字符'))
})

test('rejects an article body over 20000 code points', () => {
  const value = preview()
  value.articles[0].body = '法'.repeat(20_001)
  assert.ok(validateLawImportPreview(value).includes('条文正文须为1至20000个字符'))
})

test('counts article body length by code point', () => {
  const value = preview()
  value.articles[0].body = '😀'.repeat(20_000)
  assert.deepEqual(validateLawImportPreview(value), [])
})

test('rejects duplicate article client keys', () => {
  const value = preview()
  value.articles[1].clientKey = 'article-a'
  assert.ok(validateLawImportPreview(value).includes('法条技术标识不能重复：article-a'))
})

test('rejects duplicate structure node ids', () => {
  const value = preview()
  value.structure.push({
    nodeId: 'chapter-1', type: 'SECTION', title: '第一节', parentNodeId: null, order: 1, articleRefs: [],
  })
  assert.ok(validateLawImportPreview(value).includes('结构技术标识不能重复：chapter-1'))
})

test('rejects an empty structure title', () => {
  const value = preview()
  value.structure[0].title = '  '
  assert.ok(validateLawImportPreview(value).includes('结构标题须为1至100个字符'))
})

test('rejects a missing structure parent', () => {
  const value = preview()
  value.structure[0].parentNodeId = 'missing'
  assert.ok(validateLawImportPreview(value).includes('结构“第一章”的上级结构无效'))
})

test('rejects a self-referencing structure parent', () => {
  const value = preview()
  value.structure[0].parentNodeId = 'chapter-1'
  assert.ok(validateLawImportPreview(value).includes('结构“第一章”的上级结构无效'))
})

test('rejects a structure parent cycle', () => {
  const value = preview()
  value.structure[0].parentNodeId = 'chapter-2'
  value.structure.push({
    nodeId: 'chapter-2', type: 'CHAPTER', title: '第二章', parentNodeId: 'chapter-1', order: 1, articleRefs: [],
  })
  assert.ok(validateLawImportPreview(value).includes('结构节点不能形成循环'))
})

test('rejects a missing article reference', () => {
  const value = preview()
  value.structure[0].articleRefs.push('missing')
  assert.ok(validateLawImportPreview(value).includes('结构“第一章”包含无效法条引用'))
})

test('rejects mounting one article on multiple structure nodes', () => {
  const value = preview()
  value.structure.push({
    nodeId: 'chapter-2', type: 'CHAPTER', title: '第二章', parentNodeId: null, order: 1, articleRefs: ['article-a'],
  })
  assert.ok(validateLawImportPreview(value).includes('同一法条不能挂载到多个结构节点'))
})

test('rejects invalid base fields using the backend contract', () => {
  const value = preview()
  value.baseInfo.name = '测试\n法'
  value.baseInfo.issuingAuthority = '机关\u0000'
  value.baseInfo.publicationDate = '2026-02-30'
  value.baseInfo.validityStatus = 'UNKNOWN'
  const issues = validateLawImportPreview(value)
  assert.ok(issues.includes('法律名称不得包含换行或控制字符'))
  assert.ok(issues.includes('发布机关不得包含控制字符'))
  assert.ok(issues.includes('发布日期必须是有效的 yyyy-MM-dd 日期'))
  assert.ok(issues.includes('效力状态无效'))
})

test('nextArticleOrder uses max order plus one', () => {
  assert.equal(typeof lawValidation.nextArticleOrder, 'function')
  assert.equal(lawValidation.nextArticleOrder([]), 0)
  assert.equal(lawValidation.nextArticleOrder([{ order: 0 }, { order: 1 }]), 2)
  assert.equal(lawValidation.nextArticleOrder([{ order: 0 }, { order: 3 }]), 4)
  assert.equal(lawValidation.nextArticleOrder([{ order: 0 }, { order: 2 }]), 3)
})
