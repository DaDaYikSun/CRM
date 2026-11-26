package com.dadayiksun.crm.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentStaffAuditor")
public class JpaAuditingConfig {
}
