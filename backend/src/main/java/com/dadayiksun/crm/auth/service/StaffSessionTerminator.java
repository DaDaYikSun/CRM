package com.dadayiksun.crm.auth.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import com.dadayiksun.crm.auth.access.AuthSession;

/**
 * 한 직원의 세션을 모두 지운다 (POL-ACC-004 · 010, 11/25 회의 R5, 팀 결정 T14).
 * 다른 PC · 브라우저에 로그인해 둔 것도 다음 요청에서 401 SESSION_EXPIRED가 되어 로그인 화면으로 간다.
 * 세션은 Spring Session JDBC(K19)에 있고, 로그인 때 주인 이름 = 직원 ID로 넣어 둔다 ({@link AuthSession#principalName}).
 * 비활성화 · 역할 변경(#150)과 비밀번호 초기화(초안 33 → #183)가 쓴다.
 */
@Service
public class StaffSessionTerminator {

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    public StaffSessionTerminator(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.sessions = sessions;
    }

    /** 지운 세션 수. 세션 저장소가 없으면(일부 테스트) 0. */
    public int terminateAll(Long staffId) {
        FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
        if (repository == null) {
            return 0;
        }
        List<String> ids = List.copyOf(repository.findByPrincipalName(AuthSession.principalName(staffId)).keySet());
        ids.forEach(repository::deleteById);
        return ids.size();
    }
}
