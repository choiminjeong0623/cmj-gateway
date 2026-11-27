package com.cmj.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

/**
 * 호출 횟수 제한 — 정해진 경로에 대해 "한 사람이 1분에 N번"까지만 허용하고, 넘으면 429.
 *
 * ✅ 매크로 장애 후 gateway에서 제한 (구현 본인, 제한값은 인프라팀·업무팀)
 * ⚠️ 구현 방식은 예시: Redis 카운터로 1분 단위 칸을 나눠 센다(고정 창).
 *    rl:{누구}:{분} 키를 INCR 하고, 처음 만들 때만 만료를 걸어 1분 뒤 저절로 사라지게 한다.
 *    📘 Spring Cloud Gateway 기본 RequestRateLimiter(토큰 버킷)를 쓰는 방법도 있음 — 실제로 무엇을 썼는지는 미확인.
 *
 * 누구로 셀까:
 *   - 매크로가 노린 API는 원인 수정 후 "로그인한 사용자만" 호출 가능 → JWT의 sub(사용자 ID)로 센다
 *   - 로그인 정보가 없으면 접속 IP로 센다
 *
 * gateway의 보안 필터(JWT 서명·만료 확인)가 먼저 돌고, 그다음 이 필터가 돈다.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Duration WINDOW_TTL = Duration.ofSeconds(70); // 1분 칸 + 여유

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(ReactiveStringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (props.paths().stream().noneMatch(p -> matcher.match(p, path))) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
            .map(Principal::getName)                      // JWT sub
            .defaultIfEmpty("ip:" + clientIp(exchange))
            .flatMap(this::countThisMinute)
            .flatMap(count -> count > props.maxPerMinute()
                ? tooManyRequests(exchange.getResponse())
                : chain.filter(exchange))
            // 📘 Redis가 안 되면 막지 않고 통과(fail-open). 제한 때문에 정상 업무까지 멈추지 않게 하려는 선택 — 실제 정책은 미확인
            .onErrorResume(RedisUnavailable.class, e -> chain.filter(exchange));
    }

    private Mono<Long> countThisMinute(String who) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "rl:" + who + ":" + minute;
        return redis.opsForValue().increment(key)
            .flatMap(count -> count == 1
                ? redis.expire(key, WINDOW_TTL).thenReturn(count)
                : Mono.just(count))
            .onErrorMap(RedisUnavailable::new);
    }

    private static String clientIp(ServerWebExchange exchange) {
        // ⚠️ 로드밸런서 뒤라면 X-Forwarded-For 를 봐야 함 — 실제 구성 미확인
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static Mono<Void> tooManyRequests(ServerHttpResponse res) {
        res.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);                 // 429
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        res.getHeaders().set("Retry-After", "60");
        byte[] body = "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}"
            .getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // 라우팅보다 앞
    }

    /** Redis 오류를 "제한 판단 불가"로 구분하기 위한 표시용 예외 */
    static class RedisUnavailable extends RuntimeException {
        RedisUnavailable(Throwable cause) { super(cause); }
    }
}
