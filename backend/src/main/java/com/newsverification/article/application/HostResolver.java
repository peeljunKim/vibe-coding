/* 기사 호스트 DNS 조회 경계 */
package com.newsverification.article.application;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** DNS 결과 확인 경계 */
@FunctionalInterface
public interface HostResolver {

    /** 호스트의 전체 IP 조회 */
    List<InetAddress> resolve(String hostname) throws UnknownHostException;
}
