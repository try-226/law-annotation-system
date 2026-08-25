import type { LawArticle, LawBaseInfo, LawDetail, LawStructureInput } from '../../types/law'

export type SavedLawRegion =
  | { region: 'base' }
  | { region: 'structure' }
  | { region: 'article'; articleId: string }
  | { region: 'articles' }

export interface LawDetailDraftState {
  detail: LawDetail
  base: LawBaseInfo
  structures: LawStructureInput[]
  articles: LawArticle[]
  serverBase: LawBaseInfo
  serverStructures: LawStructureInput[]
  serverArticles: LawArticle[]
}

function baseFromDetail(value: LawDetail): LawBaseInfo {
  return {
    name: value.name,
    issuingAuthority: value.issuingAuthority,
    publicationDate: value.publicationDate,
    validityStatus: value.validityStatus,
  }
}

function structuresFromDetail(value: LawDetail): LawStructureInput[] {
  return value.structure.map((node) => ({
    nodeId: node.nodeId,
    type: node.type,
    title: node.title,
    parentNodeId: node.parentNodeId,
    order: node.order,
    articleRefs: [...node.articleIds],
  }))
}

function cloneArticles(articles: LawArticle[]): LawArticle[] {
  return articles.map((article) => ({ ...article }))
}

function cloneStructures(structures: LawStructureInput[]): LawStructureInput[] {
  return structures.map((node) => ({ ...node, articleRefs: [...node.articleRefs] }))
}

function cloneDetail(value: LawDetail): LawDetail {
  return {
    ...value,
    structure: value.structure.map((node) => ({ ...node, articleIds: [...node.articleIds] })),
    articles: cloneArticles(value.articles),
  }
}

function sameBase(left: LawBaseInfo, right: LawBaseInfo): boolean {
  return left.name === right.name
    && left.issuingAuthority === right.issuingAuthority
    && left.publicationDate === right.publicationDate
    && left.validityStatus === right.validityStatus
}

function sameStructure(left: LawStructureInput, right: LawStructureInput): boolean {
  return left.nodeId === right.nodeId
    && left.type === right.type
    && left.title === right.title
    && left.parentNodeId === right.parentNodeId
    && left.order === right.order
    && left.articleRefs.length === right.articleRefs.length
    && left.articleRefs.every((articleRef, index) => articleRef === right.articleRefs[index])
}

function sameStructures(left: LawStructureInput[], right: LawStructureInput[]): boolean {
  return left.length === right.length
    && left.every((node, index) => sameStructure(node, right[index]))
}

function mergeArticleRefs(
  localRefs: string[],
  previousServerRefs: string[],
  responseRefs: string[],
  validArticleIds: Set<string>,
): string[] {
  const previousServerSet = new Set(previousServerRefs)
  const responseSet = new Set(responseRefs)
  const orderedRefs = [...localRefs, ...responseRefs.filter((ref) => !localRefs.includes(ref))]

  return orderedRefs.filter((ref) => {
    if (!validArticleIds.has(ref)) return false
    const localHasRef = localRefs.includes(ref)
    const userChangedRef = localHasRef !== previousServerSet.has(ref)
    return userChangedRef ? localHasRef : responseSet.has(ref)
  })
}

function mergeStructureDraftAfterArticleChange(
  current: LawStructureInput[],
  previousServer: LawStructureInput[],
  response: LawStructureInput[],
  responseArticles: LawArticle[],
): LawStructureInput[] {
  const previousById = new Map(previousServer.map((node) => [node.nodeId, node]))
  const responseById = new Map(response.map((node) => [node.nodeId, node]))
  const currentIds = new Set(current.map((node) => node.nodeId))
  const validArticleIds = new Set(responseArticles.map((article) => article.articleId))
  const merged = current.map((localNode) => {
    const previousNode = previousById.get(localNode.nodeId)
    const responseNode = responseById.get(localNode.nodeId)
    return {
      ...localNode,
      articleRefs: mergeArticleRefs(
        localNode.articleRefs,
        previousNode?.articleRefs ?? [],
        responseNode?.articleRefs ?? [],
        validArticleIds,
      ),
    }
  })

  response.forEach((responseNode) => {
    if (!currentIds.has(responseNode.nodeId) && !previousById.has(responseNode.nodeId)) {
      merged.push({ ...responseNode, articleRefs: [...responseNode.articleRefs] })
    }
  })
  return merged
}

function sameArticle(left: LawArticle, right: LawArticle): boolean {
  return left.articleId === right.articleId
    && left.number === right.number
    && left.body === right.body
    && left.order === right.order
}

export function createLawDetailDraftState(value: LawDetail): LawDetailDraftState {
  const base = baseFromDetail(value)
  const structures = structuresFromDetail(value)
  const articles = cloneArticles(value.articles)
  return {
    detail: cloneDetail(value),
    base: { ...base },
    structures,
    articles,
    serverBase: { ...base },
    serverStructures: structuresFromDetail(value),
    serverArticles: cloneArticles(value.articles),
  }
}

export function mergeLawDetailDraftState(
  current: LawDetailDraftState,
  response: LawDetail,
  saved: SavedLawRegion,
): LawDetailDraftState {
  const responseBase = baseFromDetail(response)
  const responseStructures = structuresFromDetail(response)
  const currentArticles = new Map(current.articles.map((article) => [article.articleId, article]))
  const previousServerArticles = new Map(
    current.serverArticles.map((article) => [article.articleId, article]),
  )
  const articles = response.articles.map((serverArticle) => {
    if (saved.region === 'article' && saved.articleId === serverArticle.articleId) {
      return { ...serverArticle }
    }
    const localArticle = currentArticles.get(serverArticle.articleId)
    const previousServerArticle = previousServerArticles.get(serverArticle.articleId)
    return localArticle && previousServerArticle && !sameArticle(localArticle, previousServerArticle)
      ? { ...localArticle }
      : { ...serverArticle }
  })
  const keepBaseDraft = saved.region !== 'base' && !sameBase(current.base, current.serverBase)
  const keepStructureDraft = saved.region !== 'structure'
    && !sameStructures(current.structures, current.serverStructures)
  const structures = keepStructureDraft && saved.region === 'articles'
    ? mergeStructureDraftAfterArticleChange(
        current.structures,
        current.serverStructures,
        responseStructures,
        response.articles,
      )
    : keepStructureDraft
      ? cloneStructures(current.structures)
      : cloneStructures(responseStructures)

  return {
    detail: cloneDetail(response),
    base: keepBaseDraft ? { ...current.base } : { ...responseBase },
    structures,
    articles,
    serverBase: { ...responseBase },
    serverStructures: cloneStructures(responseStructures),
    serverArticles: cloneArticles(response.articles),
  }
}
