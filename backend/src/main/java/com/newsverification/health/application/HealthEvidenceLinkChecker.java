/* 건강 근거 링크 상태 확인 Port */
package com.newsverification.health.application;

import java.net.URI;

/** 외부 근거 주소의 현재 접근 상태 경계 */
public interface HealthEvidenceLinkChecker {

    /** 허용 출처 근거 주소의 상태 확인 */
    Status check(URI sourceUrl);

    /** 근거 주소 확인 결과 */
    enum Status {
        AVAILABLE,
        MISSING,
        TEMPORARY_FAILURE
    }
}
