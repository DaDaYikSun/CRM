package com.dadayiksun.crm.contact.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.contact.dto.ContactHistoryItem;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.dto.SaveContactRequest;
import com.dadayiksun.crm.contact.dto.SaveContactResponse;
import com.dadayiksun.crm.contact.dto.SendSmsRequest;
import com.dadayiksun.crm.contact.dto.SendSmsResponse;
import com.dadayiksun.crm.contact.service.ContactPanelService;
import com.dadayiksun.crm.contact.service.ContactSaveService;
import com.dadayiksun.crm.contact.service.ContactSmsDispatcher;
import com.dadayiksun.crm.contact.service.ContactSmsService;

/**
 * 연락 패널 · 지난 연락 · 결과 저장 (S-04, C13, API 설계 3-6). 관리자 · 데스크만 쓴다.
 * 미용사 403은 RoleAccessRules 기본값(규칙에 없는 /api/**는 관리자 · 데스크만)이 막는다 (T3 · T10, NFR-SEC-002).
 */
@RestController
public class ContactController {

    private final ContactPanelService panelService;
    private final ContactSaveService saveService;
    private final ContactSmsDispatcher smsDispatcher;
    private final ContactSmsService smsService;

    public ContactController(ContactPanelService panelService, ContactSaveService saveService,
                             ContactSmsDispatcher smsDispatcher, ContactSmsService smsService) {
        this.panelService = panelService;
        this.saveService = saveService;
        this.smsDispatcher = smsDispatcher;
        this.smsService = smsService;
    }

    @GetMapping("/api/guardians/{id}/contact-panel")
    public ApiResponse<ContactPanelResponse> panel(@PathVariable long id) {
        return ApiResponse.of(panelService.panel(id));
    }

    @GetMapping("/api/guardians/{id}/contacts")
    public ApiResponse<PageResponse<ContactHistoryItem>> history(@PathVariable long id,
                                                                 @RequestParam(required = false) String page,
                                                                 CurrentStaff staff) {
        return ApiResponse.of(panelService.history(id, PageQuery.parse(page), staff.staffId()));
    }

    @PostMapping("/api/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SaveContactResponse> save(@RequestBody SaveContactRequest body, CurrentStaff staff) {
        // save가 돌아오면 커밋된 상태. 2일차 문자는 그 뒤에 보낸다 (K21)
        return ApiResponse.of(smsDispatcher.sendAfterCommit(saveService.save(body, staff.staffId())));
    }

    /** [문자 보내기] 모의 발송 기록 (#174). 기록이 커밋된 뒤 보낸다 (K21) */
    @PostMapping("/api/contacts/sms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SendSmsResponse> sendSms(@RequestBody SendSmsRequest body, CurrentStaff staff) {
        return ApiResponse.of(smsDispatcher.sendAfterCommit(smsService.record(body, staff.staffId())));
    }
}
