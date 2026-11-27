package com.dadayiksun.crm.cycle;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 매일 0시 누락 판정 작업(K20)을 켠다. 다른 업무에 예약 작업이 생겨도 이 설정 하나로 함께 돈다. */
@Configuration
@EnableScheduling
public class CycleSchedulingConfig {
}
