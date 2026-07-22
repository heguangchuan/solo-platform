package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * 对应数据库表 sys_log
 */
@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String module;

    private String businessType;

    private String description;

    private String requestMethod;

    private String requestUrl;

    private String requestParam;

    /**
     * 响应状态: 0 失败 1 成功
     */
    private Integer responseStatus;

    private String errorMsg;

    private Long costMs;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    /**
     * 客户端来源: web/app/...
     */
    private String clientSource;

    private String clientIp;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
