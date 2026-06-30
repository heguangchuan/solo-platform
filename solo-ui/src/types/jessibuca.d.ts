/**
 * jessibuca 全局声明
 * <p>jessibuca 通过 {@code public/static/js/jessibuca/jessibuca.js} 静态脚本加载，
 * 在 window 上暴露 {@code Jessibuca} 构造函数。此处仅给 TS 通过类型检查。</p>
 */
declare global {
  interface Window {
    Jessibuca: new (options: Record<string, unknown>) => JessibucaInstance
  }
}

/** jessibuca 实例最小可用接口（按需扩展） */
export interface JessibucaInstance {
  play(url: string): void
  pause(): void
  destroy(): void
  clearView(): void
  hasLoaded(): boolean
  on(event: string, cb: (...args: unknown[]) => void): void
}

export {}
