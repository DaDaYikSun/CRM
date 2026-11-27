package com.dadayiksun.crm.target.domain;

import static com.dadayiksun.crm.target.domain.TargetFixtures.TODAY;
import static com.dadayiksun.crm.target.domain.TargetFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.target.dto.TargetLineResponse;

/** 보호자 묶음 · 펼치기 · 정렬 (FR-TGT-002 확인 방법, POL-TGT-001 · 010 · 011). */
class TargetLineAssemblerTest {

    @Test
    void 같은_보호자의_아이_2마리는_한_줄로_묶인다() {
        List<TargetLineResponse> lines = TargetLineAssembler.assemble(List.of(
                row(1).pet(1, "보리").guardian(7, "김보호").due(TODAY.minusDays(2)).build(),
                row(2).pet(2, "콩이").guardian(7, "김보호").grooming().due(TODAY).build()));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).pets()).extracting(TargetLineResponse.Pet::name).containsExactly("보리", "콩이");
    }

    @Test
    void 접종이_여럿이면_가장_가까운_하나만_보이고_나머지는_펼치기로() {
        List<TargetLineResponse> lines = TargetLineAssembler.assemble(List.of(
                row(1).vaccine("광견병", 4).due(TODAY).build(),
                row(2).vaccine("종합백신", 1).due(TODAY.minusDays(3)).build(),
                row(3).grooming().due(TODAY.minusDays(1)).build(),
                row(4).vaccine("심장사상충 예방약", 6).due(TODAY.minusDays(3)).build()));

        TargetLineResponse.Pet pet = lines.get(0).pets().get(0);
        // 같은 예정일이면 접종 종류 순서(sort_order)가 앞인 것
        assertThat(pet.items()).extracting(TargetLineResponse.Item::scheduleId).containsExactly(2L, 3L);
        assertThat(pet.moreItems()).extracting(TargetLineResponse.Item::scheduleId).containsExactly(4L, 1L);
    }

    @Test
    void 줄은_예정일이_오래_지난_순이고_오늘_처리한_줄은_아래로() {
        List<TargetLineResponse> lines = TargetLineAssembler.assemble(List.of(
                row(1).guardian(1, "가").pet(1, "a").due(TODAY).build(),
                row(2).guardian(2, "나").pet(2, "b").due(TODAY.minusDays(10)).processedToday().build(),
                row(3).guardian(3, "다").pet(3, "c").due(TODAY.minusDays(5)).build()));

        assertThat(lines).extracting(TargetLineResponse::guardianId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void 누락_표시는_저장된_판정만_따른다() {
        List<TargetLineResponse> lines = TargetLineAssembler.assemble(List.of(
                row(1).due(TODAY.minusDays(20)).missedAt(TODAY.minusDays(5)).build()));
        assertThat(lines.get(0).pets().get(0).items().get(0).missed()).isTrue();
    }

    @Test
    void 목록에는_전화번호_뒤_4자리만_있다() {
        TargetLineResponse line = TargetLineAssembler.assemble(List.of(row(1).build())).get(0);
        assertThat(line.phoneLast4()).hasSize(4);
    }
}
