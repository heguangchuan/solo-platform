package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.request.GbGroupPageRequest;

import java.util.List;

/**
 * 国标资源目录分组服务
 *
 * <p>简单按字段查询直接走 {@code lambdaQuery()}，本接口只保留"业务动作"。</p>
 */
public interface GbGroupService extends IService<GbGroup> {

    Page<GbGroup> pageQuery(GbGroupPageRequest request);

    /**
     * 全量分组组装成树（parentId=0 为根），按 sort 升序。
     */
    List<GbGroup> tree();

    /**
     * 新增分组。id 由雪花算法生成；校验 groupId 20 位唯一，父节点存在。
     */
    void createGroup(GbGroup group);

    /**
     * 编辑分组资料。id / groupId / type 不可改（改了会导致通道 group_id 悬挂），只更新名称/父节点/排序。
     */
    void updateGroup(GbGroup group);

    /**
     * 删除分组。默认分组不可删；删除时把挂在该分组下的通道回落到默认分组，
     * 子节点 parentId 回落到根节点。
     */
    void deleteById(String id);
}
