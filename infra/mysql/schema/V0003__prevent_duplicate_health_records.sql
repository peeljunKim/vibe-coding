-- 건강 분석 결과의 동시 중복 저장 방지
-- 이유: 조회 후 저장 사이의 경쟁 요청에서 같은 완료 결과가 둘 이상 생성될 수 있음
-- 내용: 사용자·정규화 URL·분석 시각의 복합 UNIQUE 제약 추가
-- 호환성: 기존 애플리케이션의 조회와 저장 가능, 기존 중복 행이 있으면 적용 실패
-- Rollback: ALTER TABLE health_analysis_records DROP INDEX uk_health_records_user_url_analyzed;

ALTER TABLE health_analysis_records
    ADD CONSTRAINT uk_health_records_user_url_analyzed
        UNIQUE (user_id, normalized_url_digest, analyzed_at);
