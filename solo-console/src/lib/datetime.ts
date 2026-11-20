/** 将后端 "yyyy-MM-dd HH:mm:ss" 安全解析为 Date（兼容无 T 分隔的格式） */
export const parseDateTime = (s: string): Date | null => {
  if (!s) return null
  const d = new Date(s.includes('T') ? s : s.replace(/-/g, '/'))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 相对时间：刚刚 / n 分钟前 / n 小时前 / n 天前 / 更早显示日期 */
export const fromNow = (s: string, now: number = Date.now()): string => {
  const d = parseDateTime(s)
  if (!d) return s
  const diff = Math.max(0, now - d.getTime())
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  return d.toLocaleDateString('zh-CN')
}
