package com.solo.gb28181.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通道地图打点响应
 *
 * <p>对应 {@code GET /api/gb28181/device-channel/map-markers}。</p>
 *
 * <h3>坐标系约定</h3>
 * <p>原始坐标存 WGS-84（设备原始上报），出口处按前端 {@code map.coord-system} 配置
 * 转成目标系（{@code WGS84} / {@code GCJ02} / {@code BD09}），与前端瓦片源对齐。前端无脑用，不在 marker render 时再转。</p>
 */
@Data
public class MapMarkerResponse {

    /** 落在当前视口 + 数据库过滤后实际返回的条数（&gt; 5000 时被截断） */
    private int count;

    /** 数据库里有坐标的通道总数（不受视口限制，侧栏 KPI 用） */
    private int totalWithCoord;

    /** marker 列表（每个元素见 {@link MapMarker}） */
    private List<MapMarker> markers = new ArrayList<>();
}
