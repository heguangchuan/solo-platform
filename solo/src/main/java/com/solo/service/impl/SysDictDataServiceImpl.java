package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysDictData;
import com.solo.mapper.SysDictDataMapper;
import com.solo.request.DictDataPageRequest;
import com.solo.request.DictDataRequest;
import com.solo.service.SysDictDataService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典数据服务实现类
 */
@Service
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    @Override
    public Page<SysDictData> pageQuery(DictDataPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysDictData> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (req.getDictType() != null && !req.getDictType().isBlank()) {
            wrapper.eq(SysDictData::getDictType, req.getDictType());
        }
        if (req.getDictLabel() != null && !req.getDictLabel().isBlank()) {
            wrapper.like(SysDictData::getDictLabel, req.getDictLabel());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysDictData::getStatus, req.getStatus());
        }
        wrapper.orderByAsc(SysDictData::getSort);
        return page(pageParam, wrapper);
    }

    @Override
    public List<SysDictData> listByType(String dictType) {
        return list(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, SystemConstants.STATUS_ENABLED)
                .orderByAsc(SysDictData::getSort));
    }

    @Override
    public void createDictData(DictDataRequest request) {
        SysDictData data = new SysDictData();
        data.setDictType(request.getDictType());
        data.setDictLabel(request.getDictLabel());
        data.setDictValue(request.getDictValue());
        data.setSort(request.getSort() != null ? request.getSort() : SystemConstants.DEFAULT_SORT);
        data.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        save(data);
    }

    @Override
    public void updateDictData(Long id, DictDataRequest request) {
        SysDictData data = getById(id);
        if (data == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "字典数据不存在");
        }
        if (request.getDictType() != null) data.setDictType(request.getDictType());
        if (request.getDictLabel() != null) data.setDictLabel(request.getDictLabel());
        if (request.getDictValue() != null) data.setDictValue(request.getDictValue());
        if (request.getSort() != null) data.setSort(request.getSort());
        if (request.getStatus() != null) data.setStatus(request.getStatus());
        updateById(data);
    }

    @Override
    public void deleteDictData(Long id) {
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        removeByIds(ids);
    }
}
