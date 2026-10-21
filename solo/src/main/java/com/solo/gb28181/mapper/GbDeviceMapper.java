package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.response.GbDeviceStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GbDeviceMapper extends BaseMapper<GbDevice> {

    @Select("SELECT status, COUNT(*) AS count FROM gb_device GROUP BY status")
    List<GbDeviceStatusCountVO> selectStatusCount();
}
