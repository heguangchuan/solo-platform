package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 国标资源目录分组（业务分组 215 / 虚拟组织 216）
 * <p>平台侧自建目录树，用于级联时向上级共享通道时组织目录结构；与下级设备上报的原始 Catalog
 * 目录无关，是本平台人为编排的资源目录。</p>
 * <p>逻辑关联：{@link GbDeviceChannel#getGroupId()} 引用本表 {@link #id}（数据主键），不建外键。</p>
 */
@Data
@TableName("gb_group")
public class GbGroup {

    /** 数据主键：默认分组固定 0000，其余雪花ID；通道 group_id 引用此列 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分组国标ID（20位；第11-13位 215业务分组 / 216虚拟组织，拼 Catalog 上报上级用） */
    private String groupId;

    /** 分组名称（作为 Catalog Item Name 上报上级） */
    private String name;

    /** 父分组国标ID（逻辑关联 {@link #groupId}）：0=根节点；存的是父分组的 20 位国标码而非雪花 id */
    private String parentId;

    /** 排序值（同层从小到大） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
