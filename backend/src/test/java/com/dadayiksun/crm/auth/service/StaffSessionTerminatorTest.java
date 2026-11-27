package com.dadayiksun.crm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

/** 비활성화하면 그 직원이 로그인해 둔 모든 기기에서 로그아웃 (POL-ACC-004, 11/25 회의 R5). */
class StaffSessionTerminatorTest {

    @Test
    @SuppressWarnings("unchecked")
    void 직원_ID로_찾은_세션을_모두_지운다() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        when(repository.findByPrincipalName("7")).thenReturn(Map.of("s1", new MapSession("s1"), "s2", new MapSession("s2")));
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn((FindByIndexNameSessionRepository) repository);

        int removed = new StaffSessionTerminator(provider).terminateAll(7L);

        assertThat(removed).isEqualTo(2);
        verify(repository).deleteById("s1");
        verify(repository).deleteById("s2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 세션_저장소가_없으면_아무것도_안_한다() {
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider = mock(ObjectProvider.class);
        assertThat(new StaffSessionTerminator(provider).terminateAll(7L)).isZero();
    }
}
