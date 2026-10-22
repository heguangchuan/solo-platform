package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbRecordPlan;

import java.util.List;

public interface GbRecordPlanService extends IService<GbRecordPlan> {

    /** 分页/列表：按设备过滤（deviceId 可空） */
    List<GbRecordPlan> listPlans(String deviceId);

    /** 详情，带周时段列表 */
    GbRecordPlan getDetail(Long id);

    /** 新增计划（含周时段，整段覆盖式保存） */
    void savePlan(GbRecordPlan plan);

    /** 编辑计划：先删旧时段再插新时段 */
    void updatePlan(GbRecordPlan plan);

    /** 删除计划及其时段，同时停掉它正在录的流 */
    void deletePlan(Long id);

    /** 启停 */
    void toggle(Long id, boolean enabled);
}
