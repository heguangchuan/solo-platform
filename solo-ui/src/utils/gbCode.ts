/**
 * GB/T 28181-2016 附录 D 20 位国标编码工具（前端）。
 *
 * 结构：{@code [10位 domain][3位 类型码][7位 序号]}。前 10 位为本平台的行政区划中心编码，
 * 即 {@code gb28181.domain}，由后端 {@code /api/gb28181/code/platform-info} 或
 * {@code /next-*} 接口返回；前端不再从"某个设备的 deviceId 上截 10 位"，避免在
 * 级联来源设备下产生错前缀。
 *
 * 后端在 {@code com.solo.gb28181.utils.GbCodeGenerator} 有同一套规则，两侧对齐。
 */

/** 设备类型码常用选项（GB/T 28181 附录 D 表 D.2 常见值） */
export const DEVICE_TYPE_OPTIONS = [
  { label: '网络视频编码设备 132 (IPC)', value: '132' },
  { label: '视频服务器 118 (NVR/DVR)', value: '118' },
  { label: '网络视频服务器 200', value: '200' },
  { label: '解码器 138', value: '138' },
  { label: '编码器 137', value: '137' },
  { label: '报警设备 133', value: '133' },
  { label: '语音输入设备 134', value: '134' },
  { label: '语音输出设备 135', value: '135' },
]

/** 通道类型码常用选项 */
export const CHANNEL_TYPE_OPTIONS = [
  { label: '摄像机 131', value: '131' },
  { label: 'IPC/球机 132', value: '132' },
  { label: '编码器 137', value: '137' },
]

/** 分组类型码：215 业务分组 / 216 虚拟组织 */
export const GROUP_TYPE_OPTIONS = [
  { label: '业务分组 215', value: '215' },
  { label: '虚拟组织 216', value: '216' },
]

/** 默认类型码 */
export const DEFAULT_DEVICE_TYPE = '132'
export const DEFAULT_CHANNEL_TYPE = '131'
export const DEFAULT_GROUP_TYPE = '215'

/** 7 位序号左补零 */
function pad7(seq: number): string {
  return String(seq).padStart(7, '0')
}

/**
 * 拼装 20 位国标编码。domain10 不是 10 位数字则返回空串（由调用方决定留空让用户手输）。
 */
export function buildGbCode(domain10: string, typeCode: string, seq: number): string {
  if (!/^\d{10}$/.test(domain10)) return ''
  if (!/^\d{3}$/.test(typeCode)) return ''
  return domain10 + typeCode + pad7(seq)
}

/**
 * 从一组已有编码里，取以 prefix13（10 位 domain + 3 位类型码）为前缀的最大末 7 位序号 +1。
 * 无匹配则返回 1。
 */
export function nextSeqFromCodes(codes: string[], prefix13: string): number {
  let max = 0
  for (const code of codes) {
    if (!code || code.length !== 20 || !code.startsWith(prefix13)) continue
    const seq = Number.parseInt(code.slice(13), 10)
    if (!Number.isNaN(seq) && seq > max) max = seq
  }
  return max + 1
}

/**
 * 便捷方法：给定 domain 前 10 位、类型码、一组已存在编码，直接算出下一个可用编码。
 * 用于"设备新增向导第 2 步"这类设备尚未入库、需要本地递增的场景。
 * 参数非法时返回空串。
 */
export function nextGbCodeLocal(domain10: string, typeCode: string, existingCodes: string[]): string {
  if (!/^\d{10}$/.test(domain10) || !/^\d{3}$/.test(typeCode)) return ''
  const prefix13 = domain10 + typeCode
  return buildGbCode(domain10, typeCode, nextSeqFromCodes(existingCodes, prefix13))
}
