package com.solo.common;

import lombok.Data;

import java.util.List;

/**
 * 标准分页返回对象
 * 只包含前端需要的 total 和 rows，避免暴露 MyBatis-Plus Page 的内部结构
 */
@Data
public class PageResult<T> {

    private long total;
    private List<T> rows;

    public PageResult(long total, List<T> rows) {
        this.total = total;
        this.rows = rows;
    }
}
