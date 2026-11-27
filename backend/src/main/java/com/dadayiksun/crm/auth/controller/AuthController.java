package com.dadayiksun.crm.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.access.AuthSession;
import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.dto.LoginRequest;
import com.dadayiksun.crm.auth.dto.LoginResponse;
import com.dadayiksun.crm.auth.service.LoginService;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** 계약 C3 로그인 · 현재 직원 · 로그아웃 (API 설계 3-1). 새 비밀번호는 #150이 더한다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        Staff staff = loginService.login(body.loginId(), body.password());
        AuthSession.start(request, staff);
        return ApiResponse.of(LoginResponse.from(staff));
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(CurrentStaff current, HttpServletRequest request) {
        try {
            return ApiResponse.of(LoginResponse.from(loginService.findActive(current.staffId())));
        } catch (ApiException e) { // 직원이 없어졌거나 비활성: 세션도 지운다
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            throw e;
        }
    }

    /** 로그아웃 (POL-ACC-008, #167). 로그인이 없거나 이미 만료됐어도 200 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AuthSession.end(request);
        return ApiResponse.of(null);
    }
}
