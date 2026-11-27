package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetRegisteredEvent;
import com.dadayiksun.crm.customer.dto.GuardianInput;
import com.dadayiksun.crm.customer.dto.PetInput;
import com.dadayiksun.crm.customer.dto.RegisterGuardianRequest;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * '아이 등록됨' 이벤트를 cycle이 같은 트랜잭션에서 받는다 (K13, API 설계 4-1, FR-CUS-001 확인 방법).
 *
 * <p>Fake 사용: C4 {@code createInitialSchedules(petId)}(#144) 대신 테스트 안의 {@link FakeCycleListener}.
 * #144가 머지되면 실제 리스너로 schedule 행이 생기는지 DB 테스트로 바꾼다.
 */
@SpringJUnitConfig(PetRegisteredEventTransactionTest.Config.class)
class PetRegisteredEventTransactionTest {

    @Autowired
    private PetRegistrationService service;
    @Autowired
    private FakeCycleListener listener;
    @Autowired
    private RecordingTransactionManager tx;

    @BeforeEach
    void reset() {
        listener.received.clear();
        listener.failNext = false;
        tx.commits = 0;
        tx.rollbacks = 0;
    }

    private static RegisterGuardianRequest request() {
        return new RegisterGuardianRequest(new GuardianInput("김보호", "010-0000-0000", true, false),
                new PetInput("보리", LocalDate.of(2023, 5, 2), null, 1L, 3L));
    }

    @Test
    void 등록하면_리스너가_같은_트랜잭션_안에서_petId를_받는다() {
        var result = service.registerGuardian(request());

        assertThat(listener.received).hasSize(1);
        FakeCycleListener.Received r = listener.received.get(0);
        assertThat(r.petId()).isEqualTo(result.petId());
        assertThat(r.inTransaction()).isTrue();
        assertThat(r.transactionName()).endsWith("PetRegistrationService.registerGuardian");
        assertThat(tx.commits).isEqualTo(1);
    }

    @Test
    void 기존_보호자에_아이_추가도_같은_트랜잭션에서_이벤트() {
        service.addPet(7L, request().pet());

        assertThat(listener.received).hasSize(1);
        assertThat(listener.received.get(0).transactionName()).endsWith("PetRegistrationService.addPet");
    }

    @Test
    void 리스너가_실패하면_등록도_되돌린다() {
        listener.failNext = true;

        assertThatThrownBy(() -> service.registerGuardian(request())).isInstanceOf(IllegalStateException.class);
        assertThat(tx.rollbacks).isEqualTo(1);
        assertThat(tx.commits).isZero();
    }

    /** C4 Fake: cycle의 '아이 등록됨' 리스너 대신. 받은 petId와 그때의 트랜잭션을 적어 둔다. */
    static class FakeCycleListener {

        record Received(Long petId, boolean inTransaction, String transactionName) {
        }

        final List<Received> received = new ArrayList<>();
        boolean failNext;

        @EventListener
        public void on(PetRegisteredEvent event) {
            received.add(new Received(event.petId(), TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.getCurrentTransactionName()));
            if (failNext) {
                throw new IllegalStateException("첫 예정 일정 만들기 실패 (Fake)");
            }
        }
    }

    /** DB 없이 트랜잭션 시작 · 커밋 · 되돌림만 센다. */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        FakeCycleListener fakeCycleListener() {
            return new FakeCycleListener();
        }

        @Bean
        KstClock kstClock() {
            return new KstClock();
        }

        @Bean
        PetRegistrationService petRegistrationService(ApplicationEventPublisher events, KstClock clock) {
            GuardianRepository guardians = mock(GuardianRepository.class);
            PetRepository pets = mock(PetRepository.class);
            SelectableOptionChecker options = mock(SelectableOptionChecker.class);
            when(guardians.save(any(Guardian.class))).thenAnswer(inv -> {
                Guardian g = inv.getArgument(0);
                ReflectionTestUtils.setField(g, "guardianId", 7L);
                return g;
            });
            when(guardians.existsById(7L)).thenReturn(true);
            when(pets.save(any(Pet.class))).thenAnswer(inv -> {
                Pet p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "petId", 12L);
                return p;
            });
            when(options.isSelectableBreed(anyLong())).thenReturn(true);
            when(options.isSelectableCoatType(anyLong())).thenReturn(true);
            return new PetRegistrationService(guardians, pets, options, new BirthDateResolver(clock), clock, events);
        }
    }
}
