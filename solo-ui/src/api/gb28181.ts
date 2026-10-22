import { get, post, put, del } from '@/utils/request'
import type {
  GbDevice,
  GbDevicePageRequest,
  GbDeviceSaveRequest,
  GbDeviceDetectResult,
  GbDeviceChannel,
  GbDeviceChannelPageRequest,
  GbDeviceStatusCount,
  GbChannelStatusCount,
  GbPtzPreset,
  GbDeviceAlarm,
  GbDeviceAlarmPageRequest,
  GbDeviceAlarmHandleRequest,
  GbMobilePosition,
  GbMobilePositionPageRequest,
  GbPtzCruise,
  GbPtzCruiseDetail,
  GbPtzCruisePoint,
  GbPtzCruiseSaveRequest,
  GbSyncStatus,
  GbSyncTriggerResponse,
  GbPlayStartRequest,
  GbPlayStopRequest,
  GbPlayResult,
  GbPtzCommand,
  GbPtzFocusDirection,
  GbPtzIrisDirection,
  GbRecordItem,
  GbRecordQueryRequest,
  GbPlaybackControlRequest,
  GbPlaybackResult,
  GbDownloadStartRequest,
  GbDownloadResult,
  GbLocalPlatformInfo,
  GbParentPlatform,
  GbParentPlatformPageRequest,
  GbPlatformGroup,
  GbPlatformGroupSaveRequest,
  GbShareChannelPageRequest,
  GbAddShareRequest,
  GbSharedChannel,
  GbPlatform,
  GbPlatformPageRequest,
  GbRecordFile,
  GbRecordPlan,
  PageResult,
} from '@/types/api'

/* ===== 设备管理 ===== */

/** 设备分页 */
export const getGbDevicePageApi = (data: GbDevicePageRequest) =>
  post<PageResult<GbDevice>>('/api/gb28181/device/page', data)

/** 按关键词搜索设备（服务端匹配名称/编码，支持分页） */
export const searchGbDeviceApi = (params: {
  keyword?: string
  page?: number
  pageSize?: number
}) => get<PageResult<GbDevice>>('/api/gb28181/device/search', { params })

/** 设备状态统计（全局口径） */
export const getGbDeviceStatusCountApi = () =>
  get<GbDeviceStatusCount[]>('/api/gb28181/device/status-count')

/** 设备详情 */
export const getGbDeviceApi = (deviceId: string) =>
  get<GbDevice>(`/api/gb28181/device/${deviceId}`)

/** 新增设备（设备信息 + 通道列表） */
export const saveGbDeviceApi = (data: GbDeviceSaveRequest) =>
  post<void>('/api/gb28181/device/save', data)

/** 编辑设备（仅设备基本信息） */
export const updateGbDeviceApi = (data: Partial<GbDevice>) =>
  post<void>('/api/gb28181/device/update', data)

/** 删除设备记录（不影响实际设备） */
export const deleteGbDeviceApi = (deviceId: string) =>
  del<void>(`/api/gb28181/device/${deviceId}`)

/** 批量删除设备（雪花 ID 保持字符串传递，避免精度丢失） */
export const batchDeleteGbDeviceApi = (ids: string[]) =>
  post<void>('/api/gb28181/device/batch/delete', { ids })

/** 批量探测设备存活（后端异步探测，发完即返回） */
export const detectGbDeviceApi = (ids: string[]) =>
  post<GbDeviceDetectResult>('/api/gb28181/device/detect', { ids })

/** 触发设备信息 + 通道目录同步（去重：已在跑时返回 ALREADY_RUNNING） */
export const syncGbDeviceApi = (deviceId: string) =>
  post<GbSyncTriggerResponse>(`/api/gb28181/device/${deviceId}/sync`)

/**
 * 拉取设备同步进度。
 * 后端返回的是 status JSON 字符串（无状态时可能为空），由调用方用 parseSyncStatus 容错解析。
 */
export const getDeviceSyncStatusApi = (deviceId: string) =>
  get<string | GbSyncStatus>(`/api/gb28181/device/${deviceId}/sync-status`)

