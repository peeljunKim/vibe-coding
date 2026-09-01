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
- Schema 관리: Flyway·Liquibase 없이 Git Version SQL과 `schema_change_history`, JPA `ddl-auto: validate`
- Monitoring: Spring Boot Actuator, Prometheus, Grafana 구성
- AI: 비공개 Prototype에서 Gemini 3.7 Flash 무료 등급 사용
- Evidence Search: PubMed NCBI E-utilities와 허용된 공식 기관 자료 사용
- 초기 언론사: 통신·방송·종합·경제·건강 매체 20곳을 추출 시험 후보로 선정
- Deployment: AWS Free Plan의 단일 EC2, DuckDNS, Local과 동일한 Native MySQL 8.0.30과 동일 서버 Redis
- Availability: EC2 장애 대응이 아닌 Blue/Green 애플리케이션 배포 중 무중단만 보장
- E2E: Playwright, Vite Preview, PR Chromium, Release 전 Chrome·Edge 검증
- Local OAuth: 서비스 기준 URL `http://localhost:8080`
- Local OAuth Callback: Naver `/oauth/naver`, Naver 연결 끊기 `/oauth/naver/disconnect`, Kakao `/oauth/kakao`, Google `/oauth/google`
- Secret 입력 책임: Gemini API Key, Gmail App Password, OAuth Client Key·Secret은 사용자가 Local `.env`에 직접 입력
- 외부 연결 전 개발: Secret 준비 전에는 환경 변수 자리와 Mock으로 Local 기능 개발 진행
- 현재 구현: Frontend 초기 화면, Backend 부트스트랩과 기본 Security 설정, 각 모듈 단위 테스트 1개
- 상세 제품 정책: `MVP_REQUIREMENTS.md`
- 프로젝트 구조·위험: `docs/agent/project-context.md`

## 현재 구현 경계

- 요구사항에 정의된 분석·회원·공유·신고 Domain 구현은 아직 없음
- JPA Entity, Repository, API Controller, 외부 AI·검색 연동 구현은 아직 없음
- `backend` 설명에 언급된 Worker 구현은 아직 없음

## Deferred

[실제 Gemini, OAuth, Gmail SMTP Secret은 Local 연동 시 사용자가 `.env`에 직접 입력해야 합니다.]

[초기 언론사 후보의 실제 기사 추출 테스트 결과가 필요합니다.]

[Local 개발 완료 후 사용할 DuckDNS 서브도메인 이름이 필요합니다.]

[Local 개발 완료 후 AWS 계정의 Free Plan 대상 여부 확인이 필요합니다.]
