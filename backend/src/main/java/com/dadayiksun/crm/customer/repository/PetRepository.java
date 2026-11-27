package com.dadayiksun.crm.customer.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dadayiksun.crm.customer.domain.Pet;

/** 반려동물 묶음 대표의 Repository (K16 ④). */
public interface PetRepository extends JpaRepository<Pet, Long> {

    /** 보호자별 아이 수 (같은 번호 경고의 '아이 {N}마리', 정책 4-2). 결과 행: [guardianId, count] */
    @Query("select p.guardianId, count(p) from Pet p where p.guardianId in :guardianIds group by p.guardianId")
    List<Object[]> countByGuardianIds(@Param("guardianIds") Collection<Long> guardianIds);

    /** 같은 집 아이 (S-06 카드 · 되돌리기). */
    List<Pet> findByGuardianIdOrderByPetIdAsc(Long guardianId);
}
