package com.dadayiksun.crm.customer.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

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
 * <p>#145는 등록에 쓰는 칸만 옮겼고, #151이 보조 연락처 · 선호 연락 방법 · 번호 상태 · 주의 태그를 더했다.
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

    @Column(name = "sub_phone", length = 11)
    private String subPhone;

    /** PHONE 또는 SMS, 없으면 null. */
    @Column(name = "preferred_contact", length = 5, columnDefinition = "enum('PHONE','SMS')")
    private String preferredContact;

    /** OK 또는 WRONG(번호 오류). 번호를 바꾸면 OK로 돌아간다 (POL-REG-008). */
    @Column(name = "phone_status", nullable = false, length = 5, columnDefinition = "enum('OK','WRONG')")
    private String phoneStatus = "OK";

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

    @Column(name = "tag_no_contact", nullable = false)
    private boolean tagNoContact;

    @Column(name = "tag_sms_only", nullable = false)
    private boolean tagSmsOnly;

    @Column(name = "tag_sensitive", nullable = false)
    private boolean tagSensitive;

    @Column(name = "tag_contact_time", nullable = false)
    private boolean tagContactTime;

    @Column(name = "contact_time_note", length = 50)
    private String contactTimeNote;

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

    public void rename(String name) {
        this.name = name;
        this.nameChosung = Chosung.of(name);
    }

    /** 번호를 바꾸면 확인 날짜는 오늘, 상태는 OK (POL-REG-008). phone은 정규화된 숫자만. */
    public void changePhone(String phone, LocalDate today) {
        this.phone = phone;
        this.phoneCheckedAt = today;
        this.phoneStatus = "OK";
    }

    /** 연락 결과 '번호 오류' (FR-CNT-003). 번호를 고치면 changePhone이 OK로 되돌린다 */
    public void markPhoneWrong() {
        this.phoneStatus = "WRONG";
    }

    public void changeSubPhone(String subPhone) {
        this.subPhone = subPhone;
    }

    public void changePreferredContact(String preferredContact) {
        this.preferredContact = preferredContact;
    }

    /** 태그 전체를 바꾼다. CONTACT_TIME이 빠지면 메모도 비운다 (DB ck_guardian_contact_time). */
    public void changeTags(Set<GuardianTag> tags) {
        this.tagNoContact = tags.contains(GuardianTag.NO_CONTACT);
        this.tagSmsOnly = tags.contains(GuardianTag.SMS_ONLY);
        this.tagSensitive = tags.contains(GuardianTag.SENSITIVE);
        this.tagContactTime = tags.contains(GuardianTag.CONTACT_TIME);
        if (!tagContactTime) {
            this.contactTimeNote = null;
        }
    }

    public void changeContactTimeNote(String note) {
        this.contactTimeNote = note;
    }

    /** 붙어 있는 태그. enum 순서로 돌려준다. */
    public Set<GuardianTag> tags() {
        Set<GuardianTag> tags = EnumSet.noneOf(GuardianTag.class);
        if (tagNoContact) tags.add(GuardianTag.NO_CONTACT);
        if (tagSmsOnly) tags.add(GuardianTag.SMS_ONLY);
        if (tagSensitive) tags.add(GuardianTag.SENSITIVE);
        if (tagContactTime) tags.add(GuardianTag.CONTACT_TIME);
        return tags;
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

    public String getSubPhone() {
        return subPhone;
    }

    public String getPreferredContact() {
        return preferredContact;
    }

    public String getPhoneStatus() {
        return phoneStatus;
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

    public String getContactTimeNote() {
        return contactTimeNote;
    }
}