/** 把后端返回的 status（JSON 字符串或对象）解析成 GbSyncStatus，解析失败返回 IDLE */
export const parseSyncStatus = (
  raw: string | GbSyncStatus | null | undefined,
  deviceId: string,
): GbSyncStatus => {
  const idle: GbSyncStatus = {
    deviceId,
    status: 'IDLE',
    received: 0,
    total: 0,
    percent: 0,
    startedAtMs: 0,
    lastUpdateAtMs: 0,
    duplicateCount: 0,
  }
  if (!raw) return idle
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw) as GbSyncStatus
  } catch {
    return idle
  }
}

/* ===== 设备通道 ===== */

/** 通道分页 */
export const getGbDeviceChannelPageApi = (data: GbDeviceChannelPageRequest) =>
  post<PageResult<GbDeviceChannel>>('/api/gb28181/device-channel/page', data)

/** 通道状态统计（全局口径，按 parental 区分通道/分组） */
export const getGbChannelStatusCountApi = () =>
  get<GbChannelStatusCount[]>('/api/gb28181/device-channel/status-count')

/** 手动新增通道（状态/来源由后端设定） */
export const saveGbDeviceChannelApi = (data: Partial<GbDeviceChannel>) =>
  post<void>('/api/gb28181/device-channel/save', data)

/** 手动编辑通道资料 */
export const updateGbDeviceChannelApi = (data: Partial<GbDeviceChannel>) =>
  post<void>('/api/gb28181/device-channel/update', data)

/** 删除通道 */
export const deleteGbDeviceChannelApi = (id: string) =>
  del<void>(`/api/gb28181/device-channel/${id}`)

/** 批量删除通道（雪花 ID 保持字符串传递） */
export const batchDeleteGbDeviceChannelApi = (ids: string[]) =>
  post<void>('/api/gb28181/device-channel/batch/delete', { ids })

/* ===== 点播 ===== */

/** 开始点播（后端完成 SIP INVITE 后返回播放地址） */
export const startGbPlayApi = (data: GbPlayStartRequest, signal?: AbortSignal) =>
  post<GbPlayResult>('/api/gb28181/play/start', data, { signal })

/** 停止点播（SIP BYE + 关闭收流端口） */
export const stopGbPlayApi = (data: GbPlayStopRequest) =>
  post<void>('/api/gb28181/play/stop', data)

/* ===== 报警中心 ===== */

/** 报警分页查询 */
export const getGbAlarmPageApi = (data: GbDeviceAlarmPageRequest) =>
  post<PageResult<GbDeviceAlarm>>('/api/gb28181/alarm/page', data)

/** 报警详情 */
export const getGbAlarmDetailApi = (id: string) =>
  get<GbDeviceAlarm>(`/api/gb28181/alarm/detail/${id}`)

/** 报警处置（已确认/已处置/误报） */
export const handleGbAlarmApi = (data: GbDeviceAlarmHandleRequest) =>
  post<void>('/api/gb28181/alarm/handle', data)

/** 删除报警记录 */
export const deleteGbAlarmApi = (id: string) =>
  del<void>(`/api/gb28181/alarm/delete/${id}`)

/* ===== 移动轨迹 ===== */

/** 移动位置分页查询 */
export const getGbPositionPageApi = (data: GbMobilePositionPageRequest) =>
  post<PageResult<GbMobilePosition>>('/api/gb28181/position/page', data)

/** 按设备 + 时间段查询历史轨迹（时间升序） */
export const getGbPositionTrackApi = (data: GbMobilePositionPageRequest) =>
  post<GbMobilePosition[]>('/api/gb28181/position/track', data)

/** 删除位置点 */
export const deleteGbPositionApi = (id: string) =>
  del<void>(`/api/gb28181/position/delete/${id}`)

/* ===== 云台 ===== */

/** 云台方向/变倍控制（按下时持续调用，松开发 stop） */
export const ptzControlApi = (
  deviceId: string,
  channelId: string,
  command: GbPtzCommand,
  speed = 100,
  zoomSpeed = 5,
) =>
  post<void>(
    `/api/gb28181/ptz/control/${deviceId}/${channelId}?command=${command}&speed=${speed}&zoomSpeed=${zoomSpeed}`,
  )

/** 聚焦控制：near 近 / far 远 / stop 停 */
export const ptzFocusApi = (
  deviceId: string,
  channelId: string,
  direction: GbPtzFocusDirection,
  speed = 5,
) =>
  post<void>(
    `/api/gb28181/ptz/focus/${deviceId}/${channelId}?direction=${direction}&speed=${speed}`,
  )

