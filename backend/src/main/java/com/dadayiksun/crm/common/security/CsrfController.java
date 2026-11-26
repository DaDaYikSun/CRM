package com.dadayiksun.crm.common.security;

import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;

/**
 * 화면이 첫 저장 요청 전에 CSRF 쿠키를 받는 주소 (K10). 응답과 함께 쿠키 XSRF-TOKEN이 내려간다.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.of(Map.of("headerName", token.getHeaderName()));
    }
}
