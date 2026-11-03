package com.taskflow.controller;

import com.taskflow.common.Result;
import com.taskflow.service.DashboardService;
import com.taskflow.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据看板接口。
 */
@Tag(name = "06-数据看板")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "查询看板全部统计数据")
    @GetMapping
    public Result<DashboardVO> dashboard() {
        return Result.success(dashboardService.getDashboard());
    }

    @Operation(summary = "查询工单状态分布")
    @GetMapping("/status")
    public Result<List<Map<String, Object>>> status() {
        return Result.success(dashboardService.statusCount());
    }
}