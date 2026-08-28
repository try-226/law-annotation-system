const NAME_PATTERN = /^[\p{L}\p{N}](?:[\p{L}\p{N} \-·]*[\p{L}\p{N}])?$/u
const ACCOUNT_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{3,31}$/
const PASSWORD_FORBIDDEN_PATTERN = /[\u0000-\u0020\u007f-\u009f\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/u

export function trimText(value: string): string {
  return value.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, '')
}

function characterLength(value: string): number {
  return [...value].length
}

export function normalizeSearchQuery(value: string): string {
  return trimText(value).replace(/[\r\n\t ]+/g, ' ')
}

export function validateName(value: string): string | null {
  const name = trimText(value)
  const length = characterLength(name)
  if (length < 1 || length > 50 || !NAME_PATTERN.test(name)) {
    return '姓名须为1至50个字符，仅允许字母、数字、空格和常用连接符，且首尾须为字母或数字'
  }
  return null
}

export function validateLoginAccount(value: string): string | null {
  if (!ACCOUNT_PATTERN.test(trimText(value))) {
    return '登录账号须为4至32位，并仅包含字母、数字、点、下划线或连字符'
  }
  return null
}

export function validatePassword(value: string): string | null {
  const length = characterLength(value)
  if (length < 6 || length > 64 || PASSWORD_FORBIDDEN_PATTERN.test(value)) {
    return '密码须为6至64个字符，且不得包含空白或控制字符'
  }
  return null
}

export function validatePasswordConfirmation(password: string, confirmation: string): string | null {
  return password === confirmation ? null : '两次输入的密码不一致'
}

export function validateSearch(value: string): string | null {
  return characterLength(trimText(value)) > 100 ? '搜索关键词不能超过100个字符' : null
}

export function validateRequiredSearch(value: string): string | null {
  const query = normalizeSearchQuery(value)
  if (!query) return '请输入搜索关键词'
  return characterLength(query) > 100 ? '搜索关键词不能超过100个字符' : null
}
