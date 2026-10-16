/** 文件大小 / 类型展示工具（列表与检视器共用） */

/** 按原始前端规则格式化：B 原样；KB/MB 保留 1 位；GB 及以上 2 位 */
export function formatSize(bytes: number): string {
  if (bytes == null || Number.isNaN(bytes)) return '—'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  const digits = i <= 1 ? 1 : 2
  return `${value.toFixed(digits)} ${units[i]}`
}

export type FileKind =
  | 'image'
  | 'video'
  | 'audio'
  | 'archive'
  | 'code'
  | 'doc'
  | 'file'

const ARCHIVE = new Set(['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'])
const CODE = new Set([
  'js', 'ts', 'jsx', 'tsx', 'vue', 'java', 'py', 'go', 'rs', 'c', 'cpp', 'h',
  'json', 'xml', 'yml', 'yaml', 'sh', 'bat', 'sql', 'html', 'css', 'md',
])
const DOC = new Set(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'csv'])

/** 按扩展名/MIME 粗分文件类别，用于图标与预览 */
export function fileKind(ext: string, mimeType?: string): FileKind {
  const e = (ext || '').toLowerCase()
  const mime = (mimeType || '').toLowerCase()
  if (mime.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico'].includes(e))
    return 'image'
  if (mime.startsWith('video/') || ['mp4', 'mov', 'avi', 'mkv', 'webm', 'flv'].includes(e))
    return 'video'
  if (mime.startsWith('audio/') || ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'].includes(e))
    return 'audio'
  if (ARCHIVE.has(e)) return 'archive'
  if (CODE.has(e)) return 'code'
  if (DOC.has(e)) return 'doc'
  return 'file'
}
