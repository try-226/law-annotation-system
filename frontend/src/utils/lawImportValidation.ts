import type {
  LawBaseInfo,
  LawImportConfirmPayload,
  LawImportPreview,
  LawImportPreviewBaseInfo,
  LawValidationIssue,
  ValidityStatus,
} from '../types/law'

const ARTICLE_NUMBER_PATTERN = /^第(?:[零〇一二三四五六七八九十百千万两]+|[1-9]\d*)条$/u
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001F\u007F-\u009F]/u
const VALIDITY_STATUSES: ReadonlySet<ValidityStatus> = new Set([
  'ACTIVE',
  'NOT_EFFECTIVE',
  'INVALID',
  'REPEALED',
])
const RECOMPUTABLE_PARSE_CODES = new Set([
  'IMPORT.MISSING_NAME',
  'IMPORT.MISSING_ISSUING_AUTHORITY',
  'IMPORT.MISSING_PUBLICATION_DATE',
  'IMPORT.INVALID_STRUCTURE_TITLE',
  'IMPORT.INVALID_ARTICLE_NUMBER',
  'IMPORT.DUPLICATE_ARTICLE_NUMBER',
  'IMPORT.INVALID_ARTICLE_BODY',
])

export function currentImportValidationIssues(preview: LawImportPreview): LawValidationIssue[] {
  const issues: LawValidationIssue[] = []
  validateSimpleText(preview.baseInfo.name, 'baseInfo.name', '法律名称', issues)
  validateSimpleText(
    preview.baseInfo.issuingAuthority,
    'baseInfo.issuingAuthority',
    '制定机关',
    issues,
  )
  if (!isIsoDate(preview.baseInfo.publicationDate)) {
    issues.push(issue('FRONTEND.MISSING_PUBLICATION_DATE', 'baseInfo.publicationDate', '公布日期不能为空'))
  }
  if (!preview.baseInfo.validityStatus || !VALIDITY_STATUSES.has(preview.baseInfo.validityStatus)) {
    issues.push(issue('FRONTEND.MISSING_VALIDITY_STATUS', 'baseInfo.validityStatus', '效力状态不能为空'))
  }

  preview.structure.forEach((node, index) => {
    validateSimpleText(node.title, `structure[${index}].title`, '结构节点标题', issues)
    validateOrder(node.order, `structure[${index}].order`, issues)
  })

  if (preview.articles.length === 0) {
    issues.push(issue('FRONTEND.ARTICLES_REQUIRED', 'articles', '首次创建法律至少需要一条法条'))
  }
  const seenNumbers = new Set<string>()
  preview.articles.forEach((article, index) => {
    const numberPath = `articles[${index}].number`
    const numberLength = codePointLength(article.number)
    if (numberLength < 1 || numberLength > 20 || !ARTICLE_NUMBER_PATTERN.test(article.number)) {
      issues.push(articleIssue(
        'FRONTEND.INVALID_ARTICLE_NUMBER',
        numberPath,
        index,
        article.number,
        '条号格式不合法',
      ))
    }
    if (seenNumbers.has(article.number)) {
      issues.push(articleIssue(
        'FRONTEND.DUPLICATE_ARTICLE_NUMBER',
        numberPath,
        index,
        article.number,
        '同一法律内容版本内条号不能重复',
      ))
    }
    seenNumbers.add(article.number)

    const normalizedBody = normalizeArticleBody(article.body)
    const bodyLength = codePointLength(normalizedBody)
    if (bodyLength < 1 || bodyLength > 20_000 || normalizedBody.trim().length === 0) {
      issues.push(articleIssue(
        'FRONTEND.INVALID_ARTICLE_BODY',
        `articles[${index}].body`,
        index,
        article.number,
        '条文正文须为1至20000个字符',
      ))
    }
    validateOrder(article.order, `articles[${index}].order`, issues, index, article.number)
  })
  return issues
}

export function isRecomputableParseIssue(issue: LawValidationIssue): boolean {
  return RECOMPUTABLE_PARSE_CODES.has(issue.code)
}

export function buildLawImportConfirmPayload(
  preview: LawImportPreview,
  currentIssues: LawValidationIssue[],
): LawImportConfirmPayload | null {
  if (currentIssues.length > 0 || !isCompleteBaseInfo(preview.baseInfo)) return null
  return {
    baseInfo: {
      name: preview.baseInfo.name,
      issuingAuthority: preview.baseInfo.issuingAuthority,
      publicationDate: preview.baseInfo.publicationDate,
      validityStatus: preview.baseInfo.validityStatus,
    },
    structure: preview.structure.map((node) => ({
      nodeId: node.nodeId,
      type: node.type,
      title: node.title,
      parentNodeId: node.parentNodeId,
      order: node.order,
      articleRefs: [...node.articleRefs],
    })),
    articles: preview.articles.map((article) => ({ ...article })),
  }
}

function isCompleteBaseInfo(value: LawImportPreviewBaseInfo): value is LawBaseInfo {
  return typeof value.name === 'string'
    && typeof value.issuingAuthority === 'string'
    && typeof value.publicationDate === 'string'
    && value.validityStatus !== null
    && VALIDITY_STATUSES.has(value.validityStatus)
}

function validateSimpleText(
  value: string | null,
  path: string,
  label: string,
  issues: LawValidationIssue[],
): void {
  if (value === null || CONTROL_CHARACTER_PATTERN.test(value)) {
    issues.push(issue(`FRONTEND.INVALID_${path.toUpperCase()}`, path, `${label}不得为空或包含控制字符`))
    return
  }
  const length = codePointLength(javaTrim(value))
  if (length < 1 || length > 100) {
    issues.push(issue(`FRONTEND.INVALID_${path.toUpperCase()}`, path, `${label}须为1至100个字符`))
  }
}

function validateOrder(
  value: number,
  path: string,
  issues: LawValidationIssue[],
  articleIndex: number | null = null,
  articleNumber: string | null = null,
): void {
  if (!Number.isInteger(value) || value < 0 || value > 2_147_483_647) {
    issues.push({
      ...issue('FRONTEND.INVALID_ORDER', path, '顺序必须是非负整数'),
      articleIndex,
      articleNumber,
    })
  }
}

function isIsoDate(value: string | null): boolean {
  if (!value) return false
  const match = /^(\d{4})-(\d{2})-(\d{2})$/u.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
}

function normalizeArticleBody(value: string): string {
  const normalizedLines = value.replace(/\r\n?/gu, '\n')
  return normalizedLines
    .replace(/^(?:[\t\f\v\p{Zs}]*\n)+/gu, '')
    .replace(/(?:\n[\t\f\v\p{Zs}]*)+$/gu, '')
}

function javaTrim(value: string): string {
  return value.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/gu, '')
}

function codePointLength(value: string): number {
  return Array.from(value).length
}

function issue(code: string, field: string, message: string): LawValidationIssue {
  return {
    code,
    field,
    articleIndex: null,
    articleNumber: null,
    structurePath: null,
    message,
  }
}

function articleIssue(
  code: string,
  field: string,
  articleIndex: number,
  articleNumber: string,
  message: string,
): LawValidationIssue {
  return {
    ...issue(code, field, message),
    articleIndex,
    articleNumber,
  }
}
