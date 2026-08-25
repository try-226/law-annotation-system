import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createLawDetailDraftState,
  mergeLawDetailDraftState,
} from '../src/views/law/lawDetailDraftState.ts'

function detail() {
  return {
    id: 'law-1',
    name: '服务器名称',
    issuingAuthority: '制定机关',
    publicationDate: '2026-08-25',
    validityStatus: 'ACTIVE',
    displayStatus: 'UNANNOTATED',
    structure: [
      { nodeId: 'chapter-1', type: 'CHAPTER', title: '第一章', parentNodeId: null, order: 0, articleIds: ['article-a'] },
    ],
    articles: [
      { articleId: 'article-a', number: '第一条', body: '正文 A', order: 0 },
      { articleId: 'article-b', number: '第二条', body: '正文 B', order: 1 },
    ],
    currentContentVersionId: 'content-1',
    currentContentVersionSeq: 1,
    pendingRevision: false,
    createdAt: '2026-08-25T00:00:00Z',
    updatedAt: '2026-08-25T00:00:00Z',
  }
}

function response(changes = {}) {
  return Object.assign(structuredClone(detail()), changes)
}

test('saving an article preserves an unsaved base draft and refreshes server metadata', () => {
  const state = createLawDetailDraftState(detail())
  state.base.name = '尚未保存的名称'
  const server = response({
    displayStatus: 'PENDING_REVISION',
    pendingRevision: true,
    currentContentVersionId: 'content-2',
    currentContentVersionSeq: 2,
    updatedAt: '2026-08-25T01:00:00Z',
    articles: [
      { articleId: 'article-a', number: '第一条', body: '已保存正文 A', order: 0 },
      detail().articles[1],
    ],
  })

  const merged = mergeLawDetailDraftState(state, server, { region: 'article', articleId: 'article-a' })

  assert.equal(merged.base.name, '尚未保存的名称')
  assert.equal(merged.articles[0].body, '已保存正文 A')
  assert.equal(merged.detail.displayStatus, 'PENDING_REVISION')
  assert.equal(merged.detail.pendingRevision, true)
  assert.equal(merged.detail.currentContentVersionId, 'content-2')
  assert.equal(merged.detail.updatedAt, '2026-08-25T01:00:00Z')
})

test('saving an article preserves an unsaved structure draft', () => {
  const state = createLawDetailDraftState(detail())
  state.structures[0].title = '尚未保存的结构标题'

  const merged = mergeLawDetailDraftState(state, response(), { region: 'article', articleId: 'article-a' })

  assert.equal(merged.structures[0].title, '尚未保存的结构标题')
})

test('saving article A accepts A from the server and preserves dirty article B', () => {
  const state = createLawDetailDraftState(detail())
  state.articles[0].body = '准备保存的 A'
  state.articles[1].body = '尚未保存的 B'
  const server = response({
    articles: [
      { articleId: 'article-a', number: '第一条', body: '服务器确认的 A', order: 0 },
      detail().articles[1],
    ],
  })

  const merged = mergeLawDetailDraftState(state, server, { region: 'article', articleId: 'article-a' })

  assert.equal(merged.articles[0].body, '服务器确认的 A')
  assert.equal(merged.articles[1].body, '尚未保存的 B')
})

test('saving structure accepts the saved structure and preserves an unsaved base draft', () => {
  const state = createLawDetailDraftState(detail())
  state.base.name = '尚未保存的名称'
  state.structures[0].title = '准备保存的结构标题'
  const server = response({
    structure: [
      { nodeId: 'chapter-1', type: 'CHAPTER', title: '服务器确认的结构标题', parentNodeId: null, order: 0, articleIds: ['article-a'] },
    ],
  })

  const merged = mergeLawDetailDraftState(state, server, { region: 'structure' })

  assert.equal(merged.base.name, '尚未保存的名称')
  assert.equal(merged.structures[0].title, '服务器确认的结构标题')
})

test('article collection changes add and remove server-confirmed entities without losing other dirty articles', () => {
  const state = createLawDetailDraftState(detail())
  state.articles[1].body = '尚未保存的 B'
  const added = mergeLawDetailDraftState(state, response({
    articles: [
      ...detail().articles,
      { articleId: 'article-c', number: '第三条', body: '新增正文 C', order: 2 },
    ],
  }), { region: 'articles' })

  assert.deepEqual(added.articles.map((article) => article.articleId), ['article-a', 'article-b', 'article-c'])
  assert.equal(added.articles[1].body, '尚未保存的 B')

  const removed = mergeLawDetailDraftState(added, response({ articles: [detail().articles[1]] }), { region: 'articles' })
  assert.deepEqual(removed.articles.map((article) => article.articleId), ['article-b'])
  assert.equal(removed.articles[0].body, '尚未保存的 B')
})

test('deleting an article removes its refs from an unsaved structure draft', () => {
  const state = createLawDetailDraftState(detail())
  state.structures[0].title = '尚未保存的结构标题'
  state.structures[0].articleRefs = ['article-a', 'article-b']
  const server = response({
    structure: [
      { nodeId: 'chapter-1', type: 'CHAPTER', title: '第一章', parentNodeId: null, order: 0, articleIds: [] },
    ],
    articles: [detail().articles[1]],
  })

  const merged = mergeLawDetailDraftState(state, server, { region: 'articles' })

  assert.equal(merged.structures[0].title, '尚未保存的结构标题')
  assert.deepEqual(merged.structures[0].articleRefs, ['article-b'])
})
