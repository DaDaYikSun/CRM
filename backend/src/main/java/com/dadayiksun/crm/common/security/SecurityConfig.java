package com.dadayiksun.crm.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 보안 기본 설정 (#142).
 *
 * <p>CSRF (팀 결정 K10, API 설계 1-6): Spring Security CSRF 토큰을 쓴다. 주고받는 방법은 backend/README.md 'CSRF'.
 * 서버가 쿠키 {@code XSRF-TOKEN}에 토큰을 주고, 화면은 저장 요청마다 헤더 {@code X-XSRF-TOKEN}에 그 값을 싣는다.
 * 토큰이 없거나 틀린 저장 요청은 403 FORBIDDEN (C1 형식).
 *
 * <p>로그인 · 역할 검사는 #143이 이 설정에 더한다. 지금은 모든 주소를 열어 둔다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityHandlers handlers) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .accessDeniedHandler(handlers)
                        .authenticationEntryPoint(handlers))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** 비밀번호는 BCrypt 해시로 저장한다 (NFR-SEC-001, DB 설계 4-1). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
