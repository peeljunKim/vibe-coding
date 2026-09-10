-- 지원 언론사 분류 컬럼 추가
-- 이유: 공개 지원 언론사 API의 필수 분류 제공
-- 내용: news_publishers.category와 허용 분류 CHECK 제약 추가
-- 호환성: 기존 Backend는 언론사 Table을 사용하지 않으며 신규 Backend 배포 전 적용 필요
-- Rollback: 신규 Backend 배포 전이며 분류 데이터가 불필요할 때 CHECK 제약과 컬럼 순서로 제거

ALTER TABLE news_publishers
    ADD COLUMN category VARCHAR(30) NOT NULL COMMENT '언론사 분류' AFTER name,
    ADD CONSTRAINT ck_news_publishers_category CHECK (
        category IN (
            'NEWS_AGENCY',
            'BROADCAST_NEWS',
            'GENERAL_NEWSPAPER',
            'BUSINESS_NEWSPAPER',
            'HEALTH_MEDICAL'
        )
    );
