<!-- 현재 Frontend 작업 대상과 진행 상태 -->

# 현재 작업 계획

## Target

- Figma file: `qo0ztGDqf3MrinTOyyySy9`
- Figma Section: `13:446`
- 구현 확인 Node: `13:533`, `13:565`, `13:719`, `31:2`
- PNG 기준 추가 화면: 건강 결과, 제목 결과, 저장 기록, 회원가입 3단계, 신고 관리자
- 결과 화면 최신 Node: `15:114`
- URL: `https://www.figma.com/design/qo0ztGDqf3MrinTOyyySy9/기사체크-·-데스크톱-UX-UI?node-id=13-446`
- 화면 범위: Desktop 구현과 시각 비교

## Current Status

아래는 기존 작업의 검증 이력이다. PR 16 보완과 통합 초기 SQL의 현재 검증은 마지막 절에서 별도로 기록한다.

- Frontend 전용 Harness 문서와 `AGENTS.md` 연결: PASS
- Docs/Harness, Secret와 Diff 검사: PASS
- Figma Design Context와 Screenshot 접근: PASS
- Node 22.18.0, npm 10.9.3과 Java 17.0.11: PASS
- Clean Clone `npm ci`: PASS
- 기존 Frontend Lint, 1개 Test와 Build: PASS
- Figma Section의 제품 화면 목록 확인: PASS
- 기능 선택 홈 `13:533` 구현과 1440×1024 시각 비교: PASS
- 건강 기사 분석 중 `13:565` 구현과 1440×1024 시각 비교: PASS
- 로그인 `13:719` 구현과 1440×1024 시각 비교: PASS
- 홈 → 건강 기사 분석 중 → 취소 화면 전환: PASS
- 사용자 제공 원본 PNG 7개 기준 추가 Frame 구현: PASS
- 건강 결과, 제목 결과, 저장 기록과 신고 관리자 1440×1024 시각 비교: PASS
- 회원가입 1·2·3단계 전환과 1440×1024 시각 비교: PASS
- Figma Design Context 재조회: NOT RUN (Figma Starter MCP 호출 한도)
- Provider 공식 OAuth 버튼 Asset과 Backend 로그인 시작 URL: PASS
- OAuth Callback과 실제 Provider 연동: NOT RUN
- 일반 로그인·도움말·계정 찾기 Backend 연결: NOT RUN (일반 회원가입·이메일 인증은 아래 Vertical Slice 참조)
- Browser Runtime과 Console 오류 확인: PASS
- Browser 회원가입 단계 전환: PASS
- 실행 코드의 화면 더미 데이터 제거와 화면별 입력 모델 정의: PASS
- 데이터 미제공·빈 목록 상태의 Desktop 레이아웃 유지: PASS
- 1024·1280·1440px에서 8개 Route 가로 넘침 검사: PASS
- Backend API 실제 데이터 연결: NOT RUN
- Frontend Test 15개, TypeScript, Lint, 변경 파일 Format과 Build: PASS
- Native MySQL 8.0.30 서비스 실행과 초기 Schema 파일 13개 Table 정적 확인: PASS
- 빈 Local Database에 초기 Schema 실제 적용과 `information_schema` 확인: PASS
- Local 애플리케이션 계정의 DML 전용 권한과 DDL 차단 확인: PASS
- Spring JPA DataSource와 `ddl-auto: validate` 애플리케이션 기동: PASS
- 지원 언론사 공개 조회 API의 지원·일시 중단·현재 미지원 상태 변환: PASS
- `GET /api/publishers` 비로그인 접근과 다른 요청의 인증 유지: PASS
- 지원 언론사 JPA Entity와 Repository 단위 검증: PASS
- 기존 `V0002__add_publisher_category.sql` Local 적용과 컬럼 확인: PASS (통합 전 이력, 현재 파일은 V0001에 통합)
- 지원 언론사 Entity와 Native MySQL 실제 Mapping 검증: PASS
- Native MySQL 테스트 Database·제한 계정 구성 Script: PASS
- 지원 언론사 Repository Native MySQL 통합 테스트 컴파일: PASS
- 테스트 Database·제한 계정 실제 생성과 DML 허용·DDL 차단: PASS
- 지원 언론사 Repository Native MySQL 통합 테스트 실행: PASS (1개, 실패·오류·Skip 0)
- 기사 URL HTTPS·허용 Host·Port·DNS/IP·Redirect 재검증: PASS
- Mock HTML 제목·게시일·본문 정제와 20,000자 제한: PASS
- 기사 수집 Mock 테스트: PASS (11개, 실패·오류·Skip 0)
- 초기 언론사 실제 추출 시험 실행기와 Local 보고서 경계: PASS
- 기사 외부 추출 제한 확정: PASS (압축 해제 후 원본 HTML 2 MiB, 단일 요청 Timeout 10초, Redirect 최대 3회)
- 초기 언론사 실제 외부 추출 시험: PASS (20곳 처리, 자동 추출 13곳, 본문 품질 수동 검토 통과 9곳)
- 현재 기술 지원 후보 정리: PASS (연합뉴스, MBC, SBS, 중앙일보, 한겨레, 경향신문, 국민일보, 매일경제, 한국경제)
- 언론사 지원과 기사 분야 판별의 책임 분리: PASS (건강 분석에만 개별 기사 분야 판별)
- 언론사 분류와 무관한 공개 조회 회귀 검증: PASS (통신·종합·경제·의료 분류 포함)
- 의료 전문 보완 후보 조사: PASS (8곳, 실제 추출 시험은 NOT RUN)
- 건강·의학·보건 개별 기사 분야 판별 경계: PASS (`PublisherArticleReader` 수집 후 교체 가능한 Port와 Mock Positive·Negative Fixture)
- 건강 분석 Use Case 분야 분기: PASS (관련 기사만 후속 Mock Port 전달, 비관련·판단 어려움은 사용자 안내 후 중단)
- 공통 비동기 분석 작업 상태 머신: PASS (90초 기한, 실행 중 5분·종료 상태 30분 만료, 늦은 결과·상태 역전 차단, 읽기 무변경 Mock 검증)
- Redis 작업 저장 Adapter와 실제 TTL·조건부 전환 검증: PASS (Docker Redis 8.8, 통합 테스트 7개, TTL·Version 전환·완료/실패 경쟁·장애 차단)
- 건강 분야 판별 실패 이용량 정책: PASS (회원 5회·비회원 2회, 첫 실패 무료, 이후 차감, 한국시간 자정 만료, 회원·비회원 Key 분리, Redis 원자 처리)
- 건강 분석 Use Case 이용량 연결: PASS (접수 전 Redis 확인, 비관련·판단 어려움만 실패 기록, 관련 기사는 실패 횟수 미기록)
- 건강 이용량 식별 경계: PASS (회원 ID 단일 Key, 비회원 Cookie·날짜별 IP 이중 Key, HMAC 비식별화, 한국시간 날짜 전환)
- 건강 이용량 Backend 단위 회귀: PASS (60개, 실패·오류·Skip 0; Redis IT 제외)
- 건강 이용량 Docker Redis 8.8 통합 검증: PASS (작업 상태 8개·이용량 10개, Redis `TIME` 기준 TTL 검증 포함)
- 분석 작업 소유권 Domain과 Application Polling 차단: PASS (회원·비회원 비식별 소유권 Key, 다른 소유자 조회 시 빈 결과)
- Redis 작업 소유권 저장과 변경 차단: PASS (`analysis-job:v2`, 소유권 Hash 저장·복원, 상태 변경 중 소유권 변경 거절)
- 건강 분석 비동기 HTTP 계약과 보안 Filter: PASS (비회원·회원 접수, CSRF, Cookie·작업 Token 전달, 진행 상태 조회, 동일 404, Port 미연결 503; Mock Application Port)
- 건강 분석 Queue·Worker와 완료·실패 결과 API: PASS (Redis Streams 최대 20개, 전역 Worker 1개, 무재시도, 90초 Deadline, 소유권 Polling, 종료 결과 원자 저장, Queue·Redis 장애 503)
- 건강 분석 Local Mock 연결: PASS (분야 판별·구조화 결과 Port, 외부 Gemini·검색 호출 없음, Spring Service·Worker Bean 조립)
- 건강 분석 Docker Redis 8.8 통합 검증: PASS (24개, 작업 상태·종료 결과·Stream Queue·Worker Lease·이용량 Namespace)
- 건강 분석 Backend 전체 회귀: PASS (Maven 83개, 실패·오류·Skip 0)
- 지원·일시 중단·현재 미지원 언론사 웹 표시 정책: PASS
- 지원 언론사 펼침 Figma Frame: PASS (`31:2`, `01-1 · 기능 선택 홈 · 지원 언론사 펼침`, 1440×1240)
- 지원 언론사 펼침 인터랙션 정의: PASS (동일 버튼 토글, `접기`와 `Escape` 닫기, 닫은 뒤 트리거로 Focus 복귀)
- Figma Prototype 연결: NOT RUN (기존 Prototype 미설정, Starter MCP 호출 한도로 웹 편집 우회)
- Frontend 지원 언론사 펼침 목록 구현: PASS (Backend 상태별 분류, 동일 버튼 토글과 `Escape` Focus 복귀)
- 지원 상태 공개 API 확장과 Frontend 동적 연결: PASS (응답 필드 유지, `UNSUPPORTED` 상태 추가, 정적 목록 제거)
- 지원 언론사 펼침 Browser 검증: PASS (Mock API, 1440·1280px 가로 넘침 없음, Runtime·Console 오류 없음)
- Local MySQL 실데이터 Backend 기동과 Browser E2E: PASS (Process 환경 변수 인증, 비로그인 `GET /api/publishers` 200, 실제 빈 배열과 Frontend 빈 상태를 1024·1280·1440px Headed Chrome에서 확인)
- 지원 언론사 초기 기준 데이터: PASS (추출 품질 통과 9곳 `ACTIVE`, 보완·재시험 대기 11곳 `CANDIDATE`, 허용 호스트 35건)
- 지원 언론사 초기 데이터 적용 Script: PASS (개발·테스트 Database 빈 Table 선행 조건, Process·`.env`·마스킹 입력, 기존 상태 덮어쓰기 차단)
- Native MySQL 개발·테스트 Database 초기 데이터 적용과 재검증: PASS (각 언론사 20건, 도메인 35건, `ACTIVE` 호스트 16건, `PAUSED` 호스트 19건)
- Local MySQL 상태별 언론사 실데이터 표시: PASS (`GET /api/publishers` 20건, 지원 중 9곳, 일시 중단 0곳, 현재 미지원 11곳)
- Native MySQL 일시 중단 실데이터 표시: PASS (테스트 Database의 `PAUSED_MANUAL` 1건으로 실제 API·Browser 확인 후 초기 상태 복원)
- 지원 언론사 실데이터 Browser E2E: PASS (1024·1280·1440px, Runtime·Console 오류와 가로 넘침 없음, Browser `/api/publishers` 200)
- 1024px 펼침 화면 가로 넘침: PASS (전역 `body` 최소 폭 제거 후 Headed Chrome의 1024·1280·1440px 전체 8개 Route와 펼침 상태 27건 재검증)
- 실제 시험 입력·원시 보고서·판정표의 Git 추적 제외: PASS
- 기사 전문·Secret 미저장: PASS (본문은 시작·끝 각 최대 160자 미리보기만 Local 보고서에 기록)
- Backend Maven 검증: PASS (30개, 실패·오류·Skip 0)
- 기사 Host와 MySQL 언론사·도메인 상태 연결: PASS (활성 언론사의 활성 별칭만 추출 허용)
- 후보·일시 중단·미등록 Host의 외부 HTTP 전 차단: PASS
- 언론사 도메인 Repository Native MySQL 통합 검증: PASS (MySQL 8.0.30, 언론사 상태 조인과 활성 별칭 조회)
- Backend Maven 재검증: PASS (36개, 실패·오류·Skip 0)
- 건강 분석 접수 API의 DB 상태 기반 기사 수집 진입점 호출: PASS (Worker에서 활성 언론사·URL 안전 검증·본문 추출·기사 분야 판별 연결)
- 기사 제목 분석 접수 API와 Use Case: PASS (비회원·회원 소유권, 독립 5·10회 한도, 안전 기사 수집, 건강 분야 판별·근거 검색 생략, Mock 결과 Polling)
- 기사 제목 분석 Redis Queue·이용량 통합 검증: PASS (전용 Namespace, 최대 20개, 단일 소비·무재시도, 한국시간 자정 만료)
- 기사 제목 분석 Backend 전체 회귀: PASS (Maven 97개, 실패·오류·Skip 0)
- PR 20 분석 작업 리뷰 보완: PASS (Queue 실패 정리, 로그인 후 비회원 Polling, 현재 이용량 응답, 차감 후 실패 정보, 명시적 Mock Provider, 예외 로그, Redis TTL 검사)
- Redis 작업 Key Version 배포 경계: PASS (`analysis-job:v1` 소유권 자동 추정 금지, 이전 Version 호환 구현 또는 최대 종료 TTL 30분 Drain 선행)
- PR 20 Docker Redis 통합 재검증: PASS (Docker Redis 8.8, 분석 작업·건강·제목 Queue와 이용량 통합 테스트)
- 건강 분석 Local Full-stack HTTP E2E: PASS (Native MySQL 8.0.30, Docker Redis 8.8, Mock 분석, 비회원 CSRF·소유권, 완료·실패 Polling, Redis 장애 `503`)
- 기사 제목 분석 Local Full-stack HTTP E2E: PASS (실제 지원 기사 URL, CSRF, Redis Streams Worker, 비회원 소유권, 완료 Polling, 이용량 차감)
- 기사 제목 분석 Frontend 실제 API 연결: PASS (클립보드 URL 접수, 2초 Polling, 결과 Route 이동, Browser Console 오류 0건)
- 건강 분석 Frontend 실제 API 연결: PASS (클립보드 URL 접수, CSRF, 비회원 작업 Token, 단계 Polling, 완료 결과 Route 이동)
- 건강 분석 Frontend Local Browser E2E: PASS (Native MySQL 8.0.30, Docker Redis, Mock 분석, 실제 지원 기사 추출, Runtime·Console warning/error 0건)
- 건강 분석 Frontend 실패·분야 중단 표시: PASS (Backend 오류 Code별 안전한 사용자 안내, 내부 Detail 비노출)
- 건강 분석 취소 경쟁 방지: PASS (취소 뒤 늦게 도착한 완료 결과 폐기)
- 건강 분석 자동 Browser E2E: PASS (설치된 Chrome, API Mock, 완료·새로고침·분야 중단·503·취소 4개 흐름)
- 건강 분석 Frontend 회귀 검증: PASS (Vitest 20개, TypeScript, ESLint, Vite Build)
- Local Redis 실행 경계: PASS (Windows Native Redis `6379`와 Docker Redis `6380` 분리, `.env` 기준 Backend 연결, 무인증 거부·인증 성공·Actuator `UP` 확인)

