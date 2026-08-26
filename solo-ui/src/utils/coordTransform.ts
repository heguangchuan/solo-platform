/**
 * 坐标系转换（WGS-84 / GCJ-02 / BD-09）
 *
 * <p>跟后端 {@code com.solo.common.utils.CoordinateUtil} 同算法，前后端用一份真理。
 * 用在 picker / dashboard 等需要在前端做转换的地方——后端 DB 存 WGS-84，picker 选点
 * 时的坐标系由 map.coord-system 决定（高德 GCJ-02 / 百度 BD-09 / 天地图 WGS-84），
 * 提交前要把对应系转 WGS-84 再发给后端；编辑时后端返回 WGS-84，picker 要在瓦片系上
 * 展示，需要反过来转。</p>
 *
 * <p>算法来源：https://github.com/wandergis/coordtransform</p>
 */

const PI = Math.PI
const X_PI = (PI * 3000.0) / 180.0
const A = 6378245.0
const EE = 0.00669342162296594323

/** 中国大陆粗略矩形框，境外坐标不做偏移 */
function outOfChina(lng: number, lat: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

function transformLat(x: number, y: number): number {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320.0 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

/** WGS-84 → GCJ-02 */
export function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  return [lng + dLng, lat + dLat]
}

/** GCJ-02 → WGS-84（迭代反算，精度 ~1m） */
export function gcj02ToWgs84(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  const approx = wgs84ToGcj02(lng, lat)
  const dLng = approx[0] - lng
  const dLat = approx[1] - lat
  return [lng - dLng, lat - dLat]
}

/** GCJ-02 → BD-09（无损） */
export function gcj02ToBd09(lng: number, lat: number): [number, number] {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI)
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI)
  return [z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006]
}

/** BD-09 → GCJ-02（无损） */
export function bd09ToGcj02(lng: number, lat: number): [number, number] {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return [z * Math.cos(theta), z * Math.sin(theta)]
}

/** BD-09 → WGS-84（组合两次） */
export function bd09ToWgs84(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  const gcj = bd09ToGcj02(lng, lat)
  return gcj02ToWgs84(gcj[0], gcj[1])
}

/** WGS-84 → BD-09（组合两次） */
export function wgs84ToBd09(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  const gcj = wgs84ToGcj02(lng, lat)
  return gcj02ToBd09(gcj[0], gcj[1])
}

/**
 * 按 target 调度：把 WGS-84 转成指定系。
 * - target = 'WGS84' / 其他 → 原值返回
 * - target = 'GCJ02' → wgs84ToGcj02
 * - target = 'BD09'  → wgs84ToBd09
 */
export function wgs84ToTarget(lng: number, lat: number, target: string): [number, number] {
  switch ((target || 'WGS84').toUpperCase()) {
    case 'BD09':
      return wgs84ToBd09(lng, lat)
    case 'GCJ02':
      return wgs84ToGcj02(lng, lat)
    default:
      return [lng, lat]
  }
}

/**
 * 按 source 调度：把源系转成 WGS-84。
 * - source = 'WGS84' / 其他 → 原值返回
 * - source = 'GCJ02' → gcj02ToWgs84
 * - source = 'BD09'  → bd09ToWgs84
 */
export function sourceToWgs84(lng: number, lat: number, source: string): [number, number] {
  switch ((source || 'WGS84').toUpperCase()) {
    case 'BD09':
      return bd09ToWgs84(lng, lat)
    case 'GCJ02':
      return gcj02ToWgs84(lng, lat)
    default:
      return [lng, lat]
  }
}

