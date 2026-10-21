package com.solo.onvif.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.onvif.entity.OnvifDevice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OnvifDeviceMapper extends BaseMapper<OnvifDevice> {
}
