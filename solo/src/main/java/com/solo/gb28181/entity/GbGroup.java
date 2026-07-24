package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 国标虚拟目录分组（业务分组 215 / 虚拟组织 216）
 * <p>平台侧自建目录树，用于级联时向上级共享通道时组织目录结构；与下级设备上报的原始 Catalog
 * 目录无关，是本平台人为编排的虚拟目录。</p>
 * <p>逻辑关联：{@link GbDeviceChannel#getGroupId()} 引用本表 {@link #id}（数据主键），不建外键。</p>
 */
@Data
@TableName("gb_group")
public class GbGroup {

    /** 数据主键：默认分组固定 0000，其余雪花ID；通道 group_id 引用此列 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 分组国标ID（20位；第11-13位 215业务分组 / 216虚拟组织，拼 Catalog 上报上级用） */
    private String groupId;

    /** 分组名称（作为 Catalog Item Name 上报上级） */
    private String name;

    /** 父节点主键：0=根节点 */
    private String parentId;

    /** 数据来源：0000=自有平台；其他=级联来源平台ID */
    private String platformId;

    /** 排序值（同层从小到大） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 子节点（树形返回用，不入库） */
    @TableField(exist = false)
    private List<GbGroup> children;
}
