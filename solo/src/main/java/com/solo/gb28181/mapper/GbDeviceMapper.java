package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.response.GbDeviceStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 国标设备 Mapper
 */
@Mapper
public interface GbDeviceMapper extends BaseMapper<GbDevice> {

    /**
     * 按状态分组统计设备数（全局口径，与列表查询条件无关）
     */
    @Select("SELECT status, COUNT(*) AS count FROM gb_device GROUP BY status")
    List<GbDeviceStatusCountVO> selectStatusCount();
}
