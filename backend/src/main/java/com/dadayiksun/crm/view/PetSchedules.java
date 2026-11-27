package com.dadayiksun.crm.view;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.customer.dto.OptionResponses.VaccineTypeOption;
import com.dadayiksun.crm.customer.service.OptionQueryService;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.service.CycleQueryService;

/**
 * 계약 C4 {@link PetScheduleReader}의 실제 구현 (#148, PR #220 리뷰): cycle의 진행 중 주기에 접종 종류 이름(C5 선택지)을 붙인다.
 * 누락은 저장된 missed_at만 본다 (K17 ②, T13). 다시 계산하지 않는다.
 */
@Component
public class PetSchedules implements PetScheduleReader {

    private final CycleQueryService cycle;
    private final OptionQueryService options;

    public PetSchedules(CycleQueryService cycle, OptionQueryService options) {
        this.cycle = cycle;
        this.options = options;
    }

    @Override
    public List<ScheduleItem> openSchedules(long petId) {
        Map<Long, VaccineTypeOption> types = options.vaccineTypes().stream()
                .collect(Collectors.toMap(VaccineTypeOption::vaccineTypeId, Function.identity()));
        return cycle.findOpenSchedules(petId).stream()
                .sorted(Comparator.comparing(Schedule::dueDate))
                .map(s -> new ScheduleItem(s.scheduleId(), s.serviceType().name(), vaccineType(types, s.vaccineTypeId()),
                        s.dueDate(), s.missedAt() != null))
                .toList();
    }

    /** 쓰지 않게 된 종류(선택지에 없음)는 null: 화면은 '접종'으로 보인다 */
    private static VaccineTypeItem vaccineType(Map<Long, VaccineTypeOption> types, Long id) {
        VaccineTypeOption t = id == null ? null : types.get(id);
        return t == null ? null : new VaccineTypeItem(t.vaccineTypeId(), t.name(), t.isMajor());
    }
}
