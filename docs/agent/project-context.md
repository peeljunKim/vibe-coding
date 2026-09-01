<!-- Repository 구조와 위험 근거 -->
# Project Discovery와 Risk Guardrail

기준일: 2026-08-31

## Confirmed

| 조사 항목 | Repository 근거 |
| --- | --- |
| Project Type | `frontend`, `backend`, `infra`로 분리된 비공개 Full-stack Web MVP |
| Programming Language | Frontend TypeScript/TSX/CSS, Backend Java 17 대상, 설정 YAML/JSON, 자동화 PowerShell |
| Local Java | Oracle JDK 17.0.11과 Scoop OpenJDK 17.0.2 실행 확인 |
| Framework | React 19.2 계열, Spring Boot 4.1.0, Vite 8 계열 |
| Build System | Frontend npm scripts, Backend Maven 3.9.11 Wrapper 설정 |
| Module Structure | 단일 Repository 안의 독립 Frontend/Backend/Infrastructure 모듈, Root 통합 Build 도구 없음 |
| Dependency Management | `frontend/package-lock.json`, `backend/pom.xml` |
| Configuration | Root·Frontend `.env.example`, Spring `application.yml`/`application-prod.yml`, Docker Compose |
| Architecture | 현재 코드는 React 단일 App과 Spring Boot 부트스트랩·Security 설정 수준 |
| Domain Structure | 요구사항에는 건강 뉴스, 제목 확인, 회원, 기록, 공유, 신고가 있으나 Domain 코드는 아직 없음 |
| Database/Persistence | Local MySQL 8.0.30 Native Service, GitHub Version SQL·Commit·PR 이력, JPA `ddl-auto: validate`; 업무 Entity/Repository는 아직 없음 |
| Cache/Session | Redis 8.8 Compose, Spring Data Redis와 Redis Session, 3일 분석 Cache 설정 |
| External Services | Gmail SMTP, Google/Kakao/Naver OAuth, Gemini·PubMed 환경 변수 자리만 존재; 실제 Provider 구현 없음 |
| Authentication/Authorization | Spring Security, Cookie CSRF, Actuator 일부 공개, 나머지 요청 인증 필요; Domain 인증 흐름은 미구현 |
| Testing | Vitest/Testing Library 1 test, JUnit/AssertJ 1 test; Playwright 전략은 확정됐으나 Config·E2E Test 없음 |
| Logging | Root/Spring Security level과 trace/span correlation pattern, Prod ECS 구조화 Console 설정 |
| Monitoring | Actuator, Prometheus scrape, Grafana provisioning과 Dashboard |
| CI/CD | GitHub Actions에서 Frontend lint/test/build, Backend verify, Compose config 검증; 배포 단계 없음 |
| Deployment | AWS Free Plan 단일 EC2, DuckDNS, Blue/Green 애플리케이션 배포로 결정; Pipeline은 아직 없음 |
| Container/Infrastructure | MySQL은 Host Service, Compose는 Redis·Prometheus·Grafana; 현재 Compose에는 Application image 없음 |
| Code Convention | EditorConfig, Prettier, ESLint strict typed 설정, TypeScript strict 옵션, Java 4-space 들여쓰기 |

요구사항 Discussion #1과 `MVP_REQUIREMENTS.md`는 제품 범위를 설명한다. 실제 구현 여부는 코드와 설정으로 별도 확인한다.

## 문서와 실제 구현 차이

- README는 `backend`에 API와 내부 Worker가 있다고 설명하지만 현재 Worker 구현은 없음
- README와 CI는 Maven Wrapper 실행 파일을 사용하지만 현재 `mvnw`와 `mvnw.cmd`는 없고 Wrapper JAR와 속성만 있음
- Frontend `package.json`에는 E2E 명령이 있으나 Playwright 설정과 E2E 테스트 파일은 없음
- JPA, Redis, OAuth, Mail, Testcontainers 의존성은 존재하지만 대부분의 Domain 사용 코드는 아직 없음
- Local Schema 작업은 Windows Service 전용 MySQL 8.0.30 Client를 사용하며 Scoop 기본 Client 9.7.1은 사용하지 않음

