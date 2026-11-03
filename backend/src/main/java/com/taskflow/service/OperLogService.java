package com.taskflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.common.PageResult;
import com.taskflow.entity.SysOperLog;
import com.taskflow.mapper.SysOperLogMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    /**
     * 分页查询操作日志。
     */
    public PageResult<SysOperLog> page(Long pageNum, Long pageSize, String keyword, Integer status) {
        Page<SysOperLog> page = new Page<>(pageNum, pageSize);
        IPage<SysOperLog> result = operLogMapper.selectPage(page, Wrappers.<SysOperLog>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SysOperLog::getOperName, keyword).or()
                        .like(SysOperLog::getTitle, keyword).or()
                        .like(SysOperLog::getOperUrl, keyword))
                .eq(status != null, SysOperLog::getStatus, status)
                .orderByDesc(SysOperLog::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 批量删除历史日志（保留最近 N 天）。
     */
    public void clean(Integer keepDays) {
        operLogMapper.delete(Wrappers.<SysOperLog>lambdaQuery()
                .lt(SysOperLog::getOperTime, java.time.LocalDateTime.now().minusDays(keepDays)));
    }
}