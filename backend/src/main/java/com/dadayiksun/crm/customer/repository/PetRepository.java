package com.dadayiksun.crm.customer.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dadayiksun.crm.customer.domain.Pet;

import jakarta.persistence.LockModeType;

/** 반려동물 묶음 대표의 Repository (K16 ④). */
public interface PetRepository extends JpaRepository<Pet, Long> {

    /** 보호자별 아이 수 (같은 번호 경고의 '아이 {N}마리', 정책 4-2). 결과 행: [guardianId, count] */
    @Query("select p.guardianId, count(p) from Pet p where p.guardianId in :guardianIds group by p.guardianId")
    List<Object[]> countByGuardianIds(@Param("guardianIds") Collection<Long> guardianIds);

    /** 같은 집 아이 (S-06 카드). */
    List<Pet> findByGuardianIdOrderByPetIdAsc(Long guardianId);

    @Query("select p.guardianId from Pet p where p.petId = :petId")
    Optional<Long> findGuardianId(@Param("petId") Long petId);

    /** 같은 집 아이를 잠그고 읽는다. 같은 집의 되돌리기를 한 번에 하나씩 처리해 '남은 아이' 판단이 어긋나지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pet p where p.guardianId = :guardianId order by p.petId")
    List<Pet> lockByGuardianId(@Param("guardianId") Long guardianId);
}
