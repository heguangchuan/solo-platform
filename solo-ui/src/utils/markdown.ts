/**
 * Markdown 渲染工具
 * - html: false：不解析裸 HTML，避免 XSS
 * - linkify: 自动识别 URL
 * - breaks: 单换行也渲染为 <br>，符合 AI 对话习惯
 * - 代码块走 highlight.js 高亮
 */
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

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

export function renderMarkdown(text: string | undefined | null): string {
  if (!text) return ''
  return md.render(text)
}