/** 光圈控制：open 开 / close 关 / stop 停 */
export const ptzIrisApi = (
  deviceId: string,
  channelId: string,
  direction: GbPtzIrisDirection,
  speed = 5,
) =>
  post<void>(
    `/api/gb28181/ptz/iris/${deviceId}/${channelId}?direction=${direction}&speed=${speed}`,
  )

/** 雨刷控制 */
export const ptzWiperApi = (deviceId: string, channelId: string, action: 'on' | 'off') =>
  post<void>(`/api/gb28181/ptz/wiper/${deviceId}/${channelId}?action=${action}`)

/* ----------------------- 云台预置位 ----------------------- */

/** 按通道主键列出预置位 */
export const listPtzPresetApi = (channelDbId: string) =>
  get<GbPtzPreset[]>(`/api/gb28181/ptz/preset/list/${channelDbId}`)

/** 将当前视角设置到指定编号预置位（名称由后端生成） */
export const savePtzPresetApi = (data: { channelDbId: string; presetId: number }) =>
  post<void>('/api/gb28181/ptz/preset/save', data)

/** 删除预置位 */
export const deletePtzPresetApi = (id: string) =>
  del<void>(`/api/gb28181/ptz/preset/delete/${id}`)

/** 调用预置位（云台转到该视角） */
export const callPtzPresetApi = (id: string) =>
  post<void>(`/api/gb28181/ptz/preset/call/${id}`)

/** 从设备同步预置位（以设备端为准全量覆盖） */
export const syncPtzPresetApi = (channelDbId: string) =>
  post<void>(`/api/gb28181/ptz/preset/sync/${channelDbId}`)

/* ----------------------- 云台巡航 ----------------------- */

/** 按通道主键列出巡航路线 */
export const listPtzCruiseApi = (channelDbId: string) =>
  get<GbPtzCruise[]>(`/api/gb28181/ptz/cruise/list/${channelDbId}`)

/** 巡航路线详情（含巡航点） */
export const getPtzCruiseApi = (id: string) =>
  get<GbPtzCruiseDetail>(`/api/gb28181/ptz/cruise/${id}`)

/** 新建巡航路线（可一次性带巡航点） */
export const savePtzCruiseApi = (data: GbPtzCruiseSaveRequest) =>
  post<void>('/api/gb28181/ptz/cruise/save', data)

/** 编辑巡航路线基本信息（速度/停留变化同步设备） */
export const updatePtzCruiseApi = (data: Partial<GbPtzCruise>) =>
  post<void>('/api/gb28181/ptz/cruise/update', data)

/** 删除巡航路线 */
export const deletePtzCruiseApi = (id: string) =>
  del<void>(`/api/gb28181/ptz/cruise/delete/${id}`)

/** 添加巡航点 */
export const addPtzCruisePointApi = (id: string, data: Partial<GbPtzCruisePoint>) =>
  post<void>(`/api/gb28181/ptz/cruise/${id}/add-point`, data)

/** 移除巡航点（按预置位编号） */
export const removePtzCruisePointApi = (id: string, presetId: number) =>
  del<void>(`/api/gb28181/ptz/cruise/${id}/remove-point/${presetId}`)

/** 开始巡航 */
export const startPtzCruiseApi = (id: string) =>
  post<void>(`/api/gb28181/ptz/cruise/start/${id}`)

/** 停止巡航 */
export const stopPtzCruiseApi = (id: string) =>
  post<void>(`/api/gb28181/ptz/cruise/stop/${id}`)

/* ----------------------- 录像回放 ----------------------- */

/** 录像查询（RecordInfo，设备分片返回，后端合并后一次性返回） */
export const queryRecordInfoApi = (data: GbRecordQueryRequest) =>
  post<GbRecordItem[]>('/api/gb28181/playback/query', data)

/** 开始回放（同步返回流就绪的 URL 集合 + 时段元数据） */
export const playbackStartApi = (data: {
  deviceId: string
  channelId: string
  startTime: string
  endTime: string
  transport?: 'UDP' | 'TCP-passive' | 'TCP-active'
  streamNumber?: string
}) => post<GbPlaybackResult>('/api/gb28181/playback/start', data)

