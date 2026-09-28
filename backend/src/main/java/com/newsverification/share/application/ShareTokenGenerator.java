/* 공유 토큰 생성 경계 */
package com.newsverification.share.application;

/** 원문 저장 없는 임의 공유 토큰 생성 */
public interface ShareTokenGenerator {

    String generate();
}
