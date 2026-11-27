package com.dadayiksun.crm.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** CSV 읽기와 커밋된 더미 CSV끼리의 참조 (적재 전에 깨진 참조를 잡는다, #147). */
class DummyCsvTest {

    @Test
    void 큰따옴표_칸의_쉼표_줄바꿈_따옴표를_읽는다() {
        List<Map<String, String>> rows = DummyCsv.parse("a,memo,c\r\n1,\"발톱, 짧게\",\n2,\"두 줄\n메모 \"\"예민\"\"\",x\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("memo")).isEqualTo("발톱, 짧게");
        assertThat(rows.get(0).get("c")).isEmpty();
        assertThat(rows.get(1).get("memo")).isEqualTo("두 줄\n메모 \"예민\"");
    }

    @Test
    void 칸_수가_다르면_멈춘다() {
        assertThatThrownBy(() -> DummyCsv.parse("a,b\n1\n")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 커밋된_더미_CSV의_참조가_모두_이어진다() {
        Set<String> staff = ids(DummyCsv.read("staff.csv"), "login_id");
        Set<String> owners = ids(DummyCsv.read("owners.csv"), "guardian_id");
        List<Map<String, String>> pets = DummyCsv.read("pets.csv");
        Set<String> petIds = ids(pets, "pet_id");

        assertThat(owners).hasSize(300);
        assertThat(DummyCsv.read("owners.csv")).allMatch(o -> staff.contains(o.get("created_by_login_id")));
        assertThat(pets).allMatch(p -> owners.contains(p.get("guardian_id")) && staff.contains(p.get("created_by_login_id")));
        assertThat(pets).allMatch(p -> p.get("contact_hold_by_pet_id").isEmpty() || petIds.contains(p.get("contact_hold_by_pet_id")));
        for (String file : List.of("vaccinations.csv", "groomings.csv")) {
            assertThat(DummyCsv.read(file)).as(file)
                    .allMatch(v -> petIds.contains(v.get("pet_id")) && staff.contains(v.get("created_by_login_id")));
        }
    }

    @Test
    void 참조를_못_찾으면_조용히_넘기지_않는다() {
        assertThatThrownBy(() -> DummyDataLoader.require(Map.of(), "코커 스패니얼", "품종"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("코커 스패니얼");
    }

    private static Set<String> ids(List<Map<String, String>> rows, String column) {
        Set<String> out = new HashSet<>();
        rows.forEach(r -> out.add(r.get(column)));
        return out;
    }
}