/** 停止回放 */
export const playbackStopApi = (data: { streamId: string }) =>
  post<void>('/api/gb28181/playback/stop', data)

/** 回放控制：pause / resume / seek / speed */
export const playbackControlApi = (data: GbPlaybackControlRequest) =>
  post<void>('/api/gb28181/playback/control', data)

/** 发起录像下载（异步，立即返 streamId + status） */
export const startDownloadApi = (data: GbDownloadStartRequest) =>
  post<GbDownloadResult>('/api/gb28181/download/start', data)

/** 查录像下载任务状态（手动刷新用，不轮询） */
export const getDownloadStatusApi = (streamId: string) =>
  get<GbDownloadResult>(`/api/gb28181/download/${streamId}`)

/** 主动停止录像下载 */
export const stopDownloadApi = (streamId: string) =>
  post<void>(`/api/gb28181/download/${streamId}/stop`)

/* ===== 级联平台：上级平台 ===== */

/** 上级平台分页 */
export const getGbParentPlatformPageApi = (data: GbParentPlatformPageRequest) =>
  post<PageResult<GbParentPlatform>>('/api/gb28181/parent-platform/page', data)

/** 新增上级平台 */
export const saveGbParentPlatformApi = (data: Partial<GbParentPlatform>) =>
  post<void>('/api/gb28181/parent-platform/save', data)

/** 修改上级平台 */
export const updateGbParentPlatformApi = (data: Partial<GbParentPlatform>) =>
  post<void>('/api/gb28181/parent-platform/update', data)

/** 删除上级平台 */
export const deleteGbParentPlatformApi = (id: string) =>
  del<void>(`/api/gb28181/parent-platform/${id}`)

/** 批量删除上级平台 */
export const batchDeleteGbParentPlatformApi = (ids: string[]) =>
  post<void>('/api/gb28181/parent-platform/batch/delete', { ids })

/** 本平台国标身份 */
export const getGbLocalPlatformInfoApi = () =>
  get<GbLocalPlatformInfo>('/api/gb28181/parent-platform/local-info')

/* ===== 级联平台：推送分组 ===== */

/** 推送目录分组树 */
export const getParentPlatformShareGroupTreeApi = (id: string) =>
  get<GbPlatformGroup[]>(`/api/gb28181/parent-platform/${id}/share-group-tree`)

/** 新增推送分组 */
export const savePlatformGroupApi = (platformId: string, data: GbPlatformGroupSaveRequest) =>
  post<void>(`/api/gb28181/parent-platform/${platformId}/share-group`, data)

/** 修改推送分组 */
export const updatePlatformGroupApi = (
  platformId: string,
  groupId: string,
  data: GbPlatformGroupSaveRequest,
) =>
  put<void>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}`,
    data,
  )

/** 删除推送分组 */
export const deletePlatformGroupApi = (platformId: string, groupId: string) =>
  del<void>(`/api/gb28181/parent-platform/${platformId}/share-group/${groupId}`)

/* ===== 级联平台：通道共享 ===== */

/** 分组下未挂载通道分页（添加 picker） */
export const getParentPlatformUnmountedChannelsApi = (
  platformId: string,
  groupId: string,
  data: GbShareChannelPageRequest,
) =>
  post<PageResult<GbDeviceChannel>>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/unmounted-channels/page`,
    data,
  )

/** 分组下已挂载通道分页 */
export const getParentPlatformSharedByGroupPageApi = (
  platformId: string,
  groupId: string,
  data: GbShareChannelPageRequest,
) =>
  post<PageResult<GbSharedChannel>>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/shared/page`,
    data,
  )

/** 批量挂载通道到分组 */
export const addSharesApi = (
  platformId: string,
  groupId: string,
  data: GbAddShareRequest,
) =>
  post<{ added: number }>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/share`,
    data,
  )

/** 卸载单条通道 */
export const removeShareApi = (
  platformId: string,
  groupId: string,
  resourceId: string,
) =>
  del<void>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/share/${resourceId}`,
  )

/** 清空分组下挂载 */
export const clearSharesApi = (platformId: string, groupId: string) =>
  del<{ cleared: number }>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/share`,
  )

