package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysDictType;
import com.solo.system.request.DictTypePageRequest;
import com.solo.system.request.DictTypeRequest;

import java.util.List;

public interface SysDictTypeService extends IService<SysDictType> {

    Page<SysDictType> pageQuery(DictTypePageRequest req);

    void createDictType(DictTypeRequest request);

    void updateDictType(Long id, DictTypeRequest request);

    /** 删除字典类型时级联删除其下的字典数据 */
    void deleteDictType(Long id);

    void deleteBatch(List<Long> ids);
}