## Next Loop

1. Figma MCP 호출 가능 시 PNG 구현과 실제 Design Context 차이 재검증
2. 일반 로그인과 남은 저장·삭제·신고·인증 동작 연결
3. 현재 활성화 보류 11곳의 언론사별 추출 보완·재시험과 일반 언론사 지원 범위 확대

## Backend 표준화 상태

- Backend 개발 표준과 Harness 연결·Docs 검증: PASS
- AI Agent 외부 API·MCP 최소 호출 규칙: PASS (Local 근거 우선, Batch, 결과 재사용, 중복 호출·Quota 자동 재시도 금지)
- Astra·Sol 협업 역할과 Local Custom Agent TOML 문법: PASS
- 명시적 Sol 모델 호출을 통한 교차 검토: PASS
- Custom Agent 파일 자동 로딩·실행: NOT RUN (별도 CLI Sandbox의 인증·연결 환경 제약)
- API 상세 계약·결정: Git 제외 Local 문서에서 관리
- 지원 언론사 외 Backend 업무 구현·통합 테스트 환경 구성: NOT RUN (지원 언론사 Native MySQL 범위는 위 PASS 기록 참조)
- 기존 Frontend Target과 Next Loop 유지; Backend 작업 시 개발 표준과 관련 Local 계약 우선 확인

