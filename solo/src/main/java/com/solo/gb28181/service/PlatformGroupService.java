package com.solo.gb28181.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformGroup;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.request.UnmountedChannelPageRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;

import java.util.List;

/**
 * 上级平台推送目录分组服务（per-platform 推送树）
 *
 * <p>当前提供查询 + CRUD。挂载 / 卸载接口待后续迭代"推送保存"功能时实现。</p>
 */
public interface PlatformGroupService {

    /**
     * 拉取某上级平台的推送目录分组树（含 children 嵌套）。
     * 根节点 = parent_id = 0；无数据时返回空列表。
     * 树由 {@link com.solo.common.utils.TreeUtils#build} 在 VO 上构建，Entity 不挂树形字段。
     */
    List<GbPlatformGroupResponse> listGroupTree(Long platformId);

    /**
     * 分页拉取某上级平台下"未挂载的通道"。
     *
     * <p><b>当前简化实现</b>：因为挂载关系尚未持久化，未挂载 = 所有 catalog 通道。
     * 后续实现挂载表后，本方法应改为"全通道 - 该平台任何组下挂载的通道"。</p>
     */
    IPage<GbDeviceChannel> pageUnmountedChannels(Long platformId, UnmountedChannelPageRequest req);

    /**
     * 新建分组
     */
    void createGroup(Long platformId, GbPlatformGroupSaveRequest req);

    /**
     * 改分组
     */
    void updateGroup(Long platformId, Long groupId, GbPlatformGroupSaveRequest req);

    /**
     * 删分组（拒绝有子节点）
     */
    void deleteGroup(Long platformId, Long groupId);
}
