package com.solo.tests.controller;

import com.solo.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/xss")
public class XssTestController {

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