## Required Before Live OAuth

[실제 Provider 연동 검증 전에 OAuth Client ID와 Client Secret의 Backend 전용 Local 환경 설정 입력이 필요합니다.]

Frontend 환경 설정에는 공개 값만 저장하며, `VITE_` 변수에 Client Secret 등 비밀값을 넣지 않는다.

## Native MySQL Application

- Local Database와 애플리케이션 계정 설정: Git에서 제외된 `.env`
- Root 인증 저장: 사용하지 않음
- 초기 Schema와 JPA 기동 재검증: `scripts/agent/verify-native-mysql.ps1`

## 일반 회원가입과 이메일 인증 Vertical Slice

- 초대 코드·일반 계정 입력 API와 미인증 계정 생성: PASS
- 환경별 공용 초대 코드 5개 검증과 검증 완료 시각 기록: PASS
- 비밀번호 적응형 단방향 Hash와 민감 Request 문자열 마스킹: PASS
- Redis 6자리 인증번호 Digest, 24시간 TTL, 1분 재발송, 하루 5회, 실패 5회·30분 제한: PASS
- 이메일 인증 완료와 `PENDING_EMAIL` → `ACTIVE` 전환: PASS
- 실제 Gmail 호출 없는 교체 가능 발송 Port와 Local Mock: PASS
- 비로그인 회원가입 3개 POST 경로와 CSRF 유지: PASS
- 가입 중 Session 소유권과 활성 계정 인증 재호출 개인정보 반환 차단: PASS
- Redis 발급·발송 실패 시 미인증 계정과 인증 상태 보상 정리: PASS
- Frontend 회원가입 1·2·3단계 API 연결과 재발송 Countdown: PASS
- Frontend 휴대전화 번호 자동 하이픈 적용: PASS
- Backend 단위·MVC 회귀: PASS (12개, 실패·오류·Skip 0)
- Backend 전체 회귀: PASS (120개, 실패·오류·Skip 0; `*IT` 제외)
- Frontend Test·Lint·TypeScript·Build: PASS (20개)
- Docker Redis 8.8 이메일 인증 통합 테스트: PASS (3개, 평문 미저장·TTL·실패 제한·재발송 무효화)
- Chrome 회원가입 Browser E2E: PASS (API Mock, Runtime·Console 오류 0건)
- Native MySQL 회원가입 Repository 통합 테스트 소스와 Harness 연결: PASS
- Native MySQL 회원가입 Repository 실제 실행: NOT RUN (현재 Agent Process에 테스트 계정 비밀번호 없음)
- 실제 Gmail SMTP 발송: NOT RUN (이번 범위는 Mock Adapter)

