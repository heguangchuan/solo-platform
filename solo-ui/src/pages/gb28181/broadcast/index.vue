<script setup lang="ts">
/**
 * 语音播报 / 语音对讲：网页麦克风实时喊话，单设备单通道，支持单向播报与双向对讲。
 *
 * 链路：getUserMedia 采集 → RTCPeerConnection（强制 PCMA/PCMU 优先）→ offer SDP 经后端
 * 代理给 ZLM /index/api/webrtc(type=push) → ZLM startSendRtp(use_ps) → GB 设备放音。
 *
 * 设备协商（Broadcast 通知 / 反向 INVITE / ACK）全在后端 start 接口里同步等完，
 * 前端拿到结果后只需把麦克风推上去，真正的音频腿就通了。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { Mic, MicOff, Square, Loader2, Megaphone, Phone } from 'lucide-vue-next'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import GbChannelSelect from '@/features/gb/GbChannelSelect.vue'
import Button from '@/components/ui/button/Button.vue'
import {
  startBroadcastApi,
  stopBroadcastApi,
  exchangeBroadcastSdpApi,
  exchangeTalkPlaySdpApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'

const deviceId = ref<string | undefined>(undefined)
const channelId = ref<string | undefined>(undefined)
/** broadcast=单向喊话，talk=双向对讲（多一条设备音频播放腿），由点击的图标按钮决定 */
const mode = ref<'broadcast' | 'talk'>('broadcast')

/** idle=未开始 starting=通知/协商中 live=播报/通话中 stopping=停止中 */
const phase = ref<'idle' | 'starting' | 'live' | 'stopping'>('idle')
/** WebRTC/ICE 层面的连接状态，仅用于展示 */
const connState = ref('')
/** 麦克风电平 0~1，驱动音量条 */
const level = ref(0)
/** 后端返回的流ID，stop / SDP 交换都用它 */
const streamId = ref('')
/** 对讲播放腿是否已听到设备音频 */
const listening = ref(false)

let pc: RTCPeerConnection | null = null
let playPc: RTCPeerConnection | null = null
let playAudio: HTMLAudioElement | null = null
let localStream: MediaStream | null = null
let audioCtx: AudioContext | null = null
let levelRaf = 0
let abortController: AbortController | null = null

const busy = computed(() => phase.value === 'starting' || phase.value === 'stopping')
const canStart = computed(() => !!deviceId.value && !!channelId.value && !busy.value)

/** 功能名按模式区分：语音播报（单向）/ 语音对讲（双向） */
const featureName = computed(() => (mode.value === 'talk' ? '语音对讲' : '语音播报'))
const liveText = computed(() => (mode.value === 'talk' ? '通话中' : '播报中'))

/**
 * 把 G.711（PCMA=8 / PCMU=0）排到协商列表最前。
 * GB 设备普遍只吃 G.711，浏览器默认给的是 Opus，ZLM 又不转码，不调整顺序设备收不到能解的音频。
 */
const preferG711 = (sender: RTCRtpSender) => {
  const caps = RTCRtpSender.getCapabilities?.('audio')
  if (!caps) return
  const ordered = [...caps.codecs].sort((a, b) => {
    const score = (m: string) => {
      if (m.includes('PCMA') || m.includes('pcma')) return 0
      if (m.includes('PCMU') || m.includes('pcmu')) return 1
      return 2
    }
    return score(a.mimeType) - score(b.mimeType)
  })
  try {
    // 部分版本的 TS lib 没收录该方法，运行时主流浏览器都支持
    ;(sender as RTCRtpSender & {
      // 旧版 TS DOM lib 没收录 setCodecPreferences / RTCRtpCodecCapability，这里宽松处理
      setCodecPreferences?: (codecs: { mimeType: string }[]) => void
    }).setCodecPreferences?.(ordered)
  } catch {
    // 个别浏览器不支持或对 SDP 修改敏感，失败就用默认协商
  }
}

