/**
 * Markdown 渲染工具
 * - html: false：不解析裸 HTML，避免 XSS
 * - linkify: 自动识别 URL
 * - breaks: 单换行也渲染为 <br>，符合 AI 对话习惯
 * - 代码块走 highlight.js 高亮
 * - 数学公式 ($...$ / $$...$$ / \(...\) / \[...\]) 走 KaTeX 渲染
 *
 * 数学公式渲染策略：
 * 由于 markdown-it 设了 html: false，直接把 KaTeX HTML 注入到 markdown 文本中
 * 会被转义成纯文本。所以做法是：
 *   1) 解析前把代码段抽出来用占位符替换，避免代码内的 $ 被当作公式
 *   2) 把每段公式 KaTeX 渲染好后存起来，原位置用 token 占位
 *   3) markdown-it 渲染 markdown
 *   4) 把 token 用对应的 KaTeX HTML 替换回去
 */
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'
import 'katex/dist/katex.min.css'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch {
        // 高亮失败回退普通展示
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

// 链接默认在新标签页打开
const defaultLinkRender = md.renderer.rules.link_open || function (tokens, idx, options, _env, self) {
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  const token = tokens[idx]
  const targetIdx = token.attrIndex('target')
  if (targetIdx < 0) {
    token.attrPush(['target', '_blank'])
  } else if (token.attrs) {
    token.attrs[targetIdx][1] = '_blank'
  }
  const relIdx = token.attrIndex('rel')
  if (relIdx < 0) {
    token.attrPush(['rel', 'noopener noreferrer'])
  } else if (token.attrs) {
    token.attrs[relIdx][1] = 'noopener noreferrer'
  }
  return defaultLinkRender(tokens, idx, options, env, self)
}

const FENCE_RE = /(```[\s\S]*?```|`[^`\n]*`)/g
const BLOCK_TOKEN = (i: number) => `@@KATEX_BLOCK_${i}@@`
const INLINE_TOKEN = (i: number) => `@@KATEX_INLINE_${i}@@`

function renderMath(expr: string, displayMode: boolean): string {
  try {
    return katex.renderToString(expr, {
      throwOnError: false,
      displayMode,
      output: 'html'
    })
  } catch {
    return md.utils.escapeHtml(displayMode ? `$$${expr}$$` : `$${expr}$`)
  }
}

interface MaskedResult {
  text: string
  blocks: string[]
  inlines: string[]
}

/**
 * 公式抽取：替换为占位符，原文用 KaTeX 渲染好的 HTML 单独保存。
 */
function maskMath(text: string): MaskedResult {
  const blocks: string[] = []
  const inlines: string[] = []

  // 先把代码块抽出来，避免代码块里的 $ 被错误识别
  const fenceStore: string[] = []
  let masked = text.replace(FENCE_RE, (m) => {
    fenceStore.push(m)
    return ` __FENCE${fenceStore.length - 1}__ `
  })

  // 块级公式：放在独立段落里，便于后面整段替换
  masked = masked.replace(/\\\[([\s\S]+?)\\\]/g, (_m, body) => {
    blocks.push(renderMath(body.trim(), true))
    return `\n\n${BLOCK_TOKEN(blocks.length - 1)}\n\n`
  })
  masked = masked.replace(/\$\$([\s\S]+?)\$\$/g, (_m, body) => {
    blocks.push(renderMath(body.trim(), true))
    return `\n\n${BLOCK_TOKEN(blocks.length - 1)}\n\n`
  })

  // 行内公式
  masked = masked.replace(/\\\(([\s\S]+?)\\\)/g, (_m, body) => {
    inlines.push(renderMath(body.trim(), false))
    return INLINE_TOKEN(inlines.length - 1)
  })
  masked = masked.replace(/(?<![\w$])\$(?!\s)([^\n$]+?)(?<!\s)\$(?!\w)/g, (_m, body) => {
    inlines.push(renderMath(body, false))
    return INLINE_TOKEN(inlines.length - 1)
  })

  // 还原代码块
  masked = masked.replace(/__FENCE(\d+)__/g, (_m, idx) => fenceStore[Number(idx)])

  return { text: masked, blocks, inlines }
}

/**
 * 把渲染后的 HTML 中的占位符替换为 KaTeX HTML。
 * 块级 token 单独成段，会被 markdown-it 包成 <p>@@KATEX_BLOCK_N@@</p>，整段替换。
 */
function unmaskMath(html: string, blocks: string[], inlines: string[]): string {
  let out = html
  out = out.replace(/<p>\s*@@KATEX_BLOCK_(\d+)@@\s*<\/p>/g, (_m, idx) => blocks[Number(idx)] ?? '')
  out = out.replace(/@@KATEX_BLOCK_(\d+)@@/g, (_m, idx) => blocks[Number(idx)] ?? '')
  out = out.replace(/@@KATEX_INLINE_(\d+)@@/g, (_m, idx) => inlines[Number(idx)] ?? '')
  return out
}

export function renderMarkdown(text: string | undefined | null): string {
  if (!text) return ''
  const masked = maskMath(text)
  const html = md.render(masked.text)
  return unmaskMath(html, masked.blocks, masked.inlines)
}
