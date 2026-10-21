package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbPlatformGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "推送目录分组树响应项")
@Data
@EqualsAndHashCode(callSuper = true)
public class GbPlatformGroupResponse extends GbPlatformGroup {

    @Schema(description = "子节点（树形结构）")
    private List<GbPlatformGroupResponse> children = new ArrayList<>();
}