/** 用 AnalyserNode 算个粗略音量，让音量条动起来 */
const startLevelMeter = () => {
  if (!localStream) return
  audioCtx = new AudioContext()
  const source = audioCtx.createMediaStreamSource(localStream)
  const analyser = audioCtx.createAnalyser()
  analyser.fftSize = 512
  source.connect(analyser)
  const buf = new Uint8Array(analyser.fftSize)
  const tick = () => {
    analyser.getByteTimeDomainData(buf)
    let peak = 0
    for (const v of buf) {
      const d = Math.abs(v - 128) / 128
      if (d > peak) peak = d
    }
    level.value = Math.min(1, peak * 1.6)
    levelRaf = requestAnimationFrame(tick)
  }
  tick()
}

const stopLevelMeter = () => {
  cancelAnimationFrame(levelRaf)
  levelRaf = 0
  level.value = 0
  audioCtx?.close().catch(() => {})
  audioCtx = null
}

/**
 * 对讲播放腿：只收不发，offer 里带 recvonly，经 talk-play 代理换 answer，
 * 设备回传音频到达 ZLM 后由该腿播放。后端会等回传流上线再应答。
 */
const startPlayLeg = async (id: string, controller: AbortController) => {
  const peer = new RTCPeerConnection()
  playPc = peer
  peer.addTransceiver('audio', { direction: 'recvonly' })
  const offer = await peer.createOffer()
  await peer.setLocalDescription(offer)
  const { data: answerSdp } = await exchangeTalkPlaySdpApi({ streamId: id, sdp: offer.sdp ?? '' })
  if (controller.signal.aborted || playPc !== peer) return
  await peer.setRemoteDescription({ type: 'answer', sdp: answerSdp })

  const remote = new MediaStream()
  peer.ontrack = (ev) => {
    ev.streams[0]?.getTracks().forEach((t) => remote.addTrack(t))
    if (!ev.streams[0]) remote.addTrack(ev.track)
    if (!playAudio) {
      playAudio = new Audio()
      playAudio.autoplay = true
      playAudio.srcObject = remote
    }
    playAudio.play().catch(() => {
      // 自动播放被拦时等用户在页面上任意交互后浏览器会放行
    })
    listening.value = true
  }
  peer.onconnectionstatechange = () => {
    if (peer.connectionState === 'failed') {
      listening.value = false
    }
  }
}

/** 关掉本地媒体与 PeerConnection（纯前端，不通知后端） */
const closePeer = () => {
  stopLevelMeter()
  localStream?.getTracks().forEach((t) => t.stop())
  localStream = null
  if (pc) {
    pc.onconnectionstatechange = null
    pc.oniceconnectionstatechange = null
    pc.close()
    pc = null
  }
  if (playPc) {
    playPc.ontrack = null
    playPc.onconnectionstatechange = null
    playPc.close()
    playPc = null
  }
  if (playAudio) {
    playAudio.pause()
    playAudio.srcObject = null
    playAudio = null
  }
  listening.value = false
  connState.value = ''
}

