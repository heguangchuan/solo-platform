/**
 * 从 User-Agent 解析浏览器与操作系统（仅用于展示）。
 * 后端只存储原始 UA，不提供浏览器/系统字段。
 */
export interface UaMeta {
  browser: string
  os: string
  /** 紧凑描述，如 “Chrome 120 · Win 10” */
  short: string
}

const detectBrowser = (ua: string): string => {
  let m: RegExpMatchArray | null
  if ((m = ua.match(/Edg(?:e|A|iOS)?\/([\d.]+)/))) return `Edge ${m[1].split('.')[0]}`
  if ((m = ua.match(/OPR\/([\d.]+)/))) return `Opera ${m[1].split('.')[0]}`
  if ((m = ua.match(/Chrome\/([\d.]+)/)) && !/Chromium/.test(ua)) {
    return `Chrome ${m[1].split('.')[0]}`
  }
  if ((m = ua.match(/Firefox\/([\d.]+)/))) return `Firefox ${m[1].split('.')[0]}`
  if ((m = ua.match(/Version\/([\d.]+).*Safari/))) return `Safari ${m[1].split('.')[0]}`
  if (/Safari/.test(ua)) return 'Safari'
  if (/MicroMessenger/.test(ua)) {
    const v = ua.match(/MicroMessenger\/([\d.]+)/)
    return `微信 ${v?.[1].split('.')[0] ?? ''}`.trim()
  }
  return '未知浏览器'
}

const detectOs = (ua: string): string => {
  let m: RegExpMatchArray | null
  if ((m = ua.match(/Windows NT ([\d.]+)/))) {
    const map: Record<string, string> = { '10.0': 'Win 10/11', '6.3': 'Win 8.1', '6.2': 'Win 8', '6.1': 'Win 7' }
    return map[m[1]] ?? `Win ${m[1]}`
  }
  if ((m = ua.match(/Mac OS X ([\d_]+)/))) return `macOS ${m[1].replace(/_/g, '.')}`
  if (/Android/.test(ua)) {
    const v = ua.match(/Android ([\d.]+)/)
    return v ? `Android ${v[1]}` : 'Android'
  }
  if ((m = ua.match(/iPhone OS ([\d_]+)/))) return `iOS ${m[1].replace(/_/g, '.')}`
  if (/iPad/.test(ua)) return 'iPadOS'
  if (/Linux/.test(ua)) return 'Linux'
  return '未知系统'
}

export function parseUa(ua: string): UaMeta {
  const raw = ua || ''
  const browser = detectBrowser(raw)
  const os = detectOs(raw)
  return { browser, os, short: raw ? `${browser} · ${os}` : '—' }
}
