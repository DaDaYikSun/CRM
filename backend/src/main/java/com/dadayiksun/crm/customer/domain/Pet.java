package com.dadayiksun.crm.customer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dadayiksun.crm.common.audit.AuditEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 반려동물 (DB 설계 4-3). 묶음 대표 (DB 설계 머리말 애그리거트, K16 ④). 보호자와는 ID로만 잇는다.
 *
 * <p>#145는 등록에 쓰는 칸만 옮겼다. {@code species}는 DB 기본값 'DOG'(ENUM에 'DOG'만 있음)로 들어간다
 * (POL-REG-003, NFR-EXT-002). #151이 체중 · 상태 · 연락 보류 칸을 더했다. 선택 항목 · 미용 주기 칸은
 * 그 이슈(초안 19 · #144)가 더한다. 무지개다리로 바꾸기(초안 21)는 아직 없다.
 */
@Entity
@Table(name = "pet")
public class Pet extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_id")
    private Long petId;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "name_chosung", nullable = false, length = 30)
    private String nameChosung;

    @Column(name = "breed_id", nullable = false)
    private Long breedId;

    @Column(name = "coat_type_id", nullable = false)
    private Long coatTypeId;

    /** 추정이면 한국 날짜 오늘에서 대략 나이만큼 뺀 날 (POL-REG-004). */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** true면 화면에 '추정' 표시 (POL-REG-004). */
    @Column(name = "birth_date_estimated", nullable = false)
    private boolean birthDateEstimated;

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 7)
    private PetStatus status = PetStatus.ACTIVE;

    @Column(name = "rainbow_at")
    private LocalDateTime rainbowAt;

    /** 이 날까지 연락 보류 (POL-TGT-003). */
    @Column(name = "contact_hold_until")
    private LocalDate contactHoldUntil;

    /** 보류 원인이 된 아이 (되돌리기용, POL-END-004). */
    @Column(name = "contact_hold_by_pet_id")
    private Long contactHoldByPetId;

    protected Pet() {
    }

    public static Pet register(Long guardianId, String name, Long breedId, Long coatTypeId, BirthDate birth) {
        Pet p = new Pet();
        p.guardianId = guardianId;
        p.name = name;
        p.nameChosung = Chosung.of(name);
        p.breedId = breedId;
        p.coatTypeId = coatTypeId;
        p.birthDate = birth.date();
        p.birthDateEstimated = birth.estimated();
        return p;
    }

    public void rename(String name) {
        this.name = name;
        this.nameChosung = Chosung.of(name);
    }

    public void changeBreed(Long breedId) {
        this.breedId = breedId;
    }

    public void changeCoatType(Long coatTypeId) {
        this.coatTypeId = coatTypeId;
    }

    /** 실제 날짜로 고치면 '추정' 표시를 뗀다 (POL-REG-004). */
    public void correctBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        this.birthDateEstimated = false;
    }

    public void changeWeight(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public boolean isRainbow() {
        return status == PetStatus.RAINBOW;
    }

    /** 무지개다리 되돌리기: 상태만 되돌린다. 보류 칸은 되돌리기 서비스가 정한다 (POL-END-004). */
    public void revertRainbow() {
        this.status = PetStatus.ACTIVE;
        this.rainbowAt = null;
    }

    public void holdContact(LocalDate until, Long byPetId) {
        this.contactHoldUntil = until;
        this.contactHoldByPetId = byPetId;
    }

    public PetStatus getStatus() {
        return status;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public LocalDate getContactHoldUntil() {
        return contactHoldUntil;
    }

    public Long getContactHoldByPetId() {
        return contactHoldByPetId;
    }

    public Long getPetId() {
        return petId;
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

    public Long getBreedId() {
        return breedId;
    }

    public Long getCoatTypeId() {
        return coatTypeId;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean isBirthDateEstimated() {
        return birthDateEstimated;
    }
}
