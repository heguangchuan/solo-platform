package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysDictData;
import com.solo.system.request.DictDataPageRequest;
import com.solo.system.request.DictDataRequest;

import java.util.List;

public interface SysDictDataService extends IService<SysDictData> {

    Page<SysDictData> pageQuery(DictDataPageRequest req);

    List<SysDictData> listByType(String dictType);

    void createDictData(DictDataRequest request);

    void updateDictData(Long id, DictDataRequest request);

    void deleteDictData(Long id);

    void deleteBatch(List<Long> ids);
}
