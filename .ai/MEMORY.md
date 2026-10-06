<!-- Repository 고정 사실과 미확정 정보 -->
# 프로젝트 고정 Context

## Confirmed

- 일반 사용자와 어르신을 위한 국내 한국어 기사 확인 비공개 웹 MVP
- 독립 기능: `건강·의학 뉴스 확인`, `기사 제목 확인`
- Frontend: React 19, TypeScript 5.9, Vite 8, npm
- Backend: Java 17 대상, Spring Boot 4.1.0, Maven
- Local Java 17: Oracle JDK 17.0.11 실행 확인, Scoop OpenJDK 17.0.2 예비 경로 실행 확인
- Persistence와 상태: Local MySQL 8.0.30 Native Service, JPA, Redis 8 기반 세션·캐시 구성
- Local MySQL: Windows Service Binary와 전용 Client 8.0.30, Scoop 기본 Client 9.7.1은 Schema 작업에 사용하지 않음
- Schema 관리: Flyway·Liquibase와 DB 이력 Table 없이 Local 전용 초기 SQL, 이후 GitHub Version SQL·Commit·PR 이력, JPA `ddl-auto: validate`
- Monitoring: Spring Boot Actuator, Prometheus, Grafana 구성
- AI: 비공개 Prototype에서 Gemini 3.7 Flash 무료 등급 사용
- Evidence Search: PubMed NCBI E-utilities와 허용된 공식 기관 자료 사용
- 기사 외부 추출 제한: 압축 해제 후 원본 HTML 2 MiB, 단일 요청 Timeout 10초, Redirect 최대 3회
- 초기 언론사 실제 추출 시험: 20곳 중 자동 추출 13곳, 본문 품질 수동 검토 통과 9곳
- 언론사 지원 기준: 의료 전문 여부가 아닌 국내 이용 가능성·URL 안전성·공개 접근·추출 품질
- 기사 분야 기준: `건강·의학 뉴스 확인`에서만 추출된 개별 기사의 건강·의학·보건 관련성 판별, `기사 제목 확인`은 모든 분야 허용
- 언론사 공개 상태: 관리 중인 언론사를 `지원 중`, `일시 지원 중단`, `현재 미지원`으로 구분해 웹에 표시하며 실패 언론사는 추출 보강·재시험 후 활성화
- 현재 기술 지원 후보: 연합뉴스, MBC, SBS, 중앙일보, 한겨레, 경향신문, 국민일보, 매일경제, 한국경제
- 의료 전문 보완 후보: 청년의사, 의협신문, 데일리메디, 메디게이트뉴스, 라포르시안, 병원신문, 메디칼업저버, 의학신문
- Deployment: AWS Free Plan의 단일 EC2, DuckDNS, Local과 동일한 Native MySQL 8.0.30과 동일 서버 Redis
- Availability: EC2 장애 대응이 아닌 Blue/Green 애플리케이션 배포 중 무중단만 보장
- E2E: Playwright, Vite 개발 서버, PR Chromium, Release 전 Chrome·Edge 검증
- Local MVP 전체 회귀: `verify-local-mvp.ps1`이 Repository 전체 검증 → Native MySQL IT → Docker Redis IT → Playwright E2E를 기존 Mock Adapter로 순차 실행하며 PR 39 재분석 취소 경쟁 보완 후 전체 PASS 재확인
- Agent Script 구성: 직접 실행하는 Setup·검증 진입점은 권한·필수 서비스별로 유지하고, 반복되는 `.env` 값 처리와 Java 17 탐색은 `script-utilities.ps1`, Harness·Local MVP 실행 순서 회귀는 `verify.Tests.ps1`로 통합; 통합 후 Local MVP와 Full-stack Smoke 실제 환경 검증 PASS
- Local Full-stack Smoke: `verify-full-stack-smoke.ps1`이 실제 Browser·Backend HTTP·Native MySQL 테스트 Database·실행별 Docker Redis를 연결하고 외부 Gemini·PubMed·OAuth·Gmail SMTP 없이 핵심 사용자 흐름을 검증함
- Local API 계약: `docs/api/openapi.json`은 Controller 기준 구현 Operation 37개를 기록하며, 상세 문서는 Git 제외 Local 전용으로 유지
- Local OAuth: 서비스 기준 URL `http://localhost:8080`
- Local OAuth Callback: Naver `/oauth/naver`, Naver 연결 끊기 `/oauth/naver/disconnect`, Kakao `/oauth/kakao`, Google `/oauth/google`
- Secret 입력 책임: Gemini API Key, Gmail App Password, OAuth Client Key·Secret은 사용자가 Local `.env`에 직접 입력
- 회원가입 이메일 발송: 기본 Profile은 Mock, `smtp`·`prod` Profile은 Gmail SMTP Adapter 사용
- 미인증 일반 계정: 가입 후 7일 경과 시 일일 정리, 공개 중복 오류는 계정 정보 단일 코드 사용
- 외부 연결 전 개발: Secret 준비 전에는 환경 변수 자리와 Mock으로 Local 기능 개발 진행
- 현재 구현: Frontend Desktop 화면·도움말과 Backend 상태 기반 지원 언론사 펼침 목록, 전체 지원 상태·기능별 당일 이용량 공개 조회, DB 언론사·도메인 상태 기반 기사 수집, 기사 URL 안전 검증, 건강 분석 비동기 HTTP·Redis Streams Queue·단일 Worker·Mock 분석 결과 Polling, 건강·제목 분석 결과 7일 공유, 분리된 3일 공용 Cache, 건강 근거 링크 재검증 Port·Mock·운영 HTTP Adapter와 PubMed 검색 Port·Mock·NCBI E-utilities HTTP Adapter
- 상세 제품 정책: `MVP_REQUIREMENTS.md`
- 프로젝트 구조·위험: `docs/agent/project-context.md`
- Backend 구현 표준: `docs/agent/backend-development.md` (채택 기준, 업무 기능 구현 완료 아님)
- Astra·Sol 역할과 실행 설정: `docs/agent/agent-collaboration.md` (Local Custom Agent와 명시적 모델 위임)

