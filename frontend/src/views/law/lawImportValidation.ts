import type {
  LawBaseInfo,
  LawImportPreview,
  LawStructureInput,
} from '../../types/law'

export interface LawArticleDraft {
  number: string
  body: string
  order: number
}

const ARTICLE_NUMBER_PATTERN = /^第(?:[零〇一二三四五六七八九十百千万两]+|[1-9]\d*)条$/
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f-\u009f]/
const VALIDITY_STATUSES = new Set<string>(['ACTIVE', 'NOT_EFFECTIVE', 'INVALID', 'REPEALED'])
const LEADING_BLANK_LINES = /^(?:[^\S\r\n\v\f\u0085\u2028\u2029]*(?:\r\n|[\n\v\f\r\u0085\u2028\u2029]))+/
const TRAILING_BLANK_LINES = /(?:(?:\r\n|[\n\v\f\r\u0085\u2028\u2029])[^\S\r\n\v\f\u0085\u2028\u2029]*)+$/

function codePointLength(value: string): number {
  return Array.from(value).length
}

function normalizedArticleBody(value: string): string {
  return value.replace(LEADING_BLANK_LINES, '').replace(TRAILING_BLANK_LINES, '')
}

function isValidIsoDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
}

export function nextArticleOrder(articles: Array<{ order: number }>): number {
  const validOrders = articles
    .map((article) => article.order)
    .filter((order) => Number.isInteger(order) && order >= 0)
  return validOrders.length === 0 ? 0 : Math.max(...validOrders) + 1
}

export function validateLawBaseInfo(value: LawBaseInfo): string[] {
  const issues: string[] = []
  const name = value.name ?? ''
  const authority = value.issuingAuthority ?? ''
  if (CONTROL_CHARACTER_PATTERN.test(name)) {
    issues.push('法律名称不得包含换行或控制字符')
  } else if (codePointLength(name.trim()) < 1 || codePointLength(name.trim()) > 100) {
    issues.push('法律名称须为1至100个字符')
  }
  if (CONTROL_CHARACTER_PATTERN.test(authority)) {
    issues.push('发布机关不得包含控制字符')
  } else if (codePointLength(authority.trim()) < 1 || codePointLength(authority.trim()) > 100) {
    issues.push('发布机关须为1至100个字符')
  }
  if (!isValidIsoDate(value.publicationDate)) {
    issues.push('发布日期必须是有效的 yyyy-MM-dd 日期')
  }
  if (!value.validityStatus || !VALIDITY_STATUSES.has(value.validityStatus)) {
    issues.push('效力状态无效')
  }
  return issues
}

export function validateLawArticle(
  article: LawArticleDraft,
  allArticles: LawArticleDraft[],
): string[] {
  const issues: string[] = []
  const number = article.number ?? ''
  if (codePointLength(number) < 1
      || codePointLength(number) > 20
      || !ARTICLE_NUMBER_PATTERN.test(number)) {
    issues.push(`条号格式不合法：${number || '未填写'}`)
  } else if (allArticles.some((candidate) => candidate !== article && candidate.number === number)) {
    issues.push(`条号不能重复：${number}`)
  }

  const body = normalizedArticleBody(article.body ?? '')
  if (body.trim().length === 0 || codePointLength(body) > 20_000) {
    issues.push('条文正文须为1至20000个字符')
  }

  if (!Number.isInteger(article.order) || article.order < 0) {
    issues.push('法条顺序必须是非负整数')
  } else if (allArticles.some((candidate) => candidate !== article && candidate.order === article.order)) {
    issues.push(`法条顺序不能重复：${article.order}`)
  }
  return issues
}

export function validateLawArticles(articles: LawArticleDraft[]): string[] {
  if (articles.length === 0) return ['至少需要一条法条']
  return articles.flatMap((article) => validateLawArticle(article, articles))
}

export function validateLawStructure(
  structure: LawStructureInput[],
  articleKeys: string[],
): string[] {
  const issues: string[] = []
  const nodeIds = new Set<string>()
  for (const node of structure) {
    if (!node.nodeId?.trim()) {
      issues.push('结构技术标识不能为空')
    } else if (nodeIds.has(node.nodeId)) {
      issues.push(`结构技术标识不能重复：${node.nodeId}`)
    } else {
      nodeIds.add(node.nodeId)
    }
  }

  const articleKeySet = new Set(articleKeys)
  const parentByNodeId = new Map<string, string>()
  const placedArticleRefs = new Set<string>()
  for (const node of structure) {
    const title = node.title ?? ''
    if (CONTROL_CHARACTER_PATTERN.test(title)) {
      issues.push(`结构“${title || node.nodeId}”的标题不得包含控制字符`)
    } else if (codePointLength(title.trim()) < 1 || codePointLength(title.trim()) > 100) {
      issues.push('结构标题须为1至100个字符')
    }
    if (!Number.isInteger(node.order) || node.order < 0) {
      issues.push('结构顺序必须是非负整数')
    }
    if (node.parentNodeId && (!nodeIds.has(node.parentNodeId) || node.parentNodeId === node.nodeId)) {
      issues.push(`结构“${title || node.nodeId}”的上级结构无效`)
    }
    if (node.parentNodeId) parentByNodeId.set(node.nodeId, node.parentNodeId)
    if (node.articleRefs.some((articleRef) => !articleKeySet.has(articleRef))) {
      issues.push(`结构“${title || node.nodeId}”包含无效法条引用`)
    }
    for (const articleRef of node.articleRefs) {
      if (placedArticleRefs.has(articleRef)) issues.push('同一法条不能挂载到多个结构节点')
      placedArticleRefs.add(articleRef)
    }
  }
  for (const node of structure) {
    const path = new Set<string>()
    let currentNodeId: string | undefined = node.nodeId
    while (currentNodeId) {
      if (path.has(currentNodeId)) {
        issues.push('结构节点不能形成循环')
        break
      }
      path.add(currentNodeId)
      currentNodeId = parentByNodeId.get(currentNodeId)
    }
  }
  return issues
}

export function validateLawImportPreview(value: LawImportPreview): string[] {
  const issues = [
    ...validateLawBaseInfo(value.baseInfo),
    ...validateLawArticles(value.articles),
  ]
  const articleKeys = new Set<string>()
  for (const article of value.articles) {
    if (!article.clientKey?.trim()) {
      issues.push('法条技术标识不能为空')
    } else if (articleKeys.has(article.clientKey)) {
      issues.push(`法条技术标识不能重复：${article.clientKey}`)
    } else {
      articleKeys.add(article.clientKey)
    }
  }
  issues.push(...validateLawStructure(value.structure, [...articleKeys]))
  return [...new Set(issues)]
}
