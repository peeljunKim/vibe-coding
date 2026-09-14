-- 지원 언론사 초기 기준 데이터
START TRANSACTION;

INSERT INTO news_publishers (name, category, status, status_reason)
VALUES
    ('연합뉴스', 'NEWS_AGENCY', 'ACTIVE', NULL),
    ('뉴시스', 'NEWS_AGENCY', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('KBS', 'BROADCAST_NEWS', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('MBC', 'BROADCAST_NEWS', 'ACTIVE', NULL),
    ('SBS', 'BROADCAST_NEWS', 'ACTIVE', NULL),
    ('YTN', 'BROADCAST_NEWS', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('JTBC', 'BROADCAST_NEWS', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('조선일보', 'GENERAL_NEWSPAPER', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('중앙일보', 'GENERAL_NEWSPAPER', 'ACTIVE', NULL),
    ('동아일보', 'GENERAL_NEWSPAPER', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('한겨레', 'GENERAL_NEWSPAPER', 'ACTIVE', NULL),
    ('경향신문', 'GENERAL_NEWSPAPER', 'ACTIVE', NULL),
    ('한국일보', 'GENERAL_NEWSPAPER', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('국민일보', 'GENERAL_NEWSPAPER', 'ACTIVE', NULL),
    ('서울신문', 'GENERAL_NEWSPAPER', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('매일경제', 'BUSINESS_NEWSPAPER', 'ACTIVE', NULL),
    ('한국경제', 'BUSINESS_NEWSPAPER', 'ACTIVE', NULL),
    ('헬스조선', 'HEALTH_MEDICAL', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('코메디닷컴', 'HEALTH_MEDICAL', 'CANDIDATE', '초기 추출 보완 및 재시험 대기'),
    ('메디칼타임즈', 'HEALTH_MEDICAL', 'CANDIDATE', '초기 추출 보완 및 재시험 대기');

INSERT INTO news_publisher_domains (publisher_id, hostname, status)
SELECT publisher.id, seed.hostname, seed.status
FROM (
    SELECT '연합뉴스' AS publisher_name, 'www.yna.co.kr' AS hostname, 'ACTIVE' AS status
    UNION ALL SELECT '연합뉴스', 'yna.co.kr', 'ACTIVE'
    UNION ALL SELECT '뉴시스', 'www.newsis.com', 'PAUSED'
    UNION ALL SELECT '뉴시스', 'newsis.com', 'PAUSED'
    UNION ALL SELECT 'KBS', 'news.kbs.co.kr', 'PAUSED'
    UNION ALL SELECT 'MBC', 'imnews.imbc.com', 'ACTIVE'
    UNION ALL SELECT 'SBS', 'news.sbs.co.kr', 'ACTIVE'
    UNION ALL SELECT 'YTN', 'www.ytn.co.kr', 'PAUSED'
    UNION ALL SELECT 'YTN', 'ytn.co.kr', 'PAUSED'
    UNION ALL SELECT 'JTBC', 'news.jtbc.co.kr', 'PAUSED'
    UNION ALL SELECT '조선일보', 'www.chosun.com', 'PAUSED'
    UNION ALL SELECT '조선일보', 'chosun.com', 'PAUSED'
    UNION ALL SELECT '중앙일보', 'www.joongang.co.kr', 'ACTIVE'
    UNION ALL SELECT '중앙일보', 'joongang.co.kr', 'ACTIVE'
    UNION ALL SELECT '동아일보', 'www.donga.com', 'PAUSED'
    UNION ALL SELECT '동아일보', 'donga.com', 'PAUSED'
    UNION ALL SELECT '한겨레', 'www.hani.co.kr', 'ACTIVE'
    UNION ALL SELECT '한겨레', 'hani.co.kr', 'ACTIVE'
    UNION ALL SELECT '경향신문', 'www.khan.co.kr', 'ACTIVE'
    UNION ALL SELECT '경향신문', 'khan.co.kr', 'ACTIVE'
    UNION ALL SELECT '한국일보', 'www.hankookilbo.com', 'PAUSED'
    UNION ALL SELECT '한국일보', 'hankookilbo.com', 'PAUSED'
    UNION ALL SELECT '국민일보', 'www.kmib.co.kr', 'ACTIVE'
    UNION ALL SELECT '국민일보', 'kmib.co.kr', 'ACTIVE'
    UNION ALL SELECT '서울신문', 'www.seoul.co.kr', 'PAUSED'
    UNION ALL SELECT '서울신문', 'seoul.co.kr', 'PAUSED'
    UNION ALL SELECT '매일경제', 'www.mk.co.kr', 'ACTIVE'
    UNION ALL SELECT '매일경제', 'mk.co.kr', 'ACTIVE'
    UNION ALL SELECT '한국경제', 'www.hankyung.com', 'ACTIVE'
    UNION ALL SELECT '한국경제', 'hankyung.com', 'ACTIVE'
    UNION ALL SELECT '헬스조선', 'health.chosun.com', 'PAUSED'
    UNION ALL SELECT '코메디닷컴', 'kormedi.com', 'PAUSED'
    UNION ALL SELECT '코메디닷컴', 'www.kormedi.com', 'PAUSED'
    UNION ALL SELECT '메디칼타임즈', 'www.medicaltimes.com', 'PAUSED'
    UNION ALL SELECT '메디칼타임즈', 'medicaltimes.com', 'PAUSED'
) seed
JOIN news_publishers publisher ON publisher.name = seed.publisher_name;

COMMIT;
