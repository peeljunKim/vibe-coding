<!-- Local과 Prototype 실행 환경 설정 -->
# 기사체크 프로젝트 설정

## 책임 경계

### MySQL

- Host에 직접 설치한 Native MySQL Service
- Version SQL 기반 Schema 변경
- 사용자와 인증 정보
- 초대 코드 사용 이력
- 분석 작업 원장
- 저장 결과, 공유, 신고
- 지원 언론사와 감사 이벤트

### Redis

- 로그인 세션
- 3일 공용 분석 캐시
- 일일 이용량과 로그인 제한
- 분석 진행 상태
- 동일 기사 중복 분석 방지 Lock
- 언론사별 시간당 추출 상태

Redis 장애가 비용 제한을 우회하지 않도록 이용량이나 Lock을 확인하지 못하면 새 AI 분석을 시작하지 않는다.

### Prometheus와 Grafana

- Spring Boot Actuator의 `/actuator/prometheus` 수집
- 기본 수집 간격 15초
- 로컬 보관 기간 15일
- Grafana 데이터소스와 기본 대시보드 자동 등록
- 운영 환경에서는 Prometheus endpoint를 내부 네트워크로 제한

## 로컬 실행 순서

1. Oracle JDK 17.0.11을 Local Backend 기본 Java로 사용한다.
2. `.env.example`을 `.env`로 복사하고 모든 `replace-with-` 값을 로컬 비밀번호로 변경한다.
3. Windows MySQL Service를 실행하고 `DATABASE_SCHEMA.md`의 초기 SQL을 적용한다.
4. `docker compose up -d redis prometheus grafana`로 나머지 인프라를 실행한다.
5. `backend`에서 Maven Wrapper로 Spring Boot를 실행한다.
6. `frontend`에서 npm으로 Vite 개발 서버를 실행한다.
7. Prometheus Targets 화면에서 backend가 `UP`인지 확인한다.
8. Grafana의 `기사체크 서비스 개요` 대시보드를 확인한다.

## 환경 분리

- 기본 프로필: 로컬 개발
- 로컬 백엔드: `backend` 디렉터리에서 실행할 때 루트 `.env`를 선택적으로 로드
- `prod` 프로필: Secure Cookie, Redis TLS, JSON 구조화 로그
- 운영 비밀값은 파일에 기록하지 않고 배포 환경의 Secret 기능으로 주입

## Local 외부 서비스 설정

- Gemini API Key, Gmail App Password, OAuth Key·Secret의 사용자 직접 `.env` 입력
- Naver 서비스 URL `http://localhost:8080`
- Naver 로그인 Callback `http://localhost:8080/oauth/naver`
- Naver 연결 끊기 Callback `http://localhost:8080/oauth/naver/disconnect`
- Kakao 로그인 Callback `http://localhost:8080/oauth/kakao`
- Google 서비스 URL `http://localhost:8080`
- Google 로그인 Callback `http://localhost:8080/oauth/google`
- 공급자 Callback과 Backend 처리 경로 일치 확인 후 실제 연동

## Prototype 배포 결정

- AWS Free Plan의 단일 EC2 사용
- DuckDNS 무료 Subdomain 사용
- Reverse Proxy와 Blue/Green Application Container 전환
- MySQL은 EC2 Host Service와 영구 데이터 디렉터리 사용
- Redis는 API와 동일 EC2의 영구 Volume 사용
- 애플리케이션 배포 중 연결 유지와 실패 시 기존 Version 복귀
- EC2·MySQL·Redis 장애 고가용성은 Prototype 범위 밖
- Gemini 3.7 Flash 무료 등급과 PubMed NCBI E-utilities 사용

## Git 포함 기준

### 포함하는 설정

- `.env.example`, `frontend/.env.example`: 변수 이름과 비실제 예시만 제공하는 템플릿
- `application.yml`, `application-prod.yml`: 환경 변수 참조와 비민감 기본 설정
- `docker-compose.yml`: 로컬 인프라 구조와 필수 환경 변수 참조
- `infra/mysql/schema/*.sql`: Secret 없는 Version별 Schema 정의와 변경 이력
- Prometheus, Grafana, Redis 설정: Secret을 포함하지 않는 공유 인프라 설정

### 포함하지 않는 설정과 데이터

- `.env`, `.env.local`, 환경별 실제 `.env.*` 파일
- `application-local.yml`, `application-secret.yml`, `application-secrets.yml`
- API Key, OAuth Secret, Token, Password가 기록된 credentials 및 service account 파일
- Private Key, keystore, 인증서 개인키 파일
- Native MySQL 데이터 디렉터리와 Redis, Prometheus, Grafana의 Docker Volume 데이터
- 로그, 빌드 결과물, 테스트 결과, 캐시와 임시 파일

MySQL, Redis, Grafana 비밀번호는 기본값 없이 필수 환경 변수로 받는다. MySQL은 Native Service와 Backend 연결 단계에서, Redis와 Grafana는 Compose 시작 단계에서 검증한다.

## 아직 설정하지 않는 항목

- OAuth 공급자별 실제 Key·Secret
- DuckDNS Subdomain 이름과 Token
- AWS 계정 Free Plan 대상 여부와 실제 EC2 Instance Type
- 공식 기관별 근거 검색 Adapter
- 언론사 후보별 추출 결과와 활성화 상태
- 실제 사용자·분석 데이터 모델
- 운영 알림 채널과 OTLP 수집 대상

위 항목은 요구사항의 열린 질문이 해결되거나 해당 기능을 구현할 때 추가한다.
