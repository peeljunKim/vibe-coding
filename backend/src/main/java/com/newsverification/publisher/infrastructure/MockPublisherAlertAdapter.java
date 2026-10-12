/* Local 언론사 중단 알림 Mock */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.application.PublisherAlertPort;

/** 실제 SMTP 호출 없는 언론사 운영 알림 Adapter */
public class MockPublisherAlertAdapter implements PublisherAlertPort {

    @Override
    public void notifyAutomaticallyPaused(String publisherName) {
    }
}