## 현재 구현 경계

- 일반 회원가입·이메일 인증, 일반 로그인·로그아웃 Redis Session, Google·Naver·Kakao OAuth2 Redirect 로그인·초대 가입, OAuth Provider 활성화 공개 조회, 계정 복구, 회원 탈퇴 7일 복구·신청 후 30일 보관 삭제, 건강 분석 결과 저장·만료 정리, 문제 신고·관리자 처리와 건강·제목 분석 결과 공유는 구현됨; 실제 Google·Naver·Kakao Provider의 로그인·Callback·내부 Session 전환 Smoke는 PASS
- 실제 Gemini 연동은 아직 없으며 PubMed 검색은 원문 주장·정규화 영문 Query·주장별 최대 5개·Deadline을 받는 Port, 외부 호출 없는 Mock과 NCBI ESearch·EFetch HTTP Adapter가 구현됨; 제한된 실제 NCBI Smoke Test는 ESearch 2회·EFetch 1회로 PASS했고 EFetch 표준 `DOCTYPE`과 명명 Entity는 외부 DTD 접근 없이 안전하게 변환하며 응답 본문 수신에도 Timeout·크기 제한을 적용함
- 지원 언론사 분류와 건강 저장 중복 방지 후속 Schema는 사용자 승인으로 초기 SQL V0001에 통합됨; V0002·V0003 번호는 폐기하고 기존 개발·테스트 DB는 전용 정렬 Script로 6개 표준 `INT UNSIGNED` 컬럼과 중복 방지 UNIQUE Metadata 검증 PASS
- 기사 HTTP: Apache HttpClient 5의 요청별 고정 DNS 주소, TLS Host 검증 유지; Jsoup는 HTML 분석 담당
- 지원 언론사 Native MySQL 통합 테스트용 별도 Database·제한 계정 구성과 실제 Repository 검증 완료
- 회원 탈퇴 Native MySQL 수명주기 검증 완료: 세션 만료 선행 변경 후에도 7일 복구, 신청 후 30일 삭제와 Foreign Key CASCADE 경계 PASS
- 도움말 `/help` 화면은 로그인·건강 분석 결과에서 진입하며 기능 선택, 이용 횟수, 결과 해석과 지원 제한을 안내함; Frontend 16개 Test File·76개 Test와 Desktop 1024·1280·1440px Browser E2E PASS
- 분석 작업 상태에는 회원·비회원 비식별 소유권 Key를 함께 저장하며 다른 소유자의 Polling 조회는 빈 결과로 처리
- 건강 분석 HTTP Adapter는 비회원·회원 접수와 Polling을 Redis 작업 상태·결과에 연결하고 Queue 포화·Redis 장애를 `503`으로 처리
- 건강 분석 Worker는 Redis Streams 최대 대기 20개, 전역 동시 실행 1개, 자동 재시도 없음, 90초 Deadline과 늦은 결과 폐기를 적용
- Local 건강 분석은 외부 API를 호출하지 않는 Mock 분야 판별·구조화 결과 Port를 사용
- 건강 분석 저장 기록은 한국시간 매일 03:10에 `expires_at <= 현재 시각` 조건으로 삭제하며 하위 기록은 기존 Foreign Key Cascade를 사용
- 로그인 회원은 건강 분석 저장 기록을 개별·전체 삭제할 수 있으며, 재분석 성공 뒤 기존 Aggregate를 새 결과로 원자 교체함; 교체 실패 시 기존 기록을 유지하고 성공 시 이전 공유 링크는 Cascade 삭제하되 독립 신고 Snapshot은 유지함; 교체 대기 중 취소된 요청의 늦은 성공·실패 응답은 화면에 반영하지 않음
- 문제 신고 Vertical Slice의 최신 변경 범위 Harness 검증은 PASS이며 Backend 185개 Test와 Frontend 10개 Test File·57개 Test를 통과함; Native MySQL 신고 Repository 실제 실행도 PASS
- 공유 Token은 256bit Base64URL 원문을 URL Fragment와 Header로만 전달하고 MySQL에는 SHA-256 Digest만 저장함
- 공개 공유 조회는 비로그인 읽기 전용·`no-store`·검색 수집 차단이며 AI·검색·기사 추출·이용량 Port를 호출하지 않음
- 공유 Open Graph는 Fragment Token 보안을 유지하는 공통 메타데이터와 1200×630 대표 이미지를 사용하며 결과별 동적 미리보기는 제공하지 않음
- 공유 Vertical Slice의 Backend 전체 196개 Test, Frontend 14개 Test File·70개 Test, Chrome 공유 E2E 4개와 Native MySQL ShareStoreIT 실제 실행은 PASS이며 기사 게시·수정 시각 UTC 변환 회귀도 실제 MySQL에서 검증함
- 건강·제목 공용 Cache는 정규화 URL·기능·모델·정책·언론사 정책 Version을 SHA-256 Key로 분리하고 건강 기능에는 근거 허용 목록 Version도 포함함
- Redis Cache에는 구조화 결과·만료 시각·비원문 기사 Fingerprint만 저장하고 기본 3일에서 Key 기반 최대 30분 감산 지터를 적용하며 조회로 TTL을 연장하지 않음
- Cache Hit은 안전한 기사 수집 1회로 제목·게시/수정 시각·순서형 문단 Hash를 비교하며 분야 판별·검색·AI Port는 호출하지 않음; 변경 기사에는 이용량 차감 전 `ARTICLE_CHANGED`를 반환함
- 원 분석 회원의 재조회는 Cache 수명 동안, 비회원은 날짜별 식별 경계 안에서 미차감하고 다른 사용자의 변경 없는 결과 최초 열람만 원자적으로 차감함
- 기사 변경 시 건강·제목 화면에서 명시적 재분석 확인·취소를 제공하고, 동의 요청만 Queue 표시를 거쳐 기존 분석·이용량 흐름과 Cache 교체를 실행함; Backend 전체 233개 Test, Docker Redis 통합 44개 Test, Frontend 17개 Test File·80개 Test와 Chrome 전체 E2E 20개 PASS
- Cache 건강 근거 링크는 교체 가능한 Port로 중복 제거 후 확인하고 일시 오류를 1회 재확인함; 사라진 링크가 있으면 Cache를 제거하고 전역 단일 Worker Lease 안에서 이용량 미차감 자동 재분석을 실행하며 실패 시 깨진 근거 의존 주장을 제거한 제한 결과와 재계산한 확인률을 Cache에 저장함
- 건강 근거 링크 운영 HTTP Adapter는 `evidence-http` Profile의 명시적 허용 Host 또는 `pubmed-http` Profile의 고정 PubMed Host에서 활성화되며 HTTPS·공개 IP·Redirect 재검증, 10초 Timeout·Redirect 최대 3회·1 KiB 응답 제한을 적용함; 2xx는 정상, 404·410은 누락, DNS·전송·그 밖의 HTTP 오류는 일시 오류로 분류하고 두 Profile이 모두 없으면 외부 호출 없는 Mock을 유지함
- 건강·제목 분석 접수는 기사 수집·Queue 적재 전에 기능별·사용자별 Redis 고정 시간 제한을 적용함; 각 기능 1분 5회, HMAC 식별값 추가 Digest, 비회원 다중 식별 신호 카운터 동기화, 초과 `429`, Redis 장애 `503`, Polling·자동 근거 재분석 제외
- Frontend는 건강·제목 분석의 `429 ANALYSIS_REQUEST_RATE_LIMIT_EXCEEDED`를 공통 사용자 안내로 표시하고 Polling을 시작하지 않으며 Desktop 1024·1280·1440px에서 오류 화면을 검증함
- `GET /api/usage`는 회원·비회원의 건강·제목 당일 한도·사용·남은 횟수와 다음 한국시간 자정을 공개 조회하며, 조회만으로 횟수나 Redis TTL을 변경하지 않음; 식별 준비·Redis 가용성 오류는 `503 USAGE_SERVICE_UNAVAILABLE`, Frontend는 초기·로그인·로그아웃·분석 완료 뒤 갱신하고 겹친 요청의 오래된 응답을 폐기하며 실패해도 분석 버튼을 유지함

## Deferred

[실제 Gemini, Kakao OAuth, Gmail SMTP Secret은 Local 연동 시 사용자가 `.env`에 직접 입력해야 합니다.]

[현재 활성화 보류 11곳의 언론사별 추출 보완과 재시험이 필요합니다.]

[추가 일반 언론사 확대 우선순위를 정할 이용 빈도 또는 선정 기준이 필요합니다.]

[의료 전문 추가 후보 8곳의 실제 기사 추출 시험이 필요합니다.]

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[운영 `evidence-http` Profile에 추가할 공식 기관의 정확한 허용 Host 목록이 필요합니다. PubMed Host는 `pubmed-http` Profile에서 고정 허용합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
