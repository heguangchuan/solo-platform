package com.solo.gb28181.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通道地图打点响应
 *
 * <p>字段名刻意压成单字符（c/d/n/x/y/s）以最小化 JSON 体积。
 * 5000 条 × 每条 ~50B ≈ 250KB，gzip 后 ~50KB，可接受。
 * 任何后续字段扩展请保持单字母以维持紧凑。</p>
 *
 * <p>坐标系：DB 存 WGS-84（设备原始上报），出口处按 {@code map.coord-system} 转成目标系
 * （WGS84 / GCJ02 / BD09），与前端瓦片源对齐。前端无脑用，不在 marker render 时再转。</p>
 *
 * <p>历史备注（v1 直用原始值会有几十米 WGS-84 vs GCJ-02 偏移）已废弃，转换在 {@code queryMapMarkers} 出口统一完成。</p>
 */
@Data
public class MapMarkerResponse {

    /** 落在当前视口 + 数据库过滤后实际返回的条数（>5000 时被截断） */
    private int count;

    /** 数据库里有坐标的通道总数（不受视口限制，侧栏 KPI 用） */
    private int totalWithCoord;

    /** marker 列表 */
    private List<MapMarker> markers = new ArrayList<>();
}
