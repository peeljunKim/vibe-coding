/* 언론사별 최근 기사 추출 시도 집계 Port */
package com.newsverification.publisher.application;

/** 자동 중단 임계치 판정 저장 경계 */
public interface PublisherExtractionAttemptStore {

    boolean record(String hostname, boolean failed);
}
