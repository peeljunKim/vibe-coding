/* Local 건강 근거 링크 상태 Mock */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthEvidenceLinkChecker;

import java.net.URI;
import java.util.Objects;

/** 외부 HTTP 호출 없는 정상 근거 응답 */
public class MockHealthEvidenceLinkChecker implements HealthEvidenceLinkChecker {

    /** 입력 근거 주소의 정상 상태 반환 */
    @Override
    public Status check(URI sourceUrl) {
        Objects.requireNonNull(sourceUrl);
        return Status.AVAILABLE;
    }
}
