package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysFile;
import com.solo.request.FilePageRequest;
import com.solo.request.FileSaveRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理服务接口
 */
public interface SysFileService extends IService<SysFile> {

    /**
     * 分页查询文件
     */
    Page<SysFile> pageQuery(FilePageRequest req);

    /**
     * 上传文件并返回可访问 URL。
     */
    SysFile upload(MultipartFile file);

    /**
     * 仅落库元数据（前端/独立服务已上传字节，只调本接口同步元信息到 sys_file）。
     */
    SysFile saveMeta(FileSaveRequest request);

    /**
     * 软删除单个
     */
    void deleteOne(Long id);

    /**
     * 软删除批量
     */
    void deleteBatch(List<Long> ids);
}
