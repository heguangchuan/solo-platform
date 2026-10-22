package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbRecordPlan;
import com.solo.gb28181.request.GbRecordPlanPageRequest;

public interface GbRecordPlanService extends IService<GbRecordPlan> {

    Page<GbRecordPlan> listPlans(GbRecordPlanPageRequest request);

    GbRecordPlan getDetail(Long id);

    void savePlan(GbRecordPlan plan);

    void updatePlan(GbRecordPlan plan);

    void deletePlan(Long id);

    void toggle(Long id, boolean enabled);
}
