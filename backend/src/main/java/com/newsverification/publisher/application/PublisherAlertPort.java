/* 언론사 자동 중단 관리자 알림 Port */
package com.newsverification.publisher.application;

/** 자동 중단 알림 전달 경계 */
public interface PublisherAlertPort {

    void notifyAutomaticallyPaused(String publisherName);
}
