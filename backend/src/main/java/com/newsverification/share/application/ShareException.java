/* 공유 기능 공개 오류 */
package com.newsverification.share.application;

/** 공유 링크의 접근·대상·상태 오류 */
public class ShareException extends RuntimeException {

    public ShareException(String code) {
        super(code);
    }
}
