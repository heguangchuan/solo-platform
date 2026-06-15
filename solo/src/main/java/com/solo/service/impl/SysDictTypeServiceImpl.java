package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysDictData;
import com.solo.entity.SysDictType;
import com.solo.mapper.SysDictDataMapper;
import com.solo.mapper.SysDictTypeMapper;
import com.solo.request.DictTypePageRequest;
import com.solo.request.DictTypeRequest;
import com.solo.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典类型服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictTypeService {

    private final SysDictDataMapper sysDictDataMapper;

    @Override
    public Page<SysDictType> pageQuery(DictTypePageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysDictType> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (req.getDictName() != null && !req.getDictName().isBlank()) {
            wrapper.like(SysDictType::getDictName, req.getDictName());
        }
        if (req.getDictType() != null && !req.getDictType().isBlank()) {
            wrapper.like(SysDictType::getDictType, req.getDictType());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysDictType::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(SysDictType::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createDictType(DictTypeRequest request) {
        SysDictType existing = getOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, request.getDictType()));
        if (existing != null) {
            throw new ServiceException("字典类型编码已存在");
        }
        SysDictType type = new SysDictType();
        type.setDictName(request.getDictName());
        type.setDictType(request.getDictType());
        type.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        type.setRemark(request.getRemark());
        save(type);
    }

    @Override
    public void updateDictType(Long id, DictTypeRequest request) {
        SysDictType type = getById(id);
        if (type == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "字典类型不存在");
        }
        String oldDictType = type.getDictType();
        if (request.getDictName() != null) type.setDictName(request.getDictName());
        if (request.getDictType() != null) type.setDictType(request.getDictType());
        if (request.getStatus() != null) type.setStatus(request.getStatus());
        if (request.getRemark() != null) type.setRemark(request.getRemark());
        updateById(type);
        // 类型编码变更时，同步刷新关联字典数据
        if (request.getDictType() != null && !request.getDictType().equals(oldDictType)) {
            SysDictData update = new SysDictData();
            update.setDictType(request.getDictType());
            sysDictDataMapper.update(update,
                    new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType, oldDictType));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictType(Long id) {
        SysDictType type = getById(id);
        if (type == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "字典类型不存在");
        }
        sysDictDataMapper.delete(
                new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType, type.getDictType()));
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        List<SysDictType> types = listByIds(ids);
        if (!types.isEmpty()) {
            List<String> codes = types.stream().map(SysDictType::getDictType).toList();
            sysDictDataMapper.delete(
                    new LambdaQueryWrapper<SysDictData>().in(SysDictData::getDictType, codes));
        }
        removeByIds(ids);
    }
}
