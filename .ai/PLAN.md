# 현재 작업 계획

## Goal

Native MySQL 초기 Schema와 Git Version SQL 변경 관리 적용

## Scope

- `infra/mysql/schema/*`, `docker-compose.yml`, `.env.example`
- `README.md`, `docs/architecture/*`, `.ai/*`, `AGENTS.md`
- `scripts/agent/verify.ps1`, `MVP_REQUIREMENTS.md`

## Plan

1. Native MySQL 설치·Service·Client 상태 확인
2. Compose의 영구 MySQL Service 제거
3. 업무 Table을 제외한 초기 Schema 변경 이력 SQL 작성
4. Git Version SQL의 이유·내용·호환성·Rollback 절차 문서화
5. DB 승인 규칙을 파괴적 Schema 변경 기준으로 수정
6. SQL 구조·Compose·Harness 검증

## Verification Status

- Native MySQL 설치와 Service: PASS, Windows MySQL 8.0.30 Service 실행
- Local MySQL Version 기준: PASS, Service와 전용 Client 8.0.30
- 초기 Schema SQL 구조: PASS
- 격리 Native MySQL 초기 Schema 적용과 이력 조회: PASS
- 실제 Local Schema 적용: NOT RUN, Root `.env`와 인증 정보 없음
- Infrastructure compose config: NOT RUN
- Harness 문서 구조와 Secret 검사: NOT RUN
- Git diff whitespace: NOT RUN
- 배포 검증: NOT RUN, AWS Resource와 배포 구현 없음
- 언론사 추출 검증: NOT RUN, 수집기 구현 없음
- E2E: NOT RUN, 전략만 확정되고 Playwright Config와 Test 없음

## Deferred

[Native MySQL 초기 Schema 실제 적용을 위한 Local Root 인증 설정이 필요합니다.]

[배포 환경에 설치할 Native MySQL Version 정보가 필요합니다.]

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
