package com.taskflow.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装类。
 * <p>MyBatis-Plus 的 IPage 直接序列化字段名较杂，这里转成前端友好的结构。</p>
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }
}