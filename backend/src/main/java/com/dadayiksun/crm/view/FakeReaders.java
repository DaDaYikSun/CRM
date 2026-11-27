package com.dadayiksun.crm.view;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FAKE (#149): 주인 도메인 구현이 생기면 이 파일을 지운다.
 * 조회 Fake라 빈 목록만 준다. 실제 Bean이 있으면 쓰이지 않지만(ConditionalOnMissingBean) 파일이 남으면 헷갈리므로 지운다.
 * 켜질 때마다 WARN 로그를 남긴다.
 * <ul>
 *   <li>{@link PetScheduleReader}: cycle #144 (PR #202) 머지 뒤 {@code CycleQueryService}를 감싼 구현으로 바꾼다</li>
 *   <li>{@link VisitTimelineReader}: 실제 구현 {@link VisitTimeline}으로 바꿈 (#148)</li>
 *   <li>{@link ContactTimelineReader}: 실제 구현 {@link ContactTimeline}으로 바꿈 (#153)</li>
 * </ul>
 */
@Configuration
class FakeReaders {

    private static final Logger log = LoggerFactory.getLogger(FakeReaders.class);

    @Bean
    @ConditionalOnMissingBean(PetScheduleReader.class)
    PetScheduleReader fakePetScheduleReader() {
        warn("PetScheduleReader(C4, #144)");
        return petId -> List.of();
    }

    private static void warn(String what) {
        log.warn("FAKE 사용 중: {} - 주인 도메인 구현이 생기면 view/FakeReaders를 지운다 (#149)", what);
    }
}
