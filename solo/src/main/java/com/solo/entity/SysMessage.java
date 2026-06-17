package com.solo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统消息实体
 */
@Data
@TableName("sys_message")
public class SysMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收用户名
     */
    private String username;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型：SYSTEM-系统通知，NOTICE-公告，REMINDER-提醒
     */
    private String type;

    /**
     * 消息级别：INFO-普通，WARNING-警告，ERROR-错误，IMPORTANT-重要
     */
    private String level;

    /**
     * 是否已读：0-未读，1-已读
     */
    private Integer readFlag;

    /**
     * 跳转路由（可选）
     */
    private String route;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
