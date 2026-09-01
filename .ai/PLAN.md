<!-- 현재 목표와 검증 상태 -->
# 현재 작업 계획

## Goal

Local 설정 비추적, Harness Git 유지, GitHub Actions 검증 안정화

## Scope

- `.gitignore`, `.github/workflows/ci.yml`, `scripts/agent/verify.ps1`
- `.env.example`, `frontend/.env.example`
- `AGENTS.md`, `.ai/*`, `docs/agent/*`, `docs/architecture/*`

## Plan

1. `.env.example`과 Local 설정의 Git 제외 규칙 확인
2. Harness Job 필수 Markdown의 명시적 Git 유지 규칙 확인
3. 사라진 변경 후보 파일을 허용하도록 검증 Script 수정
4. CI에서 `.env.example` 없이 Infrastructure 검증 가능하도록 수정
5. 주석 지원 파일의 짧은 명사형 파일 역할 주석 확인
6. 변경 Scope 검증과 전체 Diff Review

## Verification Status

- `.env.example` Git 제외 규칙: PASS
- Harness Markdown Git 유지 규칙: PASS
- 사라진 변경 후보 파일 처리: PASS, 단일 `Get-Item` 결과의 Null과 Directory를 건너뜀
- `.env.example` 없는 Infrastructure 검증: PASS, Process 전용 검증값으로 Compose 해석
- 주석 지원 파일의 역할 주석: PASS, 주석 불가 JSON·Lockfile·Binary 제외
- 변경 범위 Build·Test: PASS, Backend 1 Test와 Frontend 1 Test
- Harness 문서와 Secret 검사: PASS
- Git diff whitespace: PASS
- 배포 검증: NOT RUN, AWS Resource와 배포 구현 없음
- 언론사 추출 검증: NOT RUN, 수집기 구현 없음
- E2E: NOT RUN, 전략만 확정되고 Playwright Config와 Test 없음

## Deferred

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
