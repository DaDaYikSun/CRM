package com.dadayiksun.crm.customer.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dadayiksun.crm.common.audit.AuditEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보호자 (DB 설계 4-2). 묶음 대표 (DB 설계 머리말 애그리거트, K16 ④).
 *
 * <p>#145는 등록에 쓰는 칸만 옮겼다. 나머지 칸(보조 연락처 · 선호 연락 방법 · 번호 상태 · 주의 태그)은
 * DB 기본값으로 들어가고, 고치는 이슈(#151 · 초안 19 · 초안 20)가 여기에 더한다.
 * {@code phone_last4}는 DB 생성 컬럼이라 옮기지 않는다.
 */
@Entity
@Table(name = "guardian")
public class Guardian extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guardian_id")
    private Long guardianId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "name_chosung", nullable = false, length = 30)
    private String nameChosung;

    /** 숫자만 (API 설계 1-4). UK 아님: 같은 번호로도 새로 등록할 수 있다 (POL-REG-005). */
    @Column(name = "phone", nullable = false, length = 11)
    private String phone;

    /** 번호 마지막 확인일. 등록할 때 한국 날짜 오늘 (DB 설계 4-2, POL-REG-008). */
    @Column(name = "phone_checked_at", nullable = false)
    private LocalDate phoneCheckedAt;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    @Column(name = "sms_agreed", nullable = false)
    private boolean smsAgreed;

    @Column(name = "sms_agreed_at")
    private LocalDateTime smsAgreedAt;

    protected Guardian() {
    }

    /**
     * 새 보호자. phone은 {@link PhoneNumber#normalize}를 거친 숫자만의 값이다.
     * 동의 칸은 동의했으면 그때 시간을 남긴다 (POL-REG-010, DB 설계 4-2).
     */
    public static Guardian register(String name, String phone, boolean privacyAgreed, boolean smsAgreed,
            LocalDate today, LocalDateTime now) {
        Guardian g = new Guardian();
        g.name = name;
        g.nameChosung = Chosung.of(name);
        g.phone = phone;
        g.phoneCheckedAt = today;
        g.privacyAgreed = privacyAgreed;
        g.privacyAgreedAt = privacyAgreed ? now : null;
        g.smsAgreed = smsAgreed;
        g.smsAgreedAt = smsAgreed ? now : null;
        return g;
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public String getName() {
        return name;
    }

    public String getNameChosung() {
        return nameChosung;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getPhoneCheckedAt() {
        return phoneCheckedAt;
    }

    public boolean isPrivacyAgreed() {
        return privacyAgreed;
    }

    public LocalDateTime getPrivacyAgreedAt() {
        return privacyAgreedAt;
    }

    public boolean isSmsAgreed() {
        return smsAgreed;
    }

    public LocalDateTime getSmsAgreedAt() {
        return smsAgreedAt;
    }
}
