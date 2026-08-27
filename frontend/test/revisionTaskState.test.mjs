import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildRevisionTaskPayload,
  failedRevisionAnnotatorState,
  loadedRevisionAnnotatorState,
  orderedRevisionArticles,
  resetRevisionAnnotatorState,
  revisionScopeArticles,
  revisionCandidateKind,
  validateRevisionScope,
} from '../src/views/revision/revisionTaskState.ts'
import { annotatorTaskActionLabel, workbenchTitle } from '../src/types/task.ts'

const detail = {
  id: 'law-1',
  name: '测试法',
  issuingAuthority: '测试机关',
  publicationDate: '2026-08-27',
  validityStatus: 'ACTIVE',
  displayStatus: 'COMPLETED',
  structure: [
    { nodeId: 'chapter-2', type: 'CHAPTER', title: '第二章', parentNodeId: null, order: 2, articleIds: ['article-3'] },
    { nodeId: 'section-1', type: 'SECTION', title: '第一节', parentNodeId: 'chapter-1', order: 1, articleIds: ['article-2'] },
    { nodeId: 'chapter-1', type: 'CHAPTER', title: '第一章', parentNodeId: null, order: 1, articleIds: ['article-1'] },
  ],
  articles: [
    { articleId: 'article-4', number: '第四条', body: '未挂载', order: 4 },
    { articleId: 'article-3', number: '第三条', body: '第三条', order: 3 },
    { articleId: 'article-2', number: '第二条', body: '第二条', order: 2 },
    { articleId: 'article-1', number: '第一条', body: '第一条', order: 1 },
  ],
  currentContentVersionId: 'content-1',
  currentContentVersionSeq: 1,
  pendingRevision: false,
  createdAt: '2026-08-27T00:00:00Z',
  updatedAt: '2026-08-27T00:00:00Z',
}

const annotatorOne = {
  id: 'annotator-1', loginAccount: 'annotator-1', name: '标注员一',
  role: 'ANNOTATOR', enabled: true,
  createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}
const annotatorTwo = {
  id: 'annotator-2', loginAccount: 'annotator-2', name: '标注员二',
  role: 'ANNOTATOR', enabled: true,
  createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}
const disabledAnnotator = { ...annotatorOne, id: 'annotator-disabled', enabled: false }

test('修订候选只接受正式完成和待正文修订法律', () => {
  assert.equal(revisionCandidateKind('COMPLETED'), 'ANNOTATION_ONLY')
  assert.equal(revisionCandidateKind('PENDING_REVISION'), 'CONTENT_CHANGE')
  assert.equal(revisionCandidateKind('REVISING'), null)
})

test('主动纠错法条严格按服务器结构 DFS 排列并保留真实 articleId', () => {
  assert.deepEqual(orderedRevisionArticles(detail).map(({ articleId, number }) => ({ articleId, number })), [
    { articleId: 'article-1', number: '第一条' },
    { articleId: 'article-2', number: '第二条' },
    { articleId: 'article-3', number: '第三条' },
    { articleId: 'article-4', number: '第四条' },
  ])
})

test('标注修正型阻止空范围而正文变化型允许空 articleIds', () => {
  assert.match(validateRevisionScope('ANNOTATION_ONLY', false, []), /至少选择整体信息或一个法条/)
  assert.equal(validateRevisionScope('ANNOTATION_ONLY', true, []), null)
  assert.equal(validateRevisionScope('ANNOTATION_ONLY', false, ['article-2']), null)
  assert.equal(validateRevisionScope('CONTENT_CHANGE', false, []), null)
})

test('修订创建 payload 只包含客户端业务字段且正文变化固定空 articleIds', () => {
  assert.deepEqual(buildRevisionTaskPayload({
    candidateKind: 'ANNOTATION_ONLY',
    lawId: 'law-1',
    annotatorId: 'annotator-1',
    taskName: '  主动纠错  ',
    remark: '  备注  ',
    overall: true,
    articleIds: ['article-2'],
  }), {
    lawId: 'law-1', annotatorId: 'annotator-1', taskName: '主动纠错', remark: '备注',
    overall: true, articleIds: ['article-2'],
  })

  assert.deepEqual(buildRevisionTaskPayload({
    candidateKind: 'CONTENT_CHANGE',
    lawId: 'law-2',
    annotatorId: 'annotator-1',
    taskName: '',
    remark: '',
    overall: false,
    articleIds: ['client-must-not-send-this'],
  }), {
    lawId: 'law-2', annotatorId: 'annotator-1', overall: false, articleIds: [],
  })
})