## PR 16 보완 작업

- Task Understanding: 리뷰 8건의 중복을 합친 7개 항목과 사용자 승인 초기 Schema 통합
- Current Behavior (수정 전): DNS 검증 IP와 HTTP 연결 분리, DB 검증 경계 누락, 수정일 오류 코드 혼용
- Expected Behavior: 검증 IP 고정과 TLS 검증 유지, 빈 DB 초기화와 기존 DB 검증 분리
- Relevant Context: article 구현·테스트, Native MySQL Script, DATABASE_SCHEMA.md
- Affected Files: 기사 HTTP 경계, 날짜 오류·테스트, 초기 SQL·DB Script·테스트와 관련 Harness
- Risks: V0001은 Git 제외 Local 파일; 기존 DB DDL 재실행·Secret 변경·Git 이력 재작성 금지
- Implementation Plan: 승인된 HttpClient 5 도입 → 보안·날짜 회귀 수정; Sol의 DB 보완과 통합
- Verification Plan: 승인된 기사 수집·날짜·DB 초기화·연결 격리 경계에서 TDD → Backend/Harness → Self Review·Diff Review
- Backend clean verify: PASS (30개, 실패·오류·Skip 0; 실제 MySQL IT 제외)
- HTTP 회귀 검증: PASS (IP 고정·Host/SNI·TLS 거절·Redirect·압축 해제 크기·응답/전체 시간 제한)
- Native MySQL 연결 격리·Schema 정적 회귀 검증: PASS (PowerShell과 Java Guard)
- Frontend 회귀 검증: PASS (11개 Test, Lint, TypeScript와 Build)
- Harness 회귀·Infra·Docs·Secret 후보 검사: PASS (Compose는 설정 검사만 수행, Docker 설정 접근 경고 발생)
- Self Review·Diff Review·작업 트리/Stage 공백 검사: PASS
- 실제 GitHub Actions 재실행: NOT RUN (CI YAML 변경 없음)
- 실제 MySQL 재적용·외부 기사 추출: NOT RUN (이번 실행 대상 아님)
