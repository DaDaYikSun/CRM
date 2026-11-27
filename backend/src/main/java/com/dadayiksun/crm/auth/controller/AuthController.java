package com.dadayiksun.crm.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.access.AuthSession;
import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.dto.ChangePasswordRequest;
import com.dadayiksun.crm.auth.dto.LoginRequest;
import com.dadayiksun.crm.auth.dto.LoginResponse;
import com.dadayiksun.crm.auth.service.LoginService;
import com.dadayiksun.crm.auth.service.PasswordService;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** 계약 C3 로그인 · 현재 직원 · 새 비밀번호 (API 설계 3-1). 로그아웃은 #167이 더한다. 새 비밀번호는 #150. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final PasswordService passwordService;

    public AuthController(LoginService loginService, PasswordService passwordService) {
        this.loginService = loginService;
        this.passwordService = passwordService;
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

    /** 새 비밀번호 정하기 (POL-ACC-010). 정하면 이 세션은 다른 API를 다시 쓸 수 있다. 출력 {@code { data: null }}. */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(CurrentStaff current, @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        passwordService.change(current.staffId(), body.newPassword());
        AuthSession.passwordChanged(request);
        return ApiResponse.of(null);
    }
}
