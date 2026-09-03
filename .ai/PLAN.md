<!-- 현재 목표와 검증 상태 -->
# 현재 작업 계획

## Goal

초기 Domain Schema SQL의 Local 전용 전환

## Scope

- `.gitignore`, `infra/mysql/schema/V0001__create_initial_domain_schema.sql`
- 초기 Schema 추적 정책을 설명하는 Harness와 DB 문서
- Infrastructure 검증 메시지

## Plan

1. 초기 Domain Schema SQL ignore 규칙 추가
2. Git 인덱스 비추적 상태 확인
3. 초기 SQL과 이후 변경 SQL의 관리 경계 문서화
4. 관련 Harness 정적 검증
5. Self Review와 Diff Review

## Verification Status

- 초기 Domain Schema SQL Git 비추적: PASS, Local 파일 보존과 ignore 규칙 확인
- 초기 SQL과 이후 변경 이력 정책 정합성: PASS, V0001 Local 전용과 V0002 이후 Git 관리 경계 명시
- Infrastructure 정적 검증: PASS, Local 초기 SQL 구조와 Compose 구성 확인
- Docs/Harness 검증: PASS
- Git diff whitespace: PASS
- 배포 검증: NOT RUN, AWS Resource와 배포 구현 없음
- 언론사 추출 검증: NOT RUN, 수집기 구현 없음
- E2E: NOT RUN, 전략만 확정되고 Playwright Config와 Test 없음

## Deferred

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]

[Frontend Clean Clone CI 수정을 위한 `package-lock.json` 재생성 승인이 필요합니다.]
