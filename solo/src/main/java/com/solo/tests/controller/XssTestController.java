package com.solo.tests.controller;

import com.solo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <b>警告：本组接口仅供开发 / 测试环境使用</b>
 */
@Tag(name = "XSS 过滤测试管理", description = "XSS 过滤测试管理")
@RestController
@RequestMapping("/api/test/xss")
public class XssTestController {

    @Operation(summary = "Body 体 XSS 过滤测试")
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

    @Operation(summary = "查询参数 XSS 过滤测试")
    @Parameter(name = "keyword", description = "查询关键词", in = ParameterIn.QUERY)
    @Parameter(name = "remark", description = "备注", in = ParameterIn.QUERY)
    @GetMapping("/param")
    public Result<Map<String, Object>> param(@RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "") String remark) {
        Map<String, Object> data = new HashMap<>();
        data.put("keyword", keyword);
        data.put("remark", remark);
        data.put("timestamp", System.currentTimeMillis());
        return Result.success(data);
    }

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
