package com.dadayiksun.crm.customer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dadayiksun.crm.customer.domain.Guardian;

/** 보호자 묶음 대표의 Repository (K16 ④). */
public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    /** 같은 전화번호 보호자 (숫자만 비교, API 설계 1-4). 같은 번호가 여럿일 수 있다 (POL-REG-005). */
    List<Guardian> findByPhoneOrderByGuardianIdAsc(String phone);
}
