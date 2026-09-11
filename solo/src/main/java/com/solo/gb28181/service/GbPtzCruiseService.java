package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPtzCruise;
import com.solo.gb28181.entity.GbPtzCruisePoint;

import java.util.List;

public interface GbPtzCruiseService extends IService<GbPtzCruise> {

    List<GbPtzCruise> listByChannel(Long channelDbId);

    GbPtzCruise getDetail(Long id);

    void saveCruise(GbPtzCruise cruise, List<GbPtzCruisePoint> points);

    void updateCruise(GbPtzCruise cruise);

    void deleteCruise(Long id);

    void addPoint(Long cruiseId, GbPtzCruisePoint point);

    void removePoint(Long cruiseId, Integer presetId);

    void startCruise(Long cruiseId);

    void stopCruise(Long cruiseId);

    List<GbPtzCruisePoint> listPoints(Long cruiseId);
}