const start = async (m: 'broadcast' | 'talk') => {
  if (!deviceId.value || !channelId.value) {
    toast.warning('请先选择设备和通道')
    return
  }
  mode.value = m
  phase.value = 'starting'
  const controller = new AbortController()
  abortController = controller
  let media: MediaStream | null = null
  try {
    // 先拿麦克风，用户拒绝授权就别往下发广播通知了
    media = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true },
      video: false,
    })
    if (controller.signal.aborted) {
      media.getTracks().forEach((t) => t.stop())
      return
    }
    localStream = media

    // 后端：发 Broadcast 通知 → 等设备 200，设备反向 INVITE/ACK 由异步信令处理
    const { data } = await startBroadcastApi(
      { deviceId: deviceId.value, channelId: channelId.value, mode: mode.value },
      controller.signal,
    )
    if (controller.signal.aborted) return
    streamId.value = data.streamId

    // 建 WebRTC 推流腿
    const peer = new RTCPeerConnection()
    pc = peer
    peer.onconnectionstatechange = () => {
      connState.value = peer.connectionState
      if (peer.connectionState === 'failed' || peer.connectionState === 'closed') {
        toast.error('与流媒体节点的 WebRTC 连接断开')
      }
    }
    peer.oniceconnectionstatechange = () => {
      connState.value = peer.iceConnectionState
    }
    media.getAudioTracks().forEach((track) => peer.addTrack(track, media!))
    // addTransceiver 拿 sender 才能设编码偏好；addTrack 后从 transceivers 里取即可
    const audioSender = peer.getTransceivers().find((t) => t.mid === null || t.sender.track?.kind === 'audio')?.sender
      ?? peer.getSenders().find((s) => s.track?.kind === 'audio')
    if (audioSender) preferG711(audioSender)

    const offer = await peer.createOffer({ offerToReceiveAudio: false })
    await peer.setLocalDescription(offer)
    // SDP 经后端代理交换，浏览器不接触 ZLM secret，也没有跨域问题
    const { data: answerSdp } = await exchangeBroadcastSdpApi({
      streamId: data.streamId,
      sdp: offer.sdp ?? '',
    })
    if (controller.signal.aborted || pc !== peer) return
    await peer.setRemoteDescription({ type: 'answer', sdp: answerSdp })

    startLevelMeter()
    phase.value = 'live'
    toast.success(mode.value === 'talk' ? '已接通，可双向通话' : '已开始语音播报')

    // 对讲播放腿等设备音频到达可能要几秒，不阻塞喊话状态；失败只提示不影响喊话
    if (mode.value === 'talk') {
      startPlayLeg(data.streamId, controller).catch((err) => {
        if (controller.signal.aborted) return
        const msg: string = err?.message ?? '未知错误'
        // 设备反向 INVITE 只声明 recvonly（无麦克风/未开启对讲），已自动退化为单向播报
        if (msg.includes('不支持上行音频')) {
          toast.warning('该设备不支持语音回传，已按语音播报工作')
        } else {
          toast.error('设备音频接听失败：' + msg)
        }
      })
    }
  } catch (e) {
    // 主动取消不提示；start 接口的业务错误（设备拒绝/超时）由请求拦截器统一提示，这里只做清理
    if (controller.signal.aborted || (e instanceof Error && e.name === 'CanceledError')) {
      closePeer()
      phase.value = 'idle'
      return
    }
    if (e instanceof DOMException && e.name === 'NotAllowedError') {
      toast.error('麦克风权限被拒绝，无法开始' + featureName.value)
    }
    await rollback()
    phase.value = 'idle'
  } finally {
    if (abortController === controller) abortController = null
  }
}

/** start 失败/取消：通知后端停掉可能已建立的广播，再关本地腿 */
const rollback = async () => {
  const id = streamId.value
  closePeer()
  if (id) {
    await stopBroadcastApi({ streamId: id }).catch(() => {})
    streamId.value = ''
  }
}

const stop = async () => {
  phase.value = 'stopping'
  const id = streamId.value
  abortController?.abort()
  abortController = null
  closePeer()
  streamId.value = ''
  if (id) {
    try {
      await stopBroadcastApi({ streamId: id })
      toast.success(mode.value === 'talk' ? '已结束对讲' : '已停止播报')
    } catch {
      // 拦截器已提示
    }
  }
  phase.value = 'idle'
}

onBeforeUnmount(() => {
  const id = streamId.value
  abortController?.abort()
  closePeer()
  streamId.value = ''
  if (id) stopBroadcastApi({ streamId: id }).catch(() => {})
})
</script>

