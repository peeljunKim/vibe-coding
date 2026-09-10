/* 시스템 DNS 기반 기사 호스트 조회 */
package com.newsverification.article.infrastructure;

import com.newsverification.article.application.HostResolver;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** 운영 DNS 조회 구현 */
@Component
public final class SystemHostResolver implements HostResolver {

    /** 시스템 Resolver의 전체 IP 조회 */
    @Override
    public List<InetAddress> resolve(String hostname) throws UnknownHostException {
        return List.of(InetAddress.getAllByName(hostname));
    }
}