/** 清空平台下全部挂载 */
export const clearAllSharesByPlatformApi = (platformId: string) =>
  del<{ cleared: number }>(`/api/gb28181/parent-platform/${platformId}/share`)

/** 主动推送目录给上级平台 */
export const pushCatalogApi = (platformId: string) =>
  post<void>(`/api/gb28181/parent-platform/${platformId}/push-catalog`)

/** 一键添加所有未挂载通道到分组 */
export const addAllSharesApi = (platformId: string, groupId: string) =>
  post<{ added: number }>(
    `/api/gb28181/parent-platform/${platformId}/share-group/${groupId}/share-all`,
  )

/* ===== 级联平台：接入禁用黑名单 ===== */

/** 黑名单分页 */
export const getGbPlatformPageApi = (data: GbPlatformPageRequest) =>
  post<PageResult<GbPlatform>>('/api/gb28181/platform/page', data)

/** 新增黑名单 */
export const saveGbPlatformApi = (data: Partial<GbPlatform>) =>
  post<void>('/api/gb28181/platform/save', data)

/** 修改黑名单 */
export const updateGbPlatformApi = (data: Partial<GbPlatform>) =>
  post<void>('/api/gb28181/platform/update', data)

/** 删除黑名单 */
export const deleteGbPlatformApi = (id: string) =>
  del<void>(`/api/gb28181/platform/${id}`)

/** 批量删除黑名单 */
export const batchDeleteGbPlatformApi = (ids: string[]) =>
  post<void>('/api/gb28181/platform/batch/delete', { ids })

/* ===== 云端录像中心 ===== */

/** 录像文件分页（后端分页字段是 size，由调用方把 pageSize 映射过来） */
export const getCloudRecordPageApi = (params: {
  deviceId?: string
  channelId?: string
  start?: string
  end?: string
  page?: number
  size?: number
}) => get<PageResult<GbRecordFile>>('/api/gb28181/cloud-record/page', { params })

/** 取录像播放/下载预签名地址（1 小时有效） */
export const getCloudRecordUrlApi = (id: string) =>
  get<string>(`/api/gb28181/cloud-record/${id}/url`)

/** 手动开始录制，返回 streamId */
export const startCloudRecordApi = (data: {
  deviceId: string
  channelId: string
  transport?: string
  streamNumber?: string
}) => {
  const query = new URLSearchParams()
  query.set('deviceId', data.deviceId)
  query.set('channelId', data.channelId)
  if (data.transport) query.set('transport', data.transport)
  if (data.streamNumber) query.set('streamNumber', data.streamNumber)
  return post<string>(`/api/gb28181/cloud-record/start?${query.toString()}`)
}

/** 手动停止录制 */
export const stopCloudRecordApi = (streamId: string) =>
  post<void>(`/api/gb28181/cloud-record/stop?streamId=${encodeURIComponent(streamId)}`)

/** 删除录像文件（同时删 S3 对象） */
export const deleteCloudRecordApi = (id: string) =>
  del<void>(`/api/gb28181/cloud-record/${id}`)

/* ===== 云端录像计划 ===== */

/** 录像计划列表（可按设备过滤，不分页） */
export const getRecordPlanListApi = (
  deviceId?: string,
  channelId?: string,
  enabled?: number,
) =>
  get<GbRecordPlan[]>('/api/gb28181/record-plan/list', {
    params: { deviceId, channelId, enabled },
  })

/** 录像计划详情（含周时段） */
export const getRecordPlanApi = (id: string) =>
  get<GbRecordPlan>(`/api/gb28181/record-plan/${id}`)

/** 新增录像计划 */
export const saveRecordPlanApi = (data: GbRecordPlan) =>
  post<void>('/api/gb28181/record-plan/save', data)

/** 编辑录像计划（周时段整体覆盖） */
export const updateRecordPlanApi = (data: GbRecordPlan) =>
  post<void>('/api/gb28181/record-plan/update', data)

/** 删除录像计划 */
export const deleteRecordPlanApi = (id: string) =>
  del<void>(`/api/gb28181/record-plan/delete/${id}`)

/** 启用/停用录像计划 */
export const toggleRecordPlanApi = (id: string, enabled: boolean) =>
  post<void>(
    `/api/gb28181/record-plan/toggle/${id}?enabled=${enabled ? 'true' : 'false'}`,
  )
