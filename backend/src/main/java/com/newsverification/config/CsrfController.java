/* Frontend CSRF Cookie 발급 API */
package com.newsverification.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비회원 Frontend의 CSRF Token 초기화 */
@RestController
@RequestMapping("/api/csrf")
public class CsrfController {

    /** CSRF Cookie 생성과 캐시 차단 */
    @GetMapping
    public ResponseEntity<Void> issue(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        csrfToken.getToken();
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
