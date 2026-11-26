package com.dadayiksun.crm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.dadayiksun.crm.common.time.KstClock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CrmApplication {

    public static void main(String[] args) {
        // 시간은 한국 시간 (CLAUDE.md 7번, DB 설계 1장). JVM 기본 시간대를 서버 위치와 상관없이 고정한다
        TimeZone.setDefault(TimeZone.getTimeZone(KstClock.ZONE));
        SpringApplication.run(CrmApplication.class, args);
    }
}