<template>
  <div class="flex h-full flex-col gap-3 overflow-auto p-3">
    <div class="rounded-xl border border-border bg-card">
      <div class="flex h-12 items-center gap-2 border-b border-border px-4">
        <Mic class="size-4 text-muted-foreground" />
        <span class="text-[13px] font-medium">{{ phase === 'idle' ? '语音播报 / 语音对讲' : featureName }}</span>
        <span class="text-xs text-muted-foreground">
          {{ mode === 'talk' ? '网页麦克风双向通话，单设备单通道' : '网页麦克风实时播报，单设备单通道' }}
        </span>
      </div>

      <div class="flex flex-col gap-5 p-5">
        <!-- 目标选择 -->
        <div class="flex flex-wrap items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="w-12 shrink-0 text-[13px] text-muted-foreground">设备</span>
            <div class="flex h-7 w-44 items-center rounded-md border border-border px-1">
              <GbDeviceSelect v-model="deviceId" />
            </div>
          </div>
          <div class="flex items-center gap-2">
            <span class="w-12 shrink-0 text-[13px] text-muted-foreground">通道</span>
            <div class="flex h-7 w-44 items-center rounded-md border border-border px-1">
              <GbChannelSelect v-model="channelId" :device-id="deviceId" />
            </div>
          </div>
        </div>

        <!-- 喊话状态区 -->
        <div
          class="flex items-center gap-5 rounded-lg border border-dashed px-5 py-6"
          :class="phase === 'live' ? 'border-emerald-400/60 bg-emerald-50/40' : 'border-border'"
        >
          <!-- 麦克风圆形按钮 -->
          <div class="relative grid size-16 shrink-0 place-items-center">
            <span
              v-if="phase === 'live'"
              class="absolute inline-flex size-16 animate-ping rounded-full bg-emerald-400/30"
            />
            <div
              class="relative grid size-14 place-items-center rounded-full"
              :class="
                phase === 'live'
                  ? 'bg-emerald-500 text-white'
                  : 'bg-muted text-muted-foreground'
              "
            >
              <Mic v-if="phase === 'live'" class="size-6" />
              <MicOff v-else class="size-6" />
            </div>
          </div>

          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2 text-sm font-medium">
              <template v-if="phase === 'idle'">未开始</template>
              <template v-else-if="phase === 'starting'">
                <Loader2 class="size-4 animate-spin" /> 正在通知设备…
              </template>
              <template v-else-if="phase === 'stopping'">
                <Loader2 class="size-4 animate-spin" /> 正在停止…
              </template>
              <template v-else>{{ liveText }}</template>
              <span v-if="mode === 'talk' && phase === 'live'"
                class="flex items-center gap-1 text-xs font-normal"
                :class="listening ? 'text-emerald-600' : 'text-amber-600'"
              >
                <span class="size-1.5 rounded-full" :class="listening ? 'bg-emerald-500' : 'bg-amber-500 animate-pulse'" />
                {{ listening ? '正在收听设备音频' : '等待设备音频…' }}
              </span>
              <span v-else-if="phase === 'live' && connState" class="text-xs font-normal text-muted-foreground">
                （{{ connState }}）
              </span>
            </div>
            <!-- 音量条 -->
            <div class="mt-2 h-2 w-full max-w-sm overflow-hidden rounded-full bg-muted">
              <div
                class="h-full rounded-full bg-emerald-500 transition-[width] duration-75"
                :style="{ width: `${Math.round(level * 100)}%` }"
              />
            </div>
            <p class="mt-2 text-xs text-muted-foreground">
              点击开始后浏览器会请求麦克风权限，设备接通后即可{{ mode === 'talk' ? '双向通话' : '播报' }}；关闭页面会自动停止。
            </p>
          </div>

          <!-- 操作按钮：播报 / 对讲 两个纯图标按钮，进行中变为停止 -->
          <div class="flex shrink-0 gap-2">
            <template v-if="phase === 'idle'">
              <Button
                size="icon"
                title="开始语音播报"
                :disabled="!canStart"
                v-access="PERM.GB_BROADCAST_TALK"
                @click="start('broadcast')"
              >
                <Megaphone class="size-4" />
              </Button>
              <Button
                size="icon"
                variant="secondary"
                title="开始对讲"
                :disabled="!canStart"
                v-access="PERM.GB_BROADCAST_TALK"
                @click="start('talk')"
              >
                <Phone class="size-4" />
              </Button>
            </template>
            <Button
              v-else-if="phase === 'starting'"
              size="icon"
              disabled
            >
              <Loader2 class="size-4 animate-spin" />
            </Button>
            <Button
              v-else
              size="icon"
              variant="destructive"
              title="停止"
              :loading="phase === 'stopping'"
              v-access="PERM.GB_BROADCAST_TALK"
              @click="stop"
            >
              <Square v-if="phase !== 'stopping'" class="size-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
