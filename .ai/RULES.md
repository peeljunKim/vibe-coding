<!-- AI Agent 구현과 보안 Guardrail -->
# 프로젝트 작업 규칙

## Evidence

- 확인된 사실과 추론을 분리
- 확인 불가 항목은 `[xxx 정보가 필요합니다.]` 형식으로 유지
- 제품 정책 변경 전 `MVP_REQUIREMENTS.md` 관련 절과 실제 구현을 함께 확인
- 문서와 코드 불일치 시 현재 동작은 코드 기준, 의도는 문서 기준으로 병기

## Scope

- 요청 해결에 필요한 파일만 수정
- 관련 없는 정리, 일괄 Naming 변경, 디렉터리 이동, 선행 리팩터링 금지
- 새 Dependency와 추상화는 기존 도구로 해결 불가능한 근거가 있을 때만 제안
- 변경으로 새로 미사용이 된 코드만 함께 제거

## Test Protection

- 테스트 삭제, Skip, Assertion 완화로 실패 은폐 금지
- 보안 검사, Validation, Error Handling 제거 금지
- 테스트 오류를 의심할 때 요구사항·현재 동작·실패 로그 근거 우선 제시

## Comment

- 새 사람이 관리하는 파일은 문법이 허용할 때 첫 부분에 한 줄 역할 주석 작성
- Markdown은 첫 줄의 짧은 HTML 주석으로 파일 역할 표시
- JSON, Lockfile, Binary, 생성물처럼 주석이 불가능하거나 불필요한 파일은 역할 주석 제외
- 복잡한 책임 경계와 비자명한 의도만 주석화
- 클래스 설명과 메서드 역할은 짧고 쉬운 한국어 명사형
- 마침표 없는 명사형 종결
- 코드 동작을 그대로 반복하는 주석 금지
- Token을 늘리는 장문 설명과 반복 주석 금지
- `.env.example`은 파일 역할과 설정 그룹을 짧은 한국어 명사형 주석으로 설명

## Product Guardrail

- 두 분석 기능의 API, 이용량, 캐시, 저장 정책 혼합 금지
- 기사 URL 처리 전 HTTPS, 허용 언론사, 내부 IP, Redirect 최종 목적지 검증
- 기사 원문 전체와 개인정보의 DB·공용 캐시·운영 로그 저장 금지
- AI 출력의 구조 검증과 출처 URL 재검증 생략 금지
- 인증·인가를 Frontend 메뉴 표시 여부에 의존 금지
- 관리자 API의 서버 측 `ADMIN` 검사 유지
- 결과 상태를 색상만으로 전달 금지
- Gemini 무료 등급에는 공개 기사와 허용 근거만 전송하고 개인정보·기밀정보 전송 금지
- AI 출력은 저장이나 표시 전에 Schema와 고정 Domain 규칙으로 재검증

## Deployment Guardrail

- 단일 EC2 Blue/Green 전환은 애플리케이션 배포 중 무중단만 의미
- EC2, MySQL, Redis 장애까지 견디는 고가용성으로 표현 금지
- Blue와 Green은 동일 MySQL·Redis를 사용하고 DB Schema 변경은 이전·신규 Version 동시 호환 필수
- Health Check 실패 시 Traffic 전환 금지와 기존 Version 유지
- Secret, DuckDNS Token, 실제 `.env`의 Repository 저장 금지
- 운영 배포, DNS, IAM, Security Group, 비용 발생 Resource 변경은 사용자 승인 필수

## External Credential Guardrail

- Prompt나 문서에 제공된 실제 Key, Secret, App Password를 Repository 파일·로그·테스트 Fixture에 복사 금지
- 무시된 Local 설정을 읽거나 수정하기 위해 `.gitignore` 규칙을 일시 해제하지 않음
- AI Agent는 Git 무시 여부와 관계없이 Local 파일을 직접 읽고 필요한 범위에서 수정
- 실제 설정 파일에 `git add -f` 사용 금지
- YAML 설정은 Local 전용으로 유지하고 활성 CI Workflow만 Git 추적 예외 적용
- 추적·Stage·비무시 신규 파일에서 실제 Key, Secret, Token, App Password, Private Key 값 검사 필수
- `.env.example`에는 변수 이름과 비민감 Local URL만 기록하고 실제 값은 사용자가 `.env`에 직접 입력
- OAuth 공급자 Console의 Callback URL과 Backend 처리 경로를 구현·테스트에서 동일하게 유지
- Google Identity Services의 Client Callback 방식과 Spring Security OAuth2 Redirect 방식을 한 흐름에 혼합 금지

## Permission

Allowed:

- Repository 읽기와 검색
- 요청 범위의 Source·Test·문서 수정
- 관련 Build, Test, Lint와 로컬 Mock 실행
- 실패 분석과 최소 수정·재검증 반복
- 요청 기능에 필요한 이전·신규 Version 호환 Table·Column 추가 SQL과 변경 근거 작성

Require User Approval:

- Dependency와 Lock 파일 변경
- Table·Column 삭제·이름 변경·Type 축소, 대량 데이터 변환, Schema 관리 방식 변경
- 기존 Public API 호환성을 깨는 변경과 인증·인가·Security 정책 변경
- CI/CD, Docker, AWS Infrastructure 변경
- 외부 서비스 실제 요청과 Network Download
- 프로젝트 Local Skill 설치·삭제
- Git Commit, Push, PR 생성, Staging 배포와 Rollback

Do Not Perform Automatically:

- Secret, API Key, 비밀번호 생성·변경·노출
- 운영 DB Schema·데이터의 직접 변경·삭제와 Version SQL 자동 적용
- AWS IAM, Security Group, DNS 실제 변경
- 비용 발생 가능 Resource 생성과 운영 배포
- 강제 Push, Branch 삭제, `reset --hard`
- Local 설정과 Secret 파일의 `.gitignore` 일시 해제 또는 강제 Stage
- 테스트 삭제, Skip, Assertion 완화
