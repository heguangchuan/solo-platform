package com.solo.openapi.constant;

/**
 * 开放 API Redis key。常量值与 {@link OpenApiConstants} 中的前缀注释保持一致。
 */
public interface OpenApiRedisKeys {

    /** nonce 防重放：openapi:nonce:{appKey}:{nonce} */
    String NONCE_PREFIX = "openapi:nonce:";

    /** 应用档案缓存（含解密密钥，仅服务端使用）：openapi:app:{appKey} */
    String OPEN_APP_PREFIX = "openapi:app:";

    /** QPS 秒级计数：openapi:rl:qps:{appKey}:{epochSecond} */
    String RATE_QPS_PREFIX = "openapi:rl:qps:";

    /** 日调用量计数：openapi:rl:day:{appKey}:{yyyyMMdd} */
    String RATE_DAY_PREFIX = "openapi:rl:day:";
}
