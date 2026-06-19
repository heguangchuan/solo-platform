package com.solo.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

/**
 * 共享 HttpClient 与 ObjectMapper，避免各 LlmClient 重复创建。
 */
public final class HttpClientHolder {

    private HttpClientHolder() {
    }

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final ObjectMapper MAPPER = new ObjectMapper();
}
