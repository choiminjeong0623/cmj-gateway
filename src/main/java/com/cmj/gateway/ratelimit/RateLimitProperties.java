package com.cmj.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 호출 횟수 제한 설정.
 *
 * ✅ 매크로 장애 후 gateway에서 호출 횟수를 제한함 (구현은 본인, 제한값은 인프라팀·해당 업무팀이 정함 — 2026-11-27 확인)
 * ⚠️ 값(1분 15회)은 "정도"로 기억하는 숫자, 적용 경로는 예시
 *
 * cmj:
 *   rate-limit:
 *     max-per-minute: 15
 *     paths: [/api/tax-return/search/**]   # 매크로가 노렸던 무거운 조회 API (예시 경로)
 */
@ConfigurationProperties(prefix = "cmj.rate-limit")
public record RateLimitProperties(
    Integer maxPerMinute,
    List<String> paths
) {
    public RateLimitProperties {
        maxPerMinute = maxPerMinute == null ? 15 : maxPerMinute;
        paths = paths == null ? List.of() : paths;
    }
}
