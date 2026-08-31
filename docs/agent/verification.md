# Verification Harness와 Definition of Done

## 자동 검증 실행

```powershell
# 변경 파일에서 필요한 Module 자동 선택
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope changed

# 명시적 Module 검증
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope frontend
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope backend
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope infra
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope docs

# 전체 검증
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope all
```

Script는 기존 Repository 도구만 사용한다. Frontend는 npm scripts, Backend는 Maven Wrapper launcher 또는 현재 Wrapper JAR, Infrastructure는 Native MySQL Schema 정적 검사와 Docker Compose를 사용한다. 새 Lint, Formatter, Test 도구를 설치하지 않는다.

모든 Scope는 완료 전에 Git 추적·Stage·비무시 신규 파일을 검사한다. 실제 `.env`, Local 전용 설정, Credentials, Service Account, Private Key 파일이나 Key·Secret·Token·App Password 값 후보가 발견되면 검증을 실패 처리한다. 빈 값, 환경 변수 참조, 명시적 Placeholder는 허용한다. 검사 목적으로 `.gitignore`를 해제하지 않는다.

## 실제 검증 순서

| 범위 | 순서 | 명령 근거 |
| --- | --- | --- |
| Frontend | ESLint → Vitest → TypeScript/Vite Build | `frontend/package.json`, GitHub Actions |
| Backend | Compile → JUnit → Package | Maven `verify`, GitHub Actions |
| Infrastructure | Native MySQL 초기 SQL 구조 → Compose 구문과 환경 변수 해석 | Version SQL, `docker compose --env-file .env.example config --quiet` |
| E2E | Vite Preview → Chromium Scenario → Release Chrome·Edge | Playwright 전략, 지원 Browser 정책 |
| Docs/Harness | 필수 파일·참조 확인 → Diff whitespace 검사 | `AGENTS.md`, Harness 구조 |

Frontend formatting은 `npm run format`으로 별도 확인한다. 현재 기준선에 기존 formatting 불일치가 있어 자동 기본 검증에는 포함하지 않으며, Formatting 변경이나 정리 요청에서 실행한다.

## E2E 적용 기준

- Config: `frontend/playwright.config.ts`
- Test: `frontend/e2e/*.spec.ts`
- Base URL: `http://127.0.0.1:4173`
- Server: Vite Production Preview와 `reuseExistingServer: !process.env.CI`
- Pull Request: bundled Chromium
- Release 전: Stable Chrome와 Stable Edge
- External AI·검색: 기본 Mock
- Full-stack: Backend와 Infrastructure가 준비된 뒤 별도 Smoke Test
- Failure Artifact: 첫 Retry Trace, Screenshot, 필요한 경우 Video

현재는 Config와 E2E Test가 없으므로 `NOT RUN`이다. 첫 사용자 흐름 구현 시 해당 흐름의 Test와 함께 Config를 추가하고, 빈 E2E Scaffold만 미리 만들지 않는다.

## 전체 검증이 필요한 조건

- Frontend와 Backend 계약을 함께 변경
- 인증·인가, CSRF, Session 변경
- 공용 Cache, 이용량, 시간대 정책 변경
- DB Entity 또는 Schema 변경
- Root 환경 변수, Docker Compose, CI 변경
- 여러 Module에서 재사용되는 설정 변경

## 검증 결과 기록

각 명령은 다음 네 상태 중 하나로 기록한다.

- `PASS`: 현재 변경 상태에서 실제 실행 성공
- `FAIL`: 명령이 실행되었고 코드 또는 기준 위반으로 실패
- `NOT RUN`: 도구, 권한, 외부 서비스 또는 시간 제약으로 미실행
- `NOT APPLICABLE`: 변경과 관련 없음

환경 실패는 코드 실패와 분리한다. 기준선부터 실패한 항목은 변경 전후 근거를 함께 기록하고 새 실패처럼 숨기지 않는다.

## Definition of Done

| 항목 | 상태 기준 |
| --- | --- |
| 요구사항 충족 | 확인된 요청과 기대 동작을 Test 또는 근거로 확인 |
| 정보 정직성 | Confirmed/Inferred/Required 분리, 추측 없음 |
| 최소 변경 | 모든 변경 줄이 요청 또는 변경으로 생긴 정리에 연결 |
| Pattern 일관성 | 실제 기존 구현 Pattern 또는 명시된 새 결정 준수 |
| Build | 영향 Module Build 결과 기록 |
| 관련 Test | 변경 동작의 Test 결과 기록 |
| 회귀 Test | 위험에 비례한 Module 또는 전체 검증 결과 기록 |
| Test 보호 | 삭제, Skip, Assertion 약화 없음 |
| Self Review | Correctness부터 Testability까지 검토 |
| Diff Review | Secret, Debug, Noise, API 변경 검토 |
| Local 설정 보호 | 추적 금지 파일명·보안값 후보 검사 성공과 `.gitignore` 유지 확인 |
| 알려진 실패 | 기준선·환경·외부 의존 실패를 숨기지 않고 기록 |

모든 항목에 `PASS / FAIL / NOT RUN / NOT APPLICABLE`을 붙인다. 필수 항목이 `FAIL`이면 완료하지 않고 수정 Loop로 돌아간다. 필수 검증이 `NOT RUN`이면 완료로 가장하지 않고 제약과 사용자에게 필요한 조치를 보고한다.

## End-to-End Example

실제 요구사항의 “첫 화면에서 `건강·의학 뉴스 확인`과 `기사 제목 확인`을 독립 기능으로 제공” 요청을 예로 든다.

1. Context: `AGENTS.md` → `.ai/*` → `MVP_REQUIREMENTS.md` 4.2절 → `frontend/src/App.tsx` → `App.test.tsx`만 읽기
2. Understand: 현재는 설정 안내 화면, 기대 동작은 두 독립 진입점, Backend API 구현은 이번 요청에 포함되지 않았는지 확인
3. Plan: App과 관련 Style/Test만 영향 파일로 지정, 고령 사용자 가독성과 기능 혼합을 위험으로 기록
4. Implement: 먼저 두 버튼의 접근 가능한 이름을 검증하는 Test 추가, 최소 UI 변경
5. Verify: Frontend 관련 Test → ESLint → Build 순서 실행
6. Failure: 역할 Query 실패 시 DOM과 접근 가능한 이름을 확인하고 최소 Markup 수정 후 같은 Test 재실행
7. Self Review: 색상 외 문구, 충분한 구분, 불필요한 Router·State 추가 여부 확인
8. Diff Review: 기존 안내 외 무관한 Style 변경, 임시 문구, Backend 변경 여부 확인
9. DoD: 실행한 항목만 상태로 기록하고 필수 항목이 모두 `PASS`일 때 완료

Backend endpoint, URL 수집, 분석 호출은 요청에 없으므로 이 예제에서 임의로 구현하지 않는다.
