package com.solo.dashboard.controller;

import com.solo.common.Result;
import com.solo.dashboard.response.DashboardStatsResponse;
import com.solo.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合统计控制器
 *
 * <p>一次 GET 拉完全部首页数据，避免前端瀑布请求。
 * 接口本身不鉴权——只要登录的用户都能看（首页对所有角色可见）。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardStatsResponse> stats() {
        return Result.success(dashboardService.aggregate());
    }
}
