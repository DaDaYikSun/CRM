package com.dadayiksun.crm.common.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시드 계정 비밀번호. 저장소에 평문으로 두지 않고 환경 변수로 받는다 (DB 설계 8장, NFR-SEC-001).
 */
@ConfigurationProperties(prefix = "crm.seed")
public record SeedProperties(String adminPassword, String deskPassword, String groomerPassword) {
}
