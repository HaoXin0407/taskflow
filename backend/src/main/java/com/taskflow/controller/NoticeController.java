package com.taskflow.controller;

import com.taskflow.annotation.OperLog;
import com.taskflow.common.PageResult;
import com.taskflow.common.Result;
import com.taskflow.entity.SysNotice;
import com.taskflow.entity.SysOperLog;
import com.taskflow.service.NoticeService;
import com.taskflow.service.OperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 通知与操作日志接口。
 */
@Tag(name = "05-通知与日志")
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final OperLogService operLogService;

    // ==================== 站内通知 ====================

    @Operation(summary = "分页查询我的通知")
    @GetMapping("/notice/page")
    public Result<PageResult<SysNotice>> noticePage(@RequestParam(defaultValue = "1") Long pageNum,
                                                    @RequestParam(defaultValue = "10") Long pageSize,
                                                    @RequestParam(required = false) Integer isRead) {
        return Result.success(noticeService.page(pageNum, pageSize, isRead));
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/notice/unread")
    public Result<Integer> unread() {
        return Result.success(noticeService.unreadCount());
    }

    @Operation(summary = "标记已读")
    @PostMapping("/notice/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        noticeService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/notice/read-all")
    @OperLog(title = "通知中心", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> readAll() {
        noticeService.markAllRead();
        return Result.success();
    }

    // ==================== 操作日志 ====================

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/system/log/page")
    @PreAuthorize("hasAuthority('system:log') or hasRole('ADMIN')")
    public Result<PageResult<SysOperLog>> logPage(@RequestParam(defaultValue = "1") Long pageNum,
                                                  @RequestParam(defaultValue = "10") Long pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(operLogService.page(pageNum, pageSize, keyword, status));
    }
}