package com.dadayiksun.crm.visit.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.visit.domain.VisitRecordedEvent;
import com.dadayiksun.crm.visit.dto.RecordVisitRequest;
import com.dadayiksun.crm.visit.dto.VisitLine;
import com.dadayiksun.crm.visit.repository.VisitRepository;

/**
 * 접종 · 미용 방문 기록 (C8, FR-VIS-001 · 002, API 설계 3-5).
 *
 * <ul>
 *   <li>visit 테이블에만 쓰고 '방문 기록됨' 이벤트만 낸다. cycle · contact를 import하지 않는다 (K13, K17 ①).
 *       듣는 쪽이 없어도 저장은 된다</li>
 *   <li>미용사가 접종을 보내면 403 (T3, POL-ACC-011). 무지개다리 아이면 409 (POL-END-002)</li>
 *   <li>방문 날짜가 없으면 오늘(한국 시간). 오늘보다 뒤면 400 (#148 결정), 생년월일보다 앞이면 400</li>
 *   <li>같은 아이 · 같은 날 · 같은 서비스(접종이면 같은 종류)를 또 넣으면 400 (#148 결정). 같은 날 다른 종류는 된다</li>
 *   <li>기록한 직원 · 시간은 서버가 붙인다 (NFR-DATA-002, POL-DATA-008). 같은 요청 두 번은 IdempotencyFilter(K15)가 막는다</li>
 * </ul>
 */
@Service
public class VisitService {

    static final int MEMO_MAX = 200; // visit.memo VARCHAR(200)과 같다 (POL-DATA-010)
    private static final Set<String> SERVICE_TYPES = Set.of("VACCINE", "GROOMING");

    private final VisitRepository visits;
    private final ApplicationEventPublisher events;
    private final KstClock clock;

    public VisitService(VisitRepository visits, ApplicationEventPublisher events, KstClock clock) {
        this.visits = visits;
        this.events = events;
        this.clock = clock;
    }

    /** 저장한 visit_id. 부르는 쪽(조립 자리) 트랜잭션에 함께 들어간다 (응답 DTO도 그 안에서, #193) */
    @Transactional
    public long record(long petId, RecordVisitRequest req, CurrentStaff staff) {
        String service = req.serviceType();
        if ("VACCINE".equals(service)) {
            staff.require(Role.ADMIN, Role.DESK); // 권한 먼저: 미용사에게는 아이가 있는지도 알려 주지 않는다 (PR #220 리뷰)
        }
        VisitRepository.Pet pet = visits.pet(petId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();
        LocalDate visitDate = req.visitDate() == null ? today : req.visitDate();
        String memo = req.memo() == null || req.memo().isBlank() ? null : req.memo().strip();

        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (service == null || service.isBlank()) {
            errors.add(new ErrorResponse.FieldError("serviceType", ErrorCode.required("구분")));
        } else if (!SERVICE_TYPES.contains(service)) {
            errors.add(invalid("serviceType"));
        } else if ("VACCINE".equals(service)) {
            if (req.vaccineTypeId() == null) {
                errors.add(new ErrorResponse.FieldError("vaccineTypeId", ErrorCode.required("접종 종류")));
            } else if (!visits.isSelectableVaccineType(req.vaccineTypeId())) {
                errors.add(invalid("vaccineTypeId"));
            }
        } else if (req.vaccineTypeId() != null) {
            errors.add(invalid("vaccineTypeId")); // 미용에는 접종 종류가 없다 (DB CHECK)
        }
        if (visitDate.isAfter(today) || visitDate.isBefore(pet.birthDate())) {
            // 미래 날짜, 생년월일보다 이른 날짜 (PR #220 리뷰: 접종 계산이 500으로 끝나던 경우). 문구는 공통 문구 (API 설계 7장 14번 · 18번)
            errors.add(invalid("visitDate"));
        }
        if (memo != null && memo.length() > MEMO_MAX) {
            errors.add(invalid("memo"));
        }
        throwIfAny(errors);

        if ("RAINBOW".equals(pet.status())) {
            throw new ApiException(ErrorCode.PET_RAINBOW);
        }
        if (visits.existsSameDay(petId, service, req.vaccineTypeId(), visitDate)) {
            throwIfAny(List.of(invalid("visitDate")));
        }

        long visitId = visits.insert(new VisitRepository.Row(petId, service, req.vaccineTypeId(), visitDate, memo,
                staff.staffId(), now));
        events.publishEvent(new VisitRecordedEvent(visitId));
        return visitId;
    }

    /** 아이의 방문 줄 (통합 타임라인 C9). serviceTypes는 VACCINE · GROOMING 중에서 */
    @Transactional(readOnly = true)
    public List<VisitLine> lines(long petId, Collection<String> serviceTypes) {
        return visits.lines(petId, serviceTypes).stream()
                .map(r -> new VisitLine(r.visitId(), r.serviceType(), r.visitDate(),
                        "VACCINE".equals(r.serviceType()) ? r.vaccineTypeName() : "미용", r.memo(), r.staffName(),
                        r.createdAt().atZone(KstClock.ZONE).toOffsetDateTime(), r.edited()))
                .toList();
    }

    private static ErrorResponse.FieldError invalid(String field) {
        return new ErrorResponse.FieldError(field, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
    }

    private static void throwIfAny(List<ErrorResponse.FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, errors.get(0).message(), List.copyOf(errors));
        }
    }
}
