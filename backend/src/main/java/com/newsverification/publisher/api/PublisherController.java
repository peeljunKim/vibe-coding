/* 지원 언론사 공개 API */
package com.newsverification.publisher.api;

import com.newsverification.publisher.application.PublisherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 지원 언론사 조회 Endpoint */
@RestController
@RequestMapping("/api/publishers")
public class PublisherController {

    private final PublisherService publisherService;

    public PublisherController(PublisherService publisherService) {
        this.publisherService = publisherService;
    }

    /** 지원 언론사 목록 조회 */
    @GetMapping
    public List<PublisherResponse> getSupportedPublishers() {
        return publisherService.findSupportedPublishers().stream()
                .map(PublisherResponse::from)
                .toList();
    }
}
