import type { LawImportPreview } from '../../types/law'

export function validateLawImportPreview(value: LawImportPreview): string[] {
  const issues: string[] = []
  const base = value.baseInfo
  if (!base.name?.trim() || !base.issuingAuthority?.trim() || !base.publicationDate || !base.validityStatus) {
    issues.push('请完整填写法律名称、发布机关、发布日期和效力状态')
  }
  if (value.articles.length === 0) issues.push('至少需要一条法条')
  const articleNumbers = new Set<string>()
  const articleOrders = new Set<number>()
  const articleKeys = new Set(value.articles.map((article) => article.clientKey))
  for (const article of value.articles) {
    const number = article.number.trim()
    if (!number || !article.body.trim()) issues.push('每条法条都需要填写条号和正文')
    if (number && articleNumbers.has(number)) issues.push(`条号不能重复：${number}`)
    articleNumbers.add(number)
    if (!Number.isInteger(article.order) || article.order < 0) {
      issues.push('法条顺序必须是非负整数')
    } else if (articleOrders.has(article.order)) {
      issues.push(`法条顺序不能重复：${article.order}`)
    } else {
      articleOrders.add(article.order)
    }
  }
  const nodeIds = new Set(value.structure.map((node) => node.nodeId))
  const parentByNodeId = new Map<string, string>()
  const placedArticleRefs = new Set<string>()
  for (const node of value.structure) {
    if (!node.title.trim()) issues.push('每个结构都需要填写标题')
    if (!Number.isInteger(node.order) || node.order < 0) issues.push('结构顺序必须是非负整数')
    if (node.parentNodeId && (!nodeIds.has(node.parentNodeId) || node.parentNodeId === node.nodeId)) {
      issues.push(`结构“${node.title || node.nodeId}”的上级结构无效`)
    }
    if (node.parentNodeId) parentByNodeId.set(node.nodeId, node.parentNodeId)
    if (node.articleRefs.some((articleRef) => !articleKeys.has(articleRef))) {
      issues.push(`结构“${node.title || node.nodeId}”包含无效法条引用`)
    }
    for (const articleRef of node.articleRefs) {
      if (placedArticleRefs.has(articleRef)) issues.push('同一法条不能挂载到多个结构节点')
      placedArticleRefs.add(articleRef)
    }
  }
  for (const node of value.structure) {
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
  return [...new Set(issues)]
}
