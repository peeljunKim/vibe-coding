<!-- 현재 목표와 검증 상태 -->
# 현재 작업 계획

## Goal

Figma 기반 Frontend 구현 전 Local 환경 준비

## Scope

- Node, npm, Java와 Frontend 기존 검증 상태
- Figma 대상 Node 접근 상태
- Git에서 제외된 Root와 Frontend 환경 변수 Template
- Source, SecurityConfig, OAuth 로직 변경 제외

## Plan

1. Local 개발 도구 Version 확인
2. Figma 대상 Node 접근 확인
3. OAuth Redirect에 필요한 비민감 Backend Base URL 변수 보완
4. 환경 변수 Template의 Git 비추적 상태 확인
5. 기존 Frontend Lint, Test, Build 기준선 확인

## Verification Status

- Node와 npm: PASS, Node 22.18.0과 npm 10.9.3
- Java: PASS, Oracle JDK 17.0.11
- Figma Context: PASS, file `qo0ztGDqf3MrinTOyyySy9`의 node `13:719`
- Frontend Lint: PASS
- Frontend Test: PASS, 1개 Test
- Frontend Build: PASS
- Frontend Clean Clone 의존성 설치: PASS, npm 10.9.3 Lockfile 재생성과 `npm ci`
- OAuth 환경 변수 Template: PASS, Root `APP_BASE_URL`과 Frontend `VITE_BACKEND_BASE_URL` 분리
- 환경 변수 Template Git 비추적: PASS, Local 파일 보존과 인덱스 제거
- Docs/Harness와 Secret 검사: PASS
- Git diff whitespace: PASS
- 실제 OAuth Provider 연동: NOT RUN, Source와 Security 설정 변경 제외
- 배포 검증: NOT RUN, AWS Resource와 배포 구현 없음
- 언론사 추출 검증: NOT RUN, 수집기 구현 없음
- E2E: NOT RUN, 전략만 확정되고 Playwright Config와 Test 없음

## Deferred

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