## 확정된 Prototype 결정

### AI와 Evidence

- `gemini-3.7-flash` 무료 등급 사용
- 공개 기사와 허용된 근거만 AI에 전송
- 개인정보, 기밀정보, 개인 건강정보 전송 금지
- PubMed 검색은 NCBI E-utilities 사용
- 공식 기관 자료는 허용 Domain의 기관별 검색 방식 사용
- AI 응답은 Schema와 고정 판정 규칙으로 서버 재검증

### 초기 언론사 추출 후보

| 유형 | 후보 Domain |
| --- | --- |
| 통신사 | 연합뉴스 `yna.co.kr`, 뉴시스 `newsis.com` |
| 방송·보도 | KBS `news.kbs.co.kr`, MBC `imnews.imbc.com`, SBS `news.sbs.co.kr`, YTN `ytn.co.kr`, JTBC `news.jtbc.co.kr` |
| 종합지 | 조선일보 `chosun.com`, 중앙일보 `joongang.co.kr`, 동아일보 `donga.com`, 한겨레 `hani.co.kr`, 경향신문 `khan.co.kr`, 한국일보 `hankookilbo.com`, 국민일보 `kmib.co.kr`, 서울신문 `seoul.co.kr` |
| 경제지 | 매일경제 `mk.co.kr`, 한국경제 `hankyung.com` |
| 건강·의료 | 헬스조선 `health.chosun.com`, 코메디닷컴 `kormedi.com`, 메디칼타임즈 `medicaltimes.com` |

후보는 허용 목록 자체가 아니다. 실제 PC 공개 기사에서 최종 URL, 제목, 게시·수정일, 본문 시작·끝, 글자 수, 광고·댓글 혼입, 처리 시간과 오류 코드를 검증한 뒤 통과한 Domain만 활성화한다.

### Deployment

- AWS Free Plan의 단일 EC2와 DuckDNS 사용
- Blue/Green Application Container와 Reverse Proxy Traffic 전환
- MySQL은 EC2 Host의 영구 데이터 디렉터리, Redis는 같은 EC2의 영구 Volume 사용
- 보장 범위는 애플리케이션 배포 중 무중단
- EC2·MySQL·Redis 장애를 견디는 고가용성은 Prototype 범위 밖

### E2E

- `frontend/playwright.config.ts`, `frontend/e2e/*.spec.ts` 구조
- Local/CI Base URL `http://127.0.0.1:4173`, Vite Preview 사용
- Pull Request는 bundled Chromium, Release 전 Stable Chrome·Edge 검증
- 외부 AI·검색은 기본 Mock, 실제 Backend 흐름은 별도 Smoke Test
- Retry는 CI 2회와 Local 0회, 첫 Retry Trace와 실패 Screenshot 보관

### Local 외부 서비스 준비

- Gemini API Key, Gmail App Password, OAuth Key·Secret은 사용자가 Local `.env`에 직접 입력
- `.env.example`에는 빈 Secret 자리와 비민감 Local URL만 유지
- Naver 서비스 URL과 Google 서비스 URL은 `http://localhost:8080`
- Naver 로그인·연결 끊기 Callback은 각각 `/oauth/naver`, `/oauth/naver/disconnect`
- Kakao와 Google 로그인 Callback은 각각 `/oauth/kakao`, `/oauth/google`
- 실제 Spring Security OAuth 처리 경로는 아직 구현되지 않았으므로 Callback 일치 검증은 `NOT RUN`

## Inferred

- 현재는 기능 개발 전 Project Scaffold 단계로 보임
- Spring Package 계층과 Domain 경계는 구현 사례가 부족해 아직 규칙으로 확정할 수 없음

## Required

[초기 언론사 후보의 실제 기사 추출 테스트 결과가 필요합니다.]

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]

[Branch 전략과 Release 승인 절차 정보가 필요합니다.]

[통합 테스트에서 사용할 외부 서비스 또는 Testcontainer 범위 정보가 필요합니다.]

## 발견된 구현 Pattern