test('标注员重新加载失败和 reset 都清除旧候选、选择及错误状态', () => {
  const firstSuccess = loadedRevisionAnnotatorState(
    [annotatorOne],
    '',
    false,
  )
  assert.deepEqual(firstSuccess, {
    annotators: [annotatorOne], annotatorId: '', annotatorsError: '',
  })

  const failed = failedRevisionAnnotatorState('候选标注员加载失败，请稍后重试')
  assert.deepEqual(failed, {
    annotators: [], annotatorId: '', annotatorsError: '候选标注员加载失败，请稍后重试',
  })

  assert.deepEqual(resetRevisionAnnotatorState(), {
    annotators: [], annotatorId: '', annotatorsError: '',
  })
})

test('标注员重新加载成功只采用最新候选并清除已失效选择', () => {
  assert.deepEqual(loadedRevisionAnnotatorState(
    [annotatorTwo, disabledAnnotator],
    annotatorOne.id,
    true,
  ), {
    annotators: [annotatorTwo], annotatorId: '', annotatorsError: '',
  })
  assert.deepEqual(loadedRevisionAnnotatorState(
    [annotatorTwo],
    annotatorTwo.id,
    true,
  ), {
    annotators: [annotatorTwo], annotatorId: annotatorTwo.id, annotatorsError: '',
  })
})

test('标注员加载状态只替换 annotator 字段，不清空其他有效修订表单输入', () => {
  const form = {
    candidateKind: 'ANNOTATION_ONLY', lawId: 'law-1', annotatorId: annotatorOne.id,
    taskName: '修订任务', remark: '保留备注', overall: true, articleIds: ['article-1'],
  }
  const next = failedRevisionAnnotatorState('候选标注员加载失败，请稍后重试')
  const nextForm = { ...form, annotatorId: next.annotatorId }
  assert.deepEqual(nextForm, {
    candidateKind: 'ANNOTATION_ONLY', lawId: 'law-1', annotatorId: '',
    taskName: '修订任务', remark: '保留备注', overall: true, articleIds: ['article-1'],
  })
})

test('普通与修订任务共用路由但显示各自操作和工作台文案', () => {
  assert.equal(annotatorTaskActionLabel('ORDINARY', 'ANNOTATING'), '继续标注')
  assert.equal(annotatorTaskActionLabel('ORDINARY', 'PARTIALLY_REJECTED'), '查看标注')
  assert.equal(annotatorTaskActionLabel('REVISION', 'PENDING_ANNOTATION'), '开始修订')
  assert.equal(annotatorTaskActionLabel('REVISION', 'ANNOTATING'), '继续修订')
  assert.equal(annotatorTaskActionLabel('REVISION', 'PARTIALLY_REJECTED'), '修改修订')
  assert.equal(annotatorTaskActionLabel('REVISION', 'PENDING_REREVIEW'), '查看修订')
  assert.equal(workbenchTitle('ORDINARY'), '标注工作台')
  assert.equal(workbenchTitle('REVISION'), '修订工作台')
})

test('任务详情使用快照法条编号展示 scope 和 mandatory', () => {
  const task = {
    contentVersionSnapshot: {
      articles: [
        { articleId: 'article-2', number: '第二条', body: '正文', order: 2 },
        { articleId: 'article-1', number: '第一条', body: '正文', order: 1 },
      ],
    },
    revisionScope: {
      mode: 'CONTENT_CHANGE', overall: false,
      articleIds: ['article-1', 'missing-id'], mandatoryArticleIds: ['article-1'],
    },
  }
  assert.deepEqual(revisionScopeArticles(task), [
    { articleId: 'article-1', label: '第一条', mandatory: true },
    { articleId: 'missing-id', label: 'missing-id', mandatory: false },
  ])
  assert.deepEqual(revisionScopeArticles({
    ...task,
    revisionScope: { mode: 'CONTENT_CHANGE', overall: false, articleIds: [], mandatoryArticleIds: [] },
  }), [])
})
