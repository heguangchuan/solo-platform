package com.solo.gb28181.service;

import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;

import java.util.List;

/**
 * 目录共享服务
 * <p>本平台作为下级向上级平台共享资源目录 + 通道的编排与持久化。
 * 后续 S6.b 的 Catalog 查询响应 / 变更 Notify 会以此处保存的共享关系为数据源。</p>
 */
public interface CatalogShareService {

    /**
     * 本平台可共享的资源目录树：分组树（gb_group）作枝干，通道按 group_id 挂到分组下。
     */
    List<GbShareTreeNodeResponse> shareTree();

    /**
     * 查询某上级平台已共享的分组 / 通道节点 id（回填前端勾选态）。
     */
    GbPlatformSharedResponse getShared(Long platformId);

    /**
     * 全量覆盖保存某上级平台的共享目录 / 通道。
     */
    void saveShared(Long platformId, GbPlatformShareSaveRequest request);
}
