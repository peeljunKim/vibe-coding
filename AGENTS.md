<!-- AI Agent 작업 진입 규칙 -->
# 기사체크 AI Agent Harness

이 파일은 모든 작업의 진입점이다. 확인하지 못한 프로젝트 정보는 추측하지 않고 `[확인에 필요한 구체적인 정보가 필요합니다.]` 형식으로 기록한다.

## 시작 순서

1. `.ai/MEMORY.md`에서 현재 Repository의 고정 사실 확인
2. `.ai/RULES.md`에서 수정·보안·주석 규칙 확인
3. `.ai/PLAN.md`에서 진행 중 목표와 알려진 검증 상태 확인
4. 작업과 관련된 모듈의 구현 → 설정 → 테스트 순서로 탐색
5. DB Schema 변경에는 `docs/architecture/DATABASE_SCHEMA.md` 확인
6. Frontend UI·Figma·OAuth 버튼 작업에는 `docs/agent/frontend.md` 확인
7. 도메인 정책 변경에만 `MVP_REQUIREMENTS.md`의 관련 절을 선택적으로 확인

Repository 전체나 요구사항 전체를 매번 읽지 않는다. `rg`로 관련 심볼과 정책을 먼저 찾고, 연결된 파일만 단계적으로 읽는다.

## 근거 분류

- `Confirmed`: 실제 코드, Build/Config, 테스트, CI 또는 현재 Repository 문서에서 확인한 사실
- `Inferred`: 여러 구현 패턴으로 추론했으며 추론임을 명시한 내용
- `Required`: Repository에서 확인하지 못해 사용자 결정이 필요한 내용

근거 우선순위는 실제 코드 → Build/Config → 테스트 → CI → 프로젝트 문서 → README → 추론 순서다. 문서와 코드가 다르면 둘 다 기록하고 코드를 현재 동작의 근거로 삼는다.

## 작업 Loop

1. 이해: 현재 동작, 기대 동작, 관련 근거와 미확인 정보 정리
2. 계획: 영향 파일, 위험, 최소 구현 단위, 검증 방법 명시
3. 구현: 한 번에 하나의 작은 변경만 수행
4. 검증: 낮은 비용의 관련 검증부터 실행
5. 평가: 첫 번째 의미 있는 실패와 원인 구분
6. 수정: 원인과 직접 관련된 최소 변경 후 같은 검증 재실행
7. 리뷰: 자동 검증 통과 후 Self Review와 Diff Review 수행
8. 완료: Definition of Done을 `PASS / FAIL / NOT RUN / NOT APPLICABLE`로 보고

같은 실패가 세 번 반복되면 추측성 수정을 중단하고 사실, 시도, 결과, 원인 후보와 필요한 정보를 보고한다.

상세 절차와 계획 템플릿은 `docs/agent/workflow.md`, 검증 명령과 완료 조건은 `docs/agent/verification.md`를 따른다. 프로젝트 구조와 위험 근거는 `docs/agent/project-context.md`를 참고한다.

## 핵심 규칙

- 요청과 직접 관련된 최소 범위만 수정
- 기존 API·보안 검사·검증 규칙의 임의 완화 금지
- 실패 해결을 위한 테스트 삭제, Skip, Assertion 완화 금지
- Dependency, 파괴적 DB Schema, 인증·인가, CI, Infrastructure 변경은 영향과 롤백 방법을 먼저 제시
- Secret, 실제 `.env`, 운영 데이터, 운영 배포의 자동 변경 금지
- Local 설정 사용을 위한 `.gitignore` 일시 해제와 `git add -f` 금지
- 클래스 설명과 메서드 역할 주석은 필요한 경우에만 짧고 쉬운 한국어 명사형으로 작성하며 마침표 생략
- `.env.example` 주석도 파일 역할과 설정 묶음을 설명하는 짧은 한국어 명사형으로 작성
- 실행하지 않은 검증을 `PASS`로 표시 금지

## 기본 검증

```powershell
pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope changed
```

완료 전에는 변경 범위 검증, Self Review, `git diff --check`, 전체 diff 검토를 수행한다. 전체 검증이 필요한 조건은 `docs/agent/verification.md`에 정의한다.
