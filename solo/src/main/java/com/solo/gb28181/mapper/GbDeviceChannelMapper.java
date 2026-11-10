package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.response.GbChannelStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Mapper
public interface GbDeviceChannelMapper extends BaseMapper<GbDeviceChannel> {

    // 先合并批内重复业务键，再走 XML 里的按库方言 upsert
    default int batchUpsert(List<GbDeviceChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return 0;
        }
        return batchUpsertRaw(mergeDuplicates(channels));
    }

    int batchUpsertRaw(@Param("list") List<GbDeviceChannel> channels);

    @Select("SELECT parental, status, COUNT(*) AS count FROM gb_device_channel GROUP BY parental, status")
    List<GbChannelStatusCountVO> selectStatusCount();

    // 合并语义对齐 MySQL：后出现的非 null 上报值覆盖先出现的，null 不覆盖
    private static List<GbDeviceChannel> mergeDuplicates(List<GbDeviceChannel> channels) {
        Map<String, GbDeviceChannel> merged = new LinkedHashMap<>(channels.size());
        int invalid = 0;
        for (GbDeviceChannel ch : channels) {
            // channel_id 为空属于脏数据，不参与合并，交给库里的非空约束报错
            String key = ch.getChannelId() == null
                    ? "invalid#" + invalid++
                    : ch.getDeviceId() + '\u0000' + ch.getChannelId();
            GbDeviceChannel exist = merged.get(key);
            if (exist == null) {
                merged.put(key, ch);
            } else {
                mergeReportedFields(exist, ch);
            }
        }
        return merged.size() == channels.size() ? channels : new ArrayList<>(merged.values());
    }

    // 只合并冲突时会被更新的上报字段；id / parental / parent_id / source / create_time 保留首行
    private static void mergeReportedFields(GbDeviceChannel target, GbDeviceChannel later) {
        if (later.getReportName() != null) target.setReportName(later.getReportName());
        if (later.getName() != null) target.setName(later.getName());
        if (later.getManufacturer() != null) target.setManufacturer(later.getManufacturer());
        if (later.getModel() != null) target.setModel(later.getModel());
        if (later.getOwner() != null) target.setOwner(later.getOwner());
        if (later.getCivilCode() != null) target.setCivilCode(later.getCivilCode());
        if (later.getBlock() != null) target.setBlock(later.getBlock());
        if (later.getAddress() != null) target.setAddress(later.getAddress());
        if (later.getSafetyWay() != null) target.setSafetyWay(later.getSafetyWay());
        if (later.getRegisterWay() != null) target.setRegisterWay(later.getRegisterWay());
        if (later.getCertNum() != null) target.setCertNum(later.getCertNum());
        if (later.getCertifiable() != null) target.setCertifiable(later.getCertifiable());
        if (later.getSecrecy() != null) target.setSecrecy(later.getSecrecy());
        if (later.getIpAddress() != null) target.setIpAddress(later.getIpAddress());
        if (later.getPort() != null) target.setPort(later.getPort());
        if (later.getPassword() != null) target.setPassword(later.getPassword());
        if (later.getStatus() != null) target.setStatus(later.getStatus());
        if (later.getLongitude() != null) target.setLongitude(later.getLongitude());
        if (later.getLatitude() != null) target.setLatitude(later.getLatitude());
        if (later.getPtzType() != null) target.setPtzType(later.getPtzType());
    }
}
