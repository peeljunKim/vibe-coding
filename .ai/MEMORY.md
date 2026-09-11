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
- E2E: Playwright, Vite Preview, PR Chromium, Release 전 Chrome·Edge 검증
- Local OAuth: 서비스 기준 URL `http://localhost:8080`
- Local OAuth Callback: Naver `/oauth/naver`, Naver 연결 끊기 `/oauth/naver/disconnect`, Kakao `/oauth/kakao`, Google `/oauth/google`
- Secret 입력 책임: Gemini API Key, Gmail App Password, OAuth Client Key·Secret은 사용자가 Local `.env`에 직접 입력
- 외부 연결 전 개발: Secret 준비 전에는 환경 변수 자리와 Mock으로 Local 기능 개발 진행
- 현재 구현: Frontend Desktop 화면, Backend 부트스트랩·기본 Security, 지원 언론사 공개 조회, 기사 URL 안전 검증·Mock 본문 추출, 관련 단위·보안 Filter 테스트
- 상세 제품 정책: `MVP_REQUIREMENTS.md`
- 프로젝트 구조·위험: `docs/agent/project-context.md`
- Backend 구현 표준: `docs/agent/backend-development.md` (채택 기준, 업무 기능 구현 완료 아님)
- Astra·Sol 역할과 실행 설정: `docs/agent/agent-collaboration.md` (Local Custom Agent와 명시적 모델 위임)

## 현재 구현 경계

- 요구사항에 정의된 분석·회원·공유·신고 Domain 구현은 아직 없음
- 지원 언론사 외 JPA Entity·Repository·API Controller와 외부 AI·검색 연동은 아직 없음
- 지원 언론사 분류 후속 Schema는 Local 적용됨; 사용자 승인으로 초기 SQL에 통합, 기존 DB 재적용 없이 검증
- 기사 HTTP: Apache HttpClient 5의 요청별 고정 DNS 주소, TLS Host 검증 유지; Jsoup는 HTML 분석 담당
- 지원 언론사 Native MySQL 통합 테스트용 별도 Database·제한 계정 구성과 실제 Repository 검증 완료
- `backend` 설명에 언급된 Worker 구현은 아직 없음

## Deferred

[실제 Gemini, OAuth, Gmail SMTP Secret은 Local 연동 시 사용자가 `.env`에 직접 입력해야 합니다.]

[현재 활성화 보류 11곳의 언론사별 추출 보완과 재시험이 필요합니다.]

[추가 일반 언론사 확대 우선순위를 정할 이용 빈도 또는 선정 기준이 필요합니다.]

[의료 전문 추가 후보 8곳의 실제 기사 추출 시험이 필요합니다.]

[지원 언론사 펼침 목록의 Figma 디자인과 인터랙션 정보가 필요합니다.]

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
