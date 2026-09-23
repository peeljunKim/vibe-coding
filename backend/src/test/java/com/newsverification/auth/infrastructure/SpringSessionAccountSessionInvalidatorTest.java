/* 계정 Session 일괄 만료 검증 */
package com.newsverification.auth.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 사용자 ID에 연결된 전체 Session 삭제 검증 */
class SpringSessionAccountSessionInvalidatorTest {

    /** 사용자 ID Principal의 전체 Session 삭제 */
    @Test
    @SuppressWarnings("unchecked")
    void invalidatesEverySessionForUserId() {
        FindByIndexNameSessionRepository<Session> repository =
                mock(FindByIndexNameSessionRepository.class);
        when(repository.findByPrincipalName("42"))
                .thenReturn(Map.of("session-a", mock(Session.class), "session-b", mock(Session.class)));
        var invalidator = new SpringSessionAccountSessionInvalidator(repository);

        invalidator.invalidateAll(42L);

        verify(repository).deleteById("session-a");
        verify(repository).deleteById("session-b");
    }
}
