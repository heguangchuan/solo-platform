package com.solo.openapi;

import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.support.OpenApiSigner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


class OpenFishingLiveApiTest {

    private static final String APP_KEY = System.getProperty("openapi.app-key",
            "ak_83o0hlS2kHjfffSof3MMigz2");
    // 明文 appSecret 用 -Dopenapi.app-secret 或 OPENAPI_APP_SECRET 传，别提交；没配就跳过整类。
    private static final String APP_SECRET = System.getProperty("openapi.app-secret",
            System.getenv("OPENAPI_APP_SECRET"));
    private static final String DEVICE_ID = "50011800001320000856";
    private static final String CHANNEL_ID = "50011800001326023021";

    private static final String START_PATH = "/open-api/v1/play/start";
    private static final String STOP_PATH = "/open-api/v1/play/stop";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BASE_URL = System.getProperty("openapi.base-url", "http://localhost:8080");

    private final SecureRandom random = new SecureRandom();

    @BeforeAll
    static void platformMustBeRunning() {
        assumeTrue(APP_SECRET != null && !APP_SECRET.isBlank(),
                "未配置开放 API 明文密钥（-Dopenapi.app-secret=xxx 或 OPENAPI_APP_SECRET），跳过业务联调测试");
        URI uri = URI.create(BASE_URL);
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), 1000);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "开放 API 平台未启动（" + BASE_URL + "），跳过业务联调测试");
    }


    @Test
    @DisplayName("合法签名：真实开始点播 → 校验流信息与 requestId 回显 → 真实停止点播，停止幂等")
    void startThenStopRealPlay() throws Exception {

        String startBody = """
                {"deviceId":"%s","channelId":"%s","transport":"TCP-passive","streamNumber":null}"""
                .formatted(DEVICE_ID, CHANNEL_ID);

        SignedRequest startReq = signed(START_PATH, startBody, APP_KEY, APP_SECRET, null, null);
        HttpResponse<String> resp = post(startReq);

        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(200);
        JsonNode json = MAPPER.readTree(resp.body());
        assertThat(json.get("code").asInt()).as(resp.body()).isEqualTo(200);
        assertThat(json.get("message").asString()).isEqualTo("success");
        assertThat(json.get("requestId").asString()).isEqualTo(startReq.requestId());
        assertThat(resp.headers().firstValue(OpenApiConstants.HEADER_REQUEST_ID).orElseThrow())
                .isEqualTo(startReq.requestId());

        JsonNode data = json.get("data");
        String streamId = DEVICE_ID + "_" + CHANNEL_ID;
        assertThat(data.get("streamId").asString()).isEqualTo(streamId);
        assertThat(data.get("app").asString()).isEqualTo("rtp");
        assertThat(data.get("ssrc").asString()).matches("\\d{10}");
        assertThat(data.get("rtpPort").asInt()).isPositive();
        assertThat(data.get("transport").asString()).isEqualTo("TCP-passive");
        assertThat(data.get("flvUrl").asString()).contains(streamId);
        assertThat(data.get("wsFlvUrl").asString()).startsWith("ws").contains(streamId);
        assertThat(data.get("mediaServerId").asString()).isNotBlank();

        try {
            String stopBody = """
                    {"streamId":"%s"}""".formatted(streamId);
            SignedRequest stopReq = signed(STOP_PATH, stopBody, APP_KEY, APP_SECRET, null, null);
            HttpResponse<String> stopResp = post(stopReq);

            assertThat(stopResp.statusCode()).isEqualTo(200);
            JsonNode stopJson = MAPPER.readTree(stopResp.body());
            assertThat(stopJson.get("code").asInt()).isEqualTo(200);
            assertThat(stopJson.get("requestId").asString()).isEqualTo(stopReq.requestId());

            SignedRequest again = signed(STOP_PATH, stopBody, APP_KEY, APP_SECRET, null, null);
            HttpResponse<String> againResp = post(again);
            assertThat(againResp.statusCode()).isEqualTo(200);
            assertThat(MAPPER.readTree(againResp.body()).get("code").asInt()).isEqualTo(200);
        } finally {
            bestEffortStop(streamId);
        }
    }


    @Test
    @DisplayName("缺少鉴权头 → HTTP 401 / 40101")
    void missingCredentialRejected() throws Exception {
        HttpResponse<String> resp = post(START_PATH, "{}", Map.of("Content-Type", "application/json"));

        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(bizCode(resp)).isEqualTo(40101);
    }

    @Test
    @DisplayName("appKey 不存在 → HTTP 401 / 40102")
    void appNotFoundRejected() throws Exception {
        SignedRequest req = signed(START_PATH, "{}", "ak_not_exists_xxxxxxxx", APP_SECRET, null, null);

        HttpResponse<String> resp = post(req);

        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(bizCode(resp)).isEqualTo(40102);
    }

    @Test
    @DisplayName("密钥错误签名不匹配 → HTTP 401 / 40103")
    void badSignatureRejected() throws Exception {
        SignedRequest req = signed(START_PATH, "{}", APP_KEY, "WRONG_SECRET", null, null);

        HttpResponse<String> resp = post(req);

        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(bizCode(resp)).isEqualTo(40103);
    }

    @Test
    @DisplayName("时间戳超出 300s 偏差 → HTTP 401 / 40104")
    void expiredTimestampRejected() throws Exception {
        long expiredTs = Instant.now().getEpochSecond() - OpenApiConstants.TIMESTAMP_TOLERANCE_SECONDS - 60;
        SignedRequest req = signed(START_PATH, "{}", APP_KEY, APP_SECRET, expiredTs, null);

        HttpResponse<String> resp = post(req);

        assertThat(resp.statusCode()).isEqualTo(401);
        assertThat(bizCode(resp)).isEqualTo(40104);
    }

    @Test
    @DisplayName("同一 nonce 在窗口内重放 → 第二次 HTTP 401 / 40105")
    void duplicatedNonceRejected() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String nonce = freshNonce();

        // 第一次故意用错密钥：nonce 在 Redis 占位成功后签名校验失败，不会进入业务、不发 INVITE
        SignedRequest first = signed(START_PATH, "{}", APP_KEY, "WRONG_SECRET", ts, nonce);
        HttpResponse<String> firstResp = post(first);
        assertThat(firstResp.statusCode()).isEqualTo(401);
        assertThat(bizCode(firstResp)).isEqualTo(40103);

        // 第二次换成正确签名，但时间戳+nonce 与第一次相同 → 防重放环节直接拒
        SignedRequest replay = signed(START_PATH, "{}", APP_KEY, APP_SECRET, ts, nonce);
        HttpResponse<String> replayResp = post(replay);
        assertThat(replayResp.statusCode()).isEqualTo(401);
        assertThat(bizCode(replayResp)).isEqualTo(40105);
    }


    @Test
    @DisplayName("签名合法但缺少 deviceId/channelId → HTTP 400 / 40001")
    void missingDeviceParamRejected() throws Exception {
        SignedRequest req = signed(START_PATH, "{}", APP_KEY, APP_SECRET, null, null);

        HttpResponse<String> resp = post(req);

        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(400);
        assertThat(bizCode(resp)).isEqualTo(40001);
    }

    @Test
    @DisplayName("设备不存在 → HTTP 400 / 40003")
    void deviceNotFoundRejected() throws Exception {
        String body = """
                {"deviceId":"50011800001320000999","channelId":"50011800001326023099","transport":"TCP-passive","streamNumber":null}""";
        SignedRequest req = signed(START_PATH, body, APP_KEY, APP_SECRET, null, null);

        HttpResponse<String> resp = post(req);

        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(400);
        assertThat(bizCode(resp)).isEqualTo(40003);
    }


    private String freshNonce() {
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    // tsOverride/nonceOverride 是给过期和重放用例固定值用的，正常调用传 null
    private SignedRequest signed(String path, String body, String appKey, String secret,
                                 Long tsOverride, String nonceOverride) {
        long ts = tsOverride != null ? tsOverride : Instant.now().getEpochSecond();
        String nonce = nonceOverride != null ? nonceOverride : freshNonce();
        String signString = OpenApiSigner.buildSignString(
                "POST", path, "", OpenApiSigner.sha256Hex(body), String.valueOf(ts), nonce);
        String signature = OpenApiSigner.hmacSha256Hex(signString, secret);

        String requestId = UUID.randomUUID().toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put(OpenApiConstants.HEADER_APP_KEY, appKey);
        headers.put(OpenApiConstants.HEADER_TIMESTAMP, String.valueOf(ts));
        headers.put(OpenApiConstants.HEADER_NONCE, nonce);
        headers.put(OpenApiConstants.HEADER_SIGN_ALG, OpenApiConstants.SIGN_ALG_HMAC_SHA256);
        headers.put(OpenApiConstants.HEADER_SIGNATURE, signature);
        headers.put(OpenApiConstants.HEADER_REQUEST_ID, requestId);
        return new SignedRequest(path, body, headers, requestId);
    }

    private HttpResponse<String> post(SignedRequest req) throws Exception {
        return post(req.path(), req.body(), req.headers());
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void bestEffortStop(String streamId) {
        try {
            SignedRequest stop = signed(STOP_PATH, """
                    {"streamId":"%s"}""".formatted(streamId), APP_KEY, APP_SECRET, null, null);
            post(stop);
        } catch (Exception ignored) {
            // 清理失败不掩盖原始断言结果
        }
    }

    private int bizCode(HttpResponse<String> resp) {
        try {
            return MAPPER.readTree(resp.body()).get("code").asInt();
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 OpenApiResult: " + resp.body(), e);
        }
    }

    private record SignedRequest(String path, String body, Map<String, String> headers, String requestId) {
    }
}
