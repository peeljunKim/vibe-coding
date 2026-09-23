/* Spring Session 계정 만료 Adapter */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountSessionInvalidator;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** 사용자 ID Principal의 전체 Redis Session 삭제 */
public class SpringSessionAccountSessionInvalidator implements AccountSessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SpringSessionAccountSessionInvalidator(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    /** 사용자 계정의 전체 Session 삭제 */
    @Override
    public void invalidateAll(long userId) {
        sessionRepository.findByPrincipalName(Long.toString(userId))
                .keySet()
                .forEach(sessionRepository::deleteById);
    }
}
