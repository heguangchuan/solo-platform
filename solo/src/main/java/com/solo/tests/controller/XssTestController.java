package com.solo.tests.controller;

import com.solo.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * XSS 过滤测试控制器
 *
 * <p><b>警告：本组接口仅供开发 / 测试环境使用，生产环境必须禁用或单独鉴权隔离</b>。
 * 用途：前端开发联调"输入消毒"时，往接口里塞 {@code <script>} 等危险标签，
 * 验证后端是否把字符串里的恶意片段清洗掉。</p>
 *
 * <h3>通用行为</h3>
 * <ul>
 *   <li>响应里的 {@code title} / {@code content} / {@code nested} / {@code tags} 等字符串字段即为后端清洗后的结果，
 *       前端应直接渲染而不必再做二次转义。</li>
 *   <li>清洗对 {@code String} 字段、嵌套 {@code Map}、{@code List<String>} 均生效，可一次覆盖多场景。</li>
 * </ul>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /body} — Body 体清洗：JSON 字符串字段、嵌套 Map、List 全部走 {@code XssRequestBodyAdvice}</li>
 *   <li>{@code GET /param} — 查询参数清洗：{@code @RequestParam} String 字段走 {@code XssStringArgumentResolver}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/test/xss")
public class XssTestController {

    /**
     * Body 体 XSS 过滤测试
     * <p>提交任意含 HTML / 脚本片段的 JSON 字符串，验证后端对字符串字段、嵌套 Map、List 的清洗效果。
     * 返回的 {@code title} / {@code content} / {@code nested} / {@code tags} 字段即为清洗后的字符串，
     * 前端可直接对照查看消毒结果。</p>
     */
    @PostMapping("/body")
    public Result<Map<String, Object>> body(@RequestBody XssTestRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", request.getTitle());
        data.put("content", request.getContent());
        data.put("nested", request.getNested());
        data.put("tags", request.getTags());
        data.put("timestamp", System.currentTimeMillis());
        return Result.success(data);
    }

    /**
     * 查询参数 XSS 过滤测试
     * <p>通过 query string 传入 {@code keyword} / {@code remark}（均可选，默认空串），
     * 验证后端对 {@code @RequestParam} String 字段的清洗。返回字段即为消毒后的字符串。</p>
     */
    @GetMapping("/param")
    public Result<Map<String, Object>> param(@RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "") String remark) {
        Map<String, Object> data = new HashMap<>();
        data.put("keyword", keyword);
        data.put("remark", remark);
        data.put("timestamp", System.currentTimeMillis());
        return Result.success(data);
    }

    /**
     * XSS 过滤测试请求
     *
     * <p>提交给 {@code POST /api/test/xss/body} 的 JSON 请求体。所有字段均为可选：</p>
     * <ul>
     *   <li>{@code title} — 字符串字段（单层）</li>
     *   <li>{@code content} — 字符串字段（可放长文本）</li>
     *   <li>{@code nested} — 嵌套 Map（覆盖容器类型清洗）</li>
     *   <li>{@code tags} — 字符串 List（覆盖数组类型清洗）</li>
     * </ul>
     */
    public static class XssTestRequest {
        private String title;
        private String content;
        private Map<String, Object> nested;
        private java.util.List<String> tags;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Map<String, Object> getNested() {
            return nested;
        }

        public void setNested(Map<String, Object> nested) {
            this.nested = nested;
        }

        public java.util.List<String> getTags() {
            return tags;
        }

        public void setTags(java.util.List<String> tags) {
            this.tags = tags;
        }
    }
}
