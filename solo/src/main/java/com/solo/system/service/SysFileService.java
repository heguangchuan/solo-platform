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

    /**
     * 上传文件并显式指定上传人，用于异步任务等没有 HTTP 请求上下文的场景。
     */
    SysFile upload(MultipartFile file, Long uploaderId, String uploaderName);

    /**
     * 仅落库元数据（前端/独立服务已上传字节，只调本接口同步元信息到 sys_file）。
     */
    SysFile saveMeta(FileSaveRequest request);

    void deleteOne(Long id);

    void deleteBatch(List<Long> ids);
}
