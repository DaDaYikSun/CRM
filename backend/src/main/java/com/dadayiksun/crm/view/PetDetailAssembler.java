package com.dadayiksun.crm.view;

import java.util.List;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.customer.dto.PetDetail;
import com.dadayiksun.crm.customer.service.PetDetailReader;

/**
 * 아이 상세 조립 (API 설계 4-1, C9): customer(아이 · 보호자 · 같은 집)와 cycle(진행 중 주기)을 합친다.
 * customer가 cycle을 부르지 않게 이 자리에서만 두 Reader를 합친다 (FR-CUS-003, K13).
 * 다른 도메인 값(cycle의 schedule)을 읽는 곳이라 PR에 적고 cycle 주인을 리뷰어로 넣는다.
 */
@Component
public class PetDetailAssembler {

    private final PetDetailReader customer;
    private final PetScheduleReader cycle;

    public PetDetailAssembler(PetDetailReader customer, PetScheduleReader cycle) {
        this.customer = customer;
        this.cycle = cycle;
    }

    public Response assemble(Long petId) {
        PetDetail detail = customer.read(petId);
        return new Response(detail.pet(), detail.guardian(), detail.siblings(), cycle.openSchedules(petId));
    }

    /** API 설계 3-3 {@code GET /api/pets/{id}} 모양. */
    public record Response(PetDetail.PetBlock pet, PetDetail.GuardianBlock guardian, List<PetDetail.Sibling> siblings,
            List<PetScheduleReader.ScheduleItem> schedules) {
    }
}
