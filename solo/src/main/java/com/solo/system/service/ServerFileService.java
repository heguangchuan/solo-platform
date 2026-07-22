package com.solo.system.service;

import com.solo.system.response.ServerFileResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 服务器文件管理服务接口
 */
public interface ServerFileService {

    List<ServerFileResponse> listFiles(Long serverId, String path);

    void upload(Long serverId, String path, MultipartFile file) throws IOException;

    void download(Long serverId, String path, HttpServletResponse response) throws IOException;

    void delete(Long serverId, String path);
}
