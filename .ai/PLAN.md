<!-- 현재 목표와 검증 상태 -->
# 현재 작업 계획

## Goal

DB 내부 이력 Table 제거와 GitHub 중심 Schema 변경 관리

## Scope

- `infra/mysql/schema/*`, `scripts/agent/verify.ps1`, `scripts/agent/setup-local-mysql.ps1`
- `.ai/*`, `docs/agent/*`, `docs/architecture/*`

## Plan

1. DB 내부 변경 이력 Table과 의존 절차 제거
2. 실제 Domain Schema 변경만 Version SQL로 추가
3. 변경 이유와 적용 결과를 GitHub Commit·PR·문서에서 관리
4. DB 이력 Table 재도입 방지 검증 추가
5. Local DB와 애플리케이션 계정 설정 절차 분리
6. Infrastructure와 Harness 검증

## Verification Status

- DB 내부 변경 이력 Table 제거: PASS
- GitHub 중심 Schema 변경 절차: PASS
- DB 이력 Table 금지 검증: PASS
- Local DB와 계정 설정 Script 구문: PASS
- Infrastructure와 Harness 검증: PASS
- Git diff whitespace: PASS
- 배포 검증: NOT RUN, AWS Resource와 배포 구현 없음
- 언론사 추출 검증: NOT RUN, 수집기 구현 없음
- E2E: NOT RUN, 전략만 확정되고 Playwright Config와 Test 없음

## Deferred

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
