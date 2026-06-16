package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.entity.SysFile;
import com.solo.entity.SysUser;
import com.solo.mapper.SysFileMapper;
import com.solo.request.FilePageRequest;
import com.solo.request.FileSaveRequest;
import com.solo.service.SysFileService;
import com.solo.service.storage.FileStorage;
import com.solo.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 文件管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private final FileStorage fileStorage;

    @Override
    public Page<SysFile> pageQuery(FilePageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 10L;
        Page<SysFile> param = new Page<>(page, pageSize);

        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(req.getOriginalName())) wrapper.like(SysFile::getOriginalName, req.getOriginalName());
        if (notBlank(req.getUploaderName())) wrapper.like(SysFile::getUploaderName, req.getUploaderName());
        if (notBlank(req.getStartTime())) wrapper.ge(SysFile::getCreateTime, req.getStartTime());
        if (notBlank(req.getEndTime())) wrapper.le(SysFile::getCreateTime, req.getEndTime());
        wrapper.orderByDesc(SysFile::getCreateTime);
        return page(param, wrapper);
    }

    @Override
    public SysFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
        String ext = extOf(originalName);
        String storageName = buildStorageName(ext);

        String url = fileStorage.upload(file, storageName);

        SysFile record = new SysFile();
        record.setOriginalName(originalName);
        record.setStorageName(storageName);
        record.setUrl(url);
        record.setExt(ext);
        record.setMimeType(file.getContentType());
        record.setSize(file.getSize());
        fillUploader(record);
        save(record);
        log.info("[file.upload] id={}, url={}, size={}", record.getId(), record.getUrl(), record.getSize());
        return record;
    }

    @Override
    public SysFile saveMeta(FileSaveRequest req) {
        if (req == null) throw new ServiceException("请求参数不能为空");
        if (!notBlank(req.getOriginalName()) || !notBlank(req.getUrl()) || req.getSize() == null) {
            throw new ServiceException("originalName / url / size 必填");
        }
        SysFile record = new SysFile();
        record.setOriginalName(req.getOriginalName());
        record.setStorageName(notBlank(req.getStorageName()) ? req.getStorageName() : req.getUrl());
        record.setUrl(req.getUrl());
        record.setExt(notBlank(req.getExt()) ? req.getExt() : extOf(req.getOriginalName()));
        record.setMimeType(req.getMimeType());
        record.setSize(req.getSize());
        fillUploader(record);
        save(record);
        return record;
    }

    @Override
    public void deleteOne(Long id) {
        SysFile record = getById(id);
        if (record == null) return;
        removeById(id);
        // 同步清存储侧：失败不影响 DB 软删，后台清理任务可作为兜底。
        try {
            fileStorage.delete(record.getStorageName());
        } catch (Exception e) {
            log.warn("[file.delete] 存储侧删除失败 id={}, storageName={}：{}", id, record.getStorageName(), e.getMessage());
        }
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<SysFile> records = listByIds(ids);
        removeByIds(ids);
        for (SysFile r : records) {
            try {
                fileStorage.delete(r.getStorageName());
            } catch (Exception e) {
                log.warn("[file.deleteBatch] 存储侧删除失败 id={}, storageName={}：{}", r.getId(), r.getStorageName(), e.getMessage());
            }
        }
    }

    /* ============================== 内部工具 ============================== */

    private void fillUploader(SysFile record) {
        SysUser user = UserHolderUtils.getCurrentUser();
        if (user != null) {
            record.setUploaderId(user.getId());
            record.setUploaderName(user.getUsername());
        }
        if (record.getCreateTime() == null) record.setCreateTime(LocalDateTime.now());
    }

    private static String extOf(String name) {
        if (name == null) return null;
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        return name.substring(idx + 1).toLowerCase();
    }

    private static String buildStorageName(String ext) {
        // 形如 2026/06/16/{uuid}.{ext}
        // 按年月日分目录便于运维归档；UUID 防止猜测，扩展名保留以便部分网关按 ext 设置 Content-Type
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String name = UUID.randomUUID().toString().replace("-", "");
        return ext == null || ext.isBlank() ? date + "/" + name : date + "/" + name + "." + ext;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
