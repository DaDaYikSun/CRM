package com.dadayiksun.crm.customer.domain;

import java.time.LocalDate;

import com.dadayiksun.crm.common.audit.AuditEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 반려동물 (DB 설계 4-3). 묶음 대표 (DB 설계 머리말 애그리거트, K16 ④). 보호자와는 ID로만 잇는다.
 *
 * <p>#145는 등록에 쓰는 칸만 옮겼다. {@code species}는 DB 기본값 'DOG'(ENUM에 'DOG'만 있음)로 들어간다
 * (POL-REG-003, NFR-EXT-002). {@code status}는 DB 기본값 'ACTIVE'. 선택 항목 · 무지개다리 · 미용 주기 칸은
 * 그 이슈(초안 19 · 초안 21 · #144)가 더한다.
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
