package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysFile;
import com.solo.system.request.FilePageRequest;
import com.solo.system.request.FileSaveRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysFileService extends IService<SysFile> {

    Page<SysFile> pageQuery(FilePageRequest req);

    SysFile upload(MultipartFile file);

    SysFile upload(MultipartFile file, Long uploaderId, String uploaderName);

    SysFile saveMeta(FileSaveRequest request);

    void deleteOne(Long id);

    void deleteBatch(List<Long> ids);
}
