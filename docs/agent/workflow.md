# Harness와 Loop Workflow

## Context Loop

```text
요청 → AGENTS.md → .ai 고정 Context/규칙/계획 → 관련 Module → 관련 구현 → 관련 설정 → 관련 Test
```

- 먼저 `rg`로 요청 용어, Symbol, API, 테스트를 검색
- 제품 정책 작업만 `MVP_REQUIREMENTS.md`의 관련 Heading 확인
- 관련 없는 Module, 전체 요구사항, 생성 산출물은 기본 Context에서 제외
- 새 사실은 `Confirmed`, 패턴 추론은 `Inferred`, 미확인은 `Required`로 표시

## Skill Loading

Harness는 모든 작업에 적용하고 Skill은 작업 조건이 맞을 때만 추가로 읽는다.

| 조건 | Project Local Skill |
| --- | --- |
| 기능·Bug를 Test First로 구현 | `tdd` |
| 새 UI 작성 또는 기존 UI 재설계 | `frontend-design` |
| 목표·범위·완료 기준이 실제로 모호함 | `deep-interview` |
| 반복 작업에 맞는 Skill 존재 여부 조사 | `find-skills` |

- 모든 Skill을 매 작업에 한꺼번에 읽지 않음
- 설치되지 않은 Skill은 검색과 검토 후 사용자 승인 없이 추가하지 않음
- 반복되지 않는 프로젝트 규칙은 새 Skill보다 Harness 문서에 유지
- 같은 전문 작업이 반복되고 Harness만으로 절차 재현이 어려울 때 Project Local Skill 추가 검토

## Planning Loop

코드 수정 전에 다음 형식으로 짧게 작성한다.

```markdown
## Task Understanding
## Current Behavior
## Expected Behavior
## Relevant Context
## Affected Files
## Risks
## Implementation Plan
## Verification Plan
```

각 계획 단계는 수정 대상과 확인 방법을 연결한다. Repository에서 찾을 수 있는 정보는 먼저 조사하고, 찾을 수 없는 결정만 `[xxx 정보가 필요합니다.]`로 남긴다.

## Implementation Loop

1. 실패를 재현하거나 기대 동작을 검증할 Test 또는 확인 기준 준비
2. 한 책임을 해결하는 최소 변경 수행
3. 가장 가까운 Test, Type check 또는 Compile 실행
4. 통과할 때만 다음 변경으로 이동

요청과 무관한 Refactor, Naming 일괄 변경, Dependency 추가, API 계약 변경, 설정 정리는 수행하지 않는다.

## Verification Loop

검증 비용이 낮고 관련성이 높은 순서로 실행한다.

```text
정적 검사 → 관련 Unit Test → Module Build → 관련 통합 Test → 전체 검증
```

문서·Harness 전용 변경은 구조와 Diff 검증부터 수행한다. Security, 데이터 계약, 공통 Config, 여러 Module을 건드린 변경은 전체 검증 대상으로 올린다.

## Failure Loop

```text
실패 로그 확인 → 첫 의미 있는 실패 선택 → 코드 실패/환경 실패/기준선 실패 구분
→ 관련 코드와 설정 확인 → 원인에 대한 최소 수정 → 동일 명령 재실행
```

- Attempt 1: 가장 직접적인 원인 가설 검증
- Attempt 2: 같은 실패면 전제와 관련 Context 재검토
- Attempt 3: 같은 실패면 추측성 수정 중단

세 번 반복되면 확인 사실, 실행 명령, 시도별 결과, Root Cause 후보, 필요한 정보를 보고한다. 환경이나 외부 서비스 때문에 실행하지 못한 검증은 `FAIL`이 아니라 `NOT RUN`으로 구분하되 원인을 숨기지 않는다.

## Self Review Loop

자동 검증 통과 후 다음 항목을 확인한다.

| 관점 | 질문 |
| --- | --- |
| Correctness | 요청과 확인된 정책을 해결했는가 |
| Consistency | 현재 코드와 설정 Pattern을 따르는가 |
| Scope | 관련 없는 변경이 없는가 |
| Simplicity | 더 작은 변경으로 같은 결과가 가능한가 |
| Maintainability | 새 복잡성과 중복이 필요한가 |
| Performance | 외부 호출, DB, Cache의 명백한 비용 증가가 있는가 |
| Security | SSRF, 인증·인가, Secret, 개인정보 위험이 생겼는가 |
| Error Handling | 내부 원인 노출 없이 복구 가능한가 |
| Testability | 바뀐 동작을 자동 검증할 수 있는가 |

적용되지 않는 항목은 `NOT APPLICABLE`로 기록한다. 문제가 있으면 수정 후 관련 검증부터 다시 실행한다.

## Diff Review Loop

`git diff --check`와 전체 Diff에서 다음을 확인한다.

- 요청과 무관한 파일과 Formatting noise
- Debug, Temporary, 주석 처리된 코드, 새 Dead code
- 불필요한 Dependency와 Config 변경
- Secret 또는 실제 환경값
- 의도하지 않은 Public API·Security·데이터 계약 변경
- Test 삭제, Skip, Assertion 약화

문제가 발견되면 수정 → 검증 → Self Review → Diff Review 순서로 되돌아간다.

## Token 절감 원칙

- 안정적인 사실은 `.ai/MEMORY.md`에서 1회 확인하고 반복 설명 생략
- `rg` 검색 결과로 Context 범위를 좁힌 뒤 필요한 구간만 읽기
- 계획과 결과에서 파일 전체 대신 경로, Symbol, Heading 참조
- 변경 파일 기반 `-Scope changed` 검증으로 관련 Module 우선 실행
- 실패 로그는 첫 의미 있는 오류와 원인에 필요한 구간만 유지
- 통과한 동일 검증은 관련 코드가 다시 바뀌기 전까지 반복하지 않음
- 제품 요구사항 전체 대신 변경과 연결된 절만 읽고 전역 정책 변경 때만 전체 검색
- Harness만으로 충분한 작업에는 Skill Context를 추가하지 않음
- 완료 응답에는 변경 결과, 검증, 남은 Required만 기록하고 작업 과정 반복 생략
- 이미 확인한 고정 사실은 경로로 참조하고 응답마다 재서술하지 않음

모델 또는 추론 속도 변경에 의존하지 않는다.
