package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysServer;
import com.solo.mapper.SysServerMapper;
import com.solo.request.ServerPageRequest;
import com.solo.request.ServerRequest;
import com.solo.service.SysServerService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务器服务实现类
 */
@Service
public class SysServerServiceImpl extends ServiceImpl<SysServerMapper, SysServer> implements SysServerService {

    private static final String AUTH_TYPE_PASSWORD = "PASSWORD";
    private static final String AUTH_TYPE_PRIVATE_KEY = "PRIVATE_KEY";

    @Override
    public Page<SysServer> pageQuery(ServerPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysServer> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysServer> wrapper = new LambdaQueryWrapper<>();
        if (req.getServerName() != null && !req.getServerName().isBlank()) {
            wrapper.like(SysServer::getServerName, req.getServerName());
        }
        if (req.getHost() != null && !req.getHost().isBlank()) {
            wrapper.like(SysServer::getHost, req.getHost());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysServer::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(SysServer::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createServer(ServerRequest request) {
        validateRequired(request);
        SysServer existing = getOne(new LambdaQueryWrapper<SysServer>()
                .eq(SysServer::getServerName, request.getServerName()));
        if (existing != null) {
            throw new ServiceException("服务器名称已存在");
        }
        SysServer server = new SysServer();
        server.setServerName(request.getServerName());
        server.setHost(request.getHost());
        server.setPort(request.getPort());
        server.setUsername(request.getUsername());
        server.setAuthType(request.getAuthType());
        server.setPasswordSecret(request.getPassword());
        server.setPrivateKeySecret(request.getPrivateKey());
        server.setOsType(request.getOsType());
        server.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        server.setDescription(request.getDescription());
        save(server);
    }

    @Override
    public void updateServer(Long id, ServerRequest request) {
        SysServer server = getById(id);
        if (server == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "服务器不存在");
        }
        if (request.getServerName() != null && !request.getServerName().isBlank()
                && !request.getServerName().equals(server.getServerName())) {
            SysServer existing = getOne(new LambdaQueryWrapper<SysServer>()
                    .eq(SysServer::getServerName, request.getServerName()));
            if (existing != null) {
                throw new ServiceException("服务器名称已存在");
            }
            server.setServerName(request.getServerName());
        }
        if (request.getHost() != null && !request.getHost().isBlank()) server.setHost(request.getHost());
        if (request.getPort() != null) {
            validatePort(request.getPort());
            server.setPort(request.getPort());
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) server.setUsername(request.getUsername());
        if (request.getAuthType() != null && !request.getAuthType().isBlank()) {
            validateAuthType(request.getAuthType());
            server.setAuthType(request.getAuthType());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            server.setPasswordSecret(request.getPassword());
        }
        if (request.getPrivateKey() != null && !request.getPrivateKey().isBlank()) {
            server.setPrivateKeySecret(request.getPrivateKey());
        }
        if (request.getOsType() != null) server.setOsType(request.getOsType());
        if (request.getStatus() != null) server.setStatus(request.getStatus());
        if (request.getDescription() != null) server.setDescription(request.getDescription());
        updateById(server);
    }

    @Override
    public void deleteServer(Long id) {
        SysServer server = getById(id);
        if (server == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "服务器不存在");
        }
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        removeByIds(ids);
    }

    private void validateRequired(ServerRequest request) {
        if (request.getServerName() == null || request.getServerName().isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "服务器名称不能为空");
        }
        if (request.getHost() == null || request.getHost().isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "主机地址不能为空");
        }
        if (request.getPort() == null) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "SSH端口不能为空");
        }
        validatePort(request.getPort());
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "登录用户名不能为空");
        }
        validateAuthType(request.getAuthType());
        if (AUTH_TYPE_PASSWORD.equals(request.getAuthType())
                && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "密码不能为空");
        }
        if (AUTH_TYPE_PRIVATE_KEY.equals(request.getAuthType())
                && (request.getPrivateKey() == null || request.getPrivateKey().isBlank())) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "私钥不能为空");
        }
    }

    private void validatePort(Integer port) {
        if (port < 1 || port > 65535) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "SSH端口范围必须为1-65535");
        }
    }

    private void validateAuthType(String authType) {
        if (authType == null || authType.isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "认证方式不能为空");
        }
        if (!AUTH_TYPE_PASSWORD.equals(authType) && !AUTH_TYPE_PRIVATE_KEY.equals(authType)) {
            throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, "认证方式不正确");
        }
    }
}
