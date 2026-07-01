package com.solo.service.impl;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.config.TerminalProperties;
import com.solo.entity.SysServer;
import com.solo.response.ServerFileResponse;
import com.solo.service.ServerFileService;
import com.solo.service.SysServerService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * 服务器文件管理服务实现类
 */
@Service
@RequiredArgsConstructor
public class ServerFileServiceImpl implements ServerFileService {

    private static final String AUTH_TYPE_PASSWORD = "PASSWORD";
    private static final String AUTH_TYPE_PRIVATE_KEY = "PRIVATE_KEY";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysServerService sysServerService;
    private final TerminalProperties terminalProperties;

    @Override
    public List<ServerFileResponse> listFiles(Long serverId, String path) {
        try (SftpClient client = openSftp(serverId)) {
            String targetPath = normalizePath(path);
            Vector<ChannelSftp.LsEntry> entries = client.channel.ls(targetPath);
            return entries.stream()
                    .filter(entry -> !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename()))
                    .map(entry -> toResponse(targetPath, entry))
                    .sorted(Comparator.comparing(ServerFileResponse::getDirectory).reversed()
                            .thenComparing(ServerFileResponse::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("读取服务器目录失败：" + e.getMessage());
        }
    }

    @Override
    public void upload(Long serverId, String path, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "请选择上传文件");
        }
        try (SftpClient client = openSftp(serverId)) {
            String targetPath = normalizePath(path);
            String filename = file.getOriginalFilename() == null ? "upload.file" : file.getOriginalFilename();
            client.channel.put(file.getInputStream(), joinPath(targetPath, filename));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("上传服务器文件失败：" + e.getMessage());
        }
    }

    @Override
    public void download(Long serverId, String path, HttpServletResponse response) throws IOException {
        try (SftpClient client = openSftp(serverId)) {
            String targetPath = normalizePath(path);
            String filename = targetPath.substring(targetPath.lastIndexOf('/') + 1);
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            client.channel.get(targetPath, response.getOutputStream());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("下载服务器文件失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(Long serverId, String path) {
        try (SftpClient client = openSftp(serverId)) {
            String targetPath = normalizePath(path);
            try {
                client.channel.rm(targetPath);
            } catch (Exception fileError) {
                client.channel.rmdir(targetPath);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("删除服务器文件失败：" + e.getMessage());
        }
    }

    private SftpClient openSftp(Long serverId) {
        SysServer server = sysServerService.getById(serverId);
        if (server == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "服务器不存在");
        }
        if (server.getStatus() == null || server.getStatus() == SystemConstants.STATUS_DISABLED) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "服务器已停用");
        }
        try {
            JSch jsch = new JSch();
            if (AUTH_TYPE_PRIVATE_KEY.equals(server.getAuthType())) {
                jsch.addIdentity("server-" + server.getId(), server.getPrivateKeySecret().getBytes(StandardCharsets.UTF_8), null, null);
            }
            Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            if (AUTH_TYPE_PASSWORD.equals(server.getAuthType())) {
                session.setPassword(server.getPasswordSecret());
            }
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(terminalProperties.getConnectTimeoutMs());
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(terminalProperties.getConnectTimeoutMs());
            return new SftpClient(session, channel);
        } catch (Exception e) {
            throw new ServiceException("连接服务器SFTP失败：" + e.getMessage());
        }
    }

    private ServerFileResponse toResponse(String parentPath, ChannelSftp.LsEntry entry) {
        ServerFileResponse response = new ServerFileResponse();
        response.setName(entry.getFilename());
        response.setPath(joinPath(parentPath, entry.getFilename()));
        response.setDirectory(entry.getAttrs().isDir());
        response.setSize(entry.getAttrs().isDir() ? null : entry.getAttrs().getSize());
        LocalDateTime modifiedTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(entry.getAttrs().getMTime()),
                ZoneId.systemDefault()
        );
        response.setModifiedTime(modifiedTime.format(DATE_TIME_FORMATTER));
        return response;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return ".";
        }
        return path.trim();
    }

    private String joinPath(String parent, String child) {
        if (parent == null || parent.isBlank() || ".".equals(parent)) {
            return child;
        }
        if (parent.endsWith("/")) {
            return parent + child;
        }
        return parent + "/" + child;
    }

    private static class SftpClient implements AutoCloseable {
        private final Session session;
        private final ChannelSftp channel;

        private SftpClient(Session session, ChannelSftp channel) {
            this.session = session;
            this.channel = channel;
        }

        @Override
        public void close() {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