- Frontend 파일명: Component는 PascalCase, 설정과 공통 파일은 소문자
- Frontend 테스트: 대상 파일 옆 `*.test.tsx`, 사용자에게 보이는 역할 기반 Query
- Frontend 품질: TypeScript strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, ESLint typed rule
- Backend Package: `com.newsverification` Root와 기술 설정용 `config`
- Backend 설정: 비밀값 환경 변수 주입, 로컬 비민감 기본값만 Config에 유지
- Security: 서버 측 기본 인증, Cookie CSRF, 최소 Actuator endpoint 공개
- 구현 사례가 없는 Error Response, Validation, Transaction, Repository, API 응답 형식은 Pattern으로 생성하지 않음

## Risk Discovery와 Guardrail

| 위험 | 실제 근거 | Guardrail |
| --- | --- | --- |
| 요구사항과 구현 혼동 | 상세 요구사항 대비 Scaffold 코드만 존재 | 계획에서 `요구됨`과 `구현됨`을 분리 |
| 두 분석 기능 정책 혼합 | 이용량·Cache·저장 정책이 기능별로 다름 | API, Cache key, 이용량, 테스트를 기능별 분리 |
| SSRF와 추출 비용 | 외부 기사 URL 수집, Redirect와 DNS 검사 요구 | AI 호출 전 URL·Host·IP·Port·Redirect·크기·시간 검증 |
| 의료 정보 과단정 | 고정 상태·출처·전문가 미검토 문구 요구 | AI 자유 판정 금지, 구조 검증, 출처 재검증, 경고 문구 보호 |
| 개인정보·Secret 노출 | OAuth, SMTP, AI key, IP hash 설정 | 실제 `.env` 금지, 기사 원문·IP·개인정보 Log 금지 |
| 인증·관리자 권한 누락 | Security와 `ADMIN` 서버 검사 요구 | Frontend 숨김과 무관한 서버 인가 테스트 필수 |
| Redis 장애 시 비용 제한 우회 | Cache·세션·이용량·Lock 책임 집중 | 이용량 또는 Lock 확인 불가 시 새 AI 분석 중단 |
| Schema drift | `ddl-auto: validate`, GitHub Version SQL 방식, DB 이력 Table 미사용 | Entity와 호환 SQL을 함께 추가하고 Commit·PR에 적용 결과 기록 |
| 무료 AI 데이터 처리 | Gemini 무료 등급을 Prototype에 사용 | 공개 기사·허용 근거만 전송하고 개인정보·기밀정보 차단 |
| 단일 EC2 장애 범위 | API·Native MySQL·Redis가 같은 EC2에 배치될 예정 | 배포 무중단과 고가용성을 구분하고 Backup·Rollback 확인 |
| Blue/Green Schema 충돌 | 두 Application Version이 동일 DB 사용 | Traffic 전환 전 양쪽 Version 호환 Migration 검증 |
| 선언된 검증과 실행 차이 | Wrapper launcher 부재, E2E 설정 부재 | 실행 가능성 먼저 확인하고 `NOT RUN`과 도구 실패를 구분 |
| Harness 비추적 | Local Markdown 제외 요청과 CI의 Harness 문서 의존 | `AGENTS.md`, `.ai`, `docs/agent`, 연결된 Architecture 문서를 명시적으로 Git 추적 |

## Harness Architecture

```text
AGENTS.md                         모든 Task 시작
.ai/MEMORY.md                     안정적인 Project Context
.ai/RULES.md                      구현과 Guardrail
.ai/PLAN.md                       현재 목표와 검증 상태
docs/agent/project-context.md     Discovery와 Risk 판단
docs/agent/workflow.md            Plan부터 재검증까지의 Loop
docs/agent/verification.md        명령, DoD, 실제 예제
scripts/agent/verify.ps1          변경 범위별 자동 검증
```

현재 구현 규모에서는 Architecture, Domain, Security 문서를 더 쪼개지 않는다. 관련 코드가 생기고 한 문서에서 필요한 Context를 선택하기 어려워질 때 Module별 Harness 분리를 검토한다.
