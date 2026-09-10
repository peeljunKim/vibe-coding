/* 기사 처리 실패 예외 */
package com.newsverification.article.domain;

/** 외부 노출 문구와 분리된 내부 실패 */
public class ArticleProcessingException extends RuntimeException {

    private final ArticleProcessingError error;

    public ArticleProcessingException(ArticleProcessingError error) {
        super(error.name());
        this.error = error;
    }

    public ArticleProcessingException(ArticleProcessingError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }

    /** 내부 실패 코드 조회 */
    public ArticleProcessingError error() {
        return error;
    }
}
