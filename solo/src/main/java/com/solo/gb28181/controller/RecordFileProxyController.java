package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.support.RecordFileLinkSigner;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 录像文件代理：浏览器只拿平台签名链接，ZLM 地址不直接暴露。 */
@Hidden
@RestController
@RequestMapping("/api/record-file")
@RequiredArgsConstructor
public class RecordFileProxyController {

    private final RecordFileLinkSigner signer;
    private final GbMediaServerMapper mediaServerMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @GetMapping("/{token}")
    public void proxy(@PathVariable String token,
                      HttpServletRequest request,
                      HttpServletResponse response) {
        try {
            RecordFileLinkSigner.Link link = signer.verify(token);
            MediaServer server = mediaServerMapper.selectOne(new LambdaQueryWrapper<MediaServer>()
                    .eq(MediaServer::getServerId, link.serverId()));
            if (server == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "ZLM 节点不存在");
                return;
            }
            String upstreamUrl = ZlmRestClient.resolveDownloadUrl(server, link.url());
            HttpRequest.Builder upstream = HttpRequest.newBuilder(URI.create(upstreamUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET();
            String range = request.getHeader("Range");
            if (range != null && !range.isBlank()) {
                upstream.header("Range", range);
            }
            HttpResponse<InputStream> resp = httpClient.send(upstream.build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            response.setStatus(resp.statusCode());
            copyHeader(resp, "Content-Type", response);
            copyHeader(resp, "Content-Length", response);
            copyHeader(resp, "Accept-Ranges", response);
            copyHeader(resp, "Content-Range", response);
            try (InputStream in = resp.body()) {
                in.transferTo(response.getOutputStream());
            }
        } catch (Exception e) {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "录像文件不可访问");
            } catch (Exception ignored) {
                // response already committed
            }
        }
    }

    private static void copyHeader(HttpResponse<InputStream> resp, String name, HttpServletResponse target) {
        resp.headers().firstValue(name).ifPresent(value -> {
            if (value != null && !value.isBlank()) {
                target.setHeader(name, value);
            }
        });
    }
}
