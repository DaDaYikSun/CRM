package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.customer.dto.GuardianDetail;
import com.dadayiksun.crm.customer.service.GuardianDetailReader;

/**
 * 보호자 상세 조립 (API 설계 4-1, R3): customer(보호자 · 같은 집 아이)와 cycle(다음 예정)을 합친다.
 * customer가 cycle을 부르지 않게 이 자리에서만 두 주인의 Reader를 합친다 (FR-CUS-008, K17 ②).
 * 다른 도메인 값(예정)을 읽는 곳이라 PR에 적고 cycle 주인을 리뷰어로 넣는다.
 */
@Component
public class GuardianDetailAssembler {

    private final GuardianDetailReader customer;
    private final NextScheduleReader cycle;

    public GuardianDetailAssembler(GuardianDetailReader customer, NextScheduleReader cycle) {
        this.customer = customer;
        this.cycle = cycle;
    }

    public Response assemble(Long guardianId) {
        GuardianDetail detail = customer.read(guardianId);
        Map<Long, NextScheduleReader.NextSchedule> next = cycle
                .nextSchedules(detail.pets().stream().map(GuardianDetail.PetCard::petId).toList());
        List<PetCard> pets = detail.pets().stream().map(p -> new PetCard(p, next.get(p.petId()))).toList();
        return new Response(detail.guardian(), pets);
    }

    public record Response(GuardianDetail.GuardianInfo guardian, List<PetCard> pets) {
    }

    /** API 설계 3-3 pets[] 모양. */
    public record PetCard(Long petId, String name, String status, String breedName, String coatTypeName,
            LocalDate birthDate, boolean birthDateEstimated, LocalDate contactHoldUntil,
            NextScheduleReader.NextSchedule nextSchedule) {

        PetCard(GuardianDetail.PetCard p, NextScheduleReader.NextSchedule next) {
            this(p.petId(), p.name(), p.status(), p.breedName(), p.coatTypeName(), p.birthDate(),
                    p.birthDateEstimated(), p.contactHoldUntil(), next);
        }
    }
}
