import assert from 'node:assert/strict'
import test from 'node:test'

import {
  normalizeArticlePayload,
  normalizeOverallPayload,
  validateArticleDraft,
  validateOverallDraft,
} from '../src/views/annotation/annotationValidation.ts'

test('允许保存尚未满足必填项的阶段性草稿', () => {
  assert.deepEqual(validateOverallDraft({ lawCategory: '', overallKeywords: '', summary: '', overallNote: '' }), {})
  assert.deepEqual(validateArticleDraft({ itemType: '', keywords: '', subjects: '', legalLiability: '', annotationNote: '' }), {})
})

test('关键词按中英文逗号分隔、去空格并规范为英文逗号', () => {
  const form = { lawCategory: ' 民事 ', overallKeywords: ' 合同， 违约 ', summary: ' 摘要 ', overallNote: '' }
  assert.deepEqual(validateOverallDraft(form), {})
  assert.deepEqual(normalizeOverallPayload(form), {
    lawCategory: '民事', overallKeywords: '合同,违约', summary: '摘要', overallNote: null,
  })
})

test('拒绝空关键词、超量关键词和超长单关键词', () => {
  assert.match(validateArticleDraft({ itemType: '', keywords: '一,,二', subjects: '', legalLiability: '', annotationNote: '' }).keywords, /不能为空/)
  assert.match(validateArticleDraft({ itemType: '', keywords: Array.from({ length: 21 }, (_, index) => `k${index}`).join(','), subjects: '', legalLiability: '', annotationNote: '' }).keywords, /最多20个/)
  assert.match(validateArticleDraft({ itemType: '', keywords: '关'.repeat(31), subjects: '', legalLiability: '', annotationNote: '' }).keywords, /1至30/)
})

test('拒绝控制字符、超长文本和非法枚举', () => {
  assert.match(validateOverallDraft({ lawCategory: '民事', overallKeywords: '', summary: '第一行\n第二行', overallNote: '' }).summary, /控制字符/)
  assert.match(validateArticleDraft({ itemType: 'UNKNOWN', keywords: '', subjects: '', legalLiability: '', annotationNote: '' }).itemType, /允许范围/)
  assert.match(validateArticleDraft({ itemType: 'OTHER', keywords: '', subjects: '人'.repeat(201), legalLiability: '', annotationNote: '' }).subjects, /200/)
})

test('法条 payload 保留后端枚举并将空文本转为 null', () => {
  assert.deepEqual(normalizeArticlePayload({
    itemType: 'DEFINITION', keywords: ' 定义 ', subjects: '', legalLiability: '', annotationNote: '',
  }), {
    itemType: 'DEFINITION', keywords: '定义', subjects: null, legalLiability: null, annotationNote: null,
  })
})
