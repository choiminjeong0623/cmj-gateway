package com.cmj.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

/**
 * ✅ gateway는 WebFlux(Spring Cloud Gateway)
 * ✅ gateway는 JWT의 서명과 만료만 확인한다. 역할·화면 권한 검사는 하지 않는다
 *    → 각 업무 서비스의 common.security(SessionAuthFilter)가 Redis 세션으로 판단 (Q1)
 *
 * v1과 다른 점: hasRole(...) 규칙, roles 변환기, X-User-* 헤더 전달 필터를 모두 뺐다.
 * Authorization 헤더(JWT)는 그대로 업무 서비스까지 전달된다.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll() // 로그인·재발급: 아직 토큰이 없거나 만료된 상태 (🟩 우리가 만든 로그인 API)
                .pathMatchers("/api/public/**").permitAll()                 // 라벨·테마: 로그인 화면에서도 필요
                .pathMatchers(HttpMethod.POST, "/api/security/password-reset/**").permitAll() // ✅ 비밀번호 재설정: 로그인 전 기능
                // ✅ 비로그인 허용 API는 주소 끝 _NONLGN 규칙 (본인 기억). 토큰 없는 요청이 업무 서비스까지 가려면
                //    gateway도 이 주소를 통과시켜야 한다 — 실제로 gateway에 어떻게 열어 두었는지는 ⚠️ 미확인(예시)
                //    (PathPattern은 ** 뒤에 글자를 못 붙여서, 주소 끝을 직접 보는 matcher를 씀)
                .matchers(exchange -> exchange.getRequest().getPath().value().endsWith("_NONLGN")
                        ? ServerWebExchangeMatcher.MatchResult.match()
                        : ServerWebExchangeMatcher.MatchResult.notMatch()).permitAll()
                .anyExchange().authenticated())                             // 그 외: 유효한 JWT만 있으면 통과
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> {}));                 // 공개키(jwk-set-uri)로 서명·만료 검증
        return http.build();
    }
}
