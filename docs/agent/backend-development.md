<!-- Spring Backend 구현 표준 -->
# 기사체크 Backend 개발 표준

## 상태와 근거

사용자가 초안 적용과 미정 기준 결정을 위임한 구현 표준이다. 아래의 **채택 기준**은 앞으로 구현할 규칙이며 실행 중 기능을 뜻하지 않는다. 현재 동작은 실제 코드로, 목표는 사용자 결정과 요구사항으로 구분한다.

| 구분 | 확인된 값 | 근거 |
| --- | --- | --- |
| 언어·Framework | Java 17 대상, Spring Boot 4.1.0 | `backend/pom.xml` |
| Build | Maven 3.9.11, Wrapper 3.3.4 설정 | `backend/.mvn/wrapper/maven-wrapper.properties` |
| Web·보안 | MVC, Validation, Security, OAuth2 Client | `backend/pom.xml` |
| 저장·세션 | JPA, Redis, Spring Session Redis | `backend/pom.xml` |
| MySQL | Native MySQL 8.0.30 기준 | `docs/architecture/DATABASE_SCHEMA.md` |
| Redis | `redis:8.8-alpine` 설정, 실행 버전 미검증 | Local `docker-compose.yml` |
| 외부 처리 | Jsoup 1.21.2, Apache HttpClient 5 (Boot 관리), Spring Mail | `backend/pom.xml` |
| 테스트 | Boot Test, Security Test, Testcontainers 2.0.5 의존성 | `backend/pom.xml` |
| 관측 | Actuator, Prometheus registry | `backend/pom.xml` |

Boot 관리 Dependency의 세부 버전은 실제 effective POM/dependency tree로 확인한다. JUnit Jupiter import만으로 JUnit 세대나 세부 버전을 추정하지 않는다. Java 17에서 지원하지 않는 Virtual Thread와 최신 버전 예제의 import를 그대로 사용하지 않는다. Wrapper launcher가 없을 때는 기존 `scripts/agent/verify.ps1`의 Wrapper JAR 경로를 사용한다.

`backend/pom.xml`에서 Dependency를 추가·삭제하거나 버전을 변경할 때는 작업 전에 다음 내용을 사용자에게 보고하고 명시적 승인을 받는다. 승인 전에는 `pom.xml`을 수정하거나 Dependency를 내려받지 않는다.

- 변경 이유
- 기존 Dependency로 해결할 수 없는 이유
- 영향 범위
- 롤백 방법

현재 코드는 Application, HTTP Basic·Cookie CSRF 설정, 지원 언론사 조회·JPA와 기사 URL 검증·본문 추출 범위다. OAuth와 그 밖의 업무 기능·작업 실행기는 미구현이다. `@EnableAsync`만으로 Executor·작업 복구·분산 실행이 구현됐다고 판단하지 않는다.

## 읽기와 계약 관리

- 실제 구현 → 관련 설정 → 테스트로 현재 동작 확인, 목표는 사용자 결정 → 요구사항 → 승인된 계약 순서로 확인
- Local API 작업 시 `docs/api/README.md` → `docs/api/decisions.md` → 해당 OpenAPI operation만 읽기
- Local 계약이 없는 Clean Clone에서는 계약을 추측하지 않고 해당 문서 제공 요청, 독립적인 작업만 계속 수행
- 상세 Endpoint·Payload·Cookie 이름·Origin·업무 Enum 매핑은 `docs/api/`에만 기록
- Git 추적 테스트에는 요청 기능의 동작 검증에 필요한 내용만 포함하고 별도 계약 전문·생성된 명세·실제 Secret 복사 금지
- 구현 시 계약과 테스트를 함께 갱신하되 CI에 Local API 문서나 초기 SQL이 있다고 가정하지 않음
- 충돌 시 코드의 현재 상태와 목표 차이를 먼저 보고, 새 표준 때문에 기존 사용자 코드를 일괄 리팩터링하지 않음

## 채택 기준: 계층과 Java

Root `com.newsverification`과 기존 `config`를 유지하고 기능 중심으로 Package를 추가한다. 기능 안에 필요한 `api`, `application`, `domain`, `infrastructure`만 만든다. 분석 두 기능은 정책과 데이터 책임을 분리한다. 실제 공통 책임이 생기기 전 `common`을 만들지 않는다.

| 계층 | 책임 | 경계 |
| --- | --- | --- |
| api | HTTP 변환, DTO 형식 검증, 인증 주체 전달 | Repository·외부 Client 직접 호출 금지 |
| application | Use Case, 소유권, Transaction, 작업 조정 | HTTP 응답 타입을 업무 로직에 전파 금지 |
| domain | 판정·불변 조건·상태 전환, Entity | HTTP·Redis·Provider DTO에 의존 금지 |
| infrastructure | JPA Repository, Redis, 기사·AI·메일 Adapter | 외부 응답을 검증·변환 후 전달 |

JPA Entity의 Persistence Annotation은 허용한다. Domain 모델과 Entity를 항상 이중으로 만들거나 Service마다 interface/Impl을 만들지 않는다. 교체 경계가 필요한 외부 Client 등에만 interface를 둔다. 생성자 주입과 final 의존성을 기본으로 한다. Lombok·MapStruct·QueryDSL 등 현재 없는 도구를 표준 적용만으로 추가하지 않는다.

Request/Response와 Entity를 분리한다. 단순 비민감 불변 DTO는 record를 우선 사용하되 비밀번호·인증번호를 담는 DTO는 자동 toString 노출을 막는다. Entity는 공개 setter 대신 의미 있는 상태 변경 메서드를 제공하고 equals/toString으로 연관관계를 순회하지 않는다.

## 채택 기준: API·Validation·예외

- 채택된 Local 계약에 맞춰 Method·Header·HTTP 상태·Schema 구현
- 성공은 Resource 본문 직접 반환, 목록은 전용 Pagination DTO 사용, Spring Page/Entity 직렬화 금지
- JSON camelCase, Enum 문자열 사용, API·DB의 의미가 같으면 기존 Schema 값을 우선 재사용
- DTO에서 필수·형식·길이, Domain에서 업무·상태, DB에서 UNIQUE/FK/CHECK 무결성 검증
- 다중 필드 조건·소유권·중복 방지는 Bean Validation만으로 대체하지 않음
- 본문 없는 성공에는 본문 생성 금지, 오류를 HTTP 성공 상태로 숨기지 않음
- 공통 HTTP 오류는 RFC 9457 ProblemDetail과 안정적인 code 사용
- `@RestControllerAdvice`/`ResponseEntityExceptionHandler`에서 MVC 오류 변환
- Security Filter의 인증·인가 실패는 AuthenticationEntryPoint/AccessDeniedHandler에서 같은 오류 형식으로 변환; ControllerAdvice가 Filter 예외까지 처리한다고 가정하지 않음
- 업무 예외는 의미 중심으로 정의하고 최외곽 처리 경계에서 한 번만 로깅
- 검증 오류에 거절된 원본 입력값을 담지 않음; SQL·Stack Trace·내부 주소·Secret 노출 금지
- 비동기 작업 실패는 작업 상태의 오류이며 Polling HTTP 요청 자체의 실패와 구분
- 알 수 없는 시스템 예외를 성공 값이나 빈 결과로 변환하지 않음

## 채택 기준: 인증·보안

Session 인증과 OAuth Redirect를 목표로 유지한다. 현재 HTTP Basic을 최종 로그인 구현으로 보지 않는다. Security 정책의 코드 반영 시 영향·롤백을 먼저 설명하고 기존 승인 범위를 확인한다.

- 인증 정보는 서버에서 확인, 요청 Body의 사용자 ID·역할을 인증 주체로 신뢰 금지
- 인증과 자원 소유권을 별도로 검사, 관리자 기능은 서버 측 ADMIN 검사
- 요청 레벨 경계와 필요 시 명시적으로 활성화한 Method Security 병행
- 비밀번호는 Spring Security PasswordEncoder의 적응형 단방향 해시로 저장, 평문·가역 암호화 저장 금지
- 세션 고정 공격 방지, 로그아웃과 비밀번호 변경·재설정의 세션 만료 검증
- 기본 세션과 유지 로그인 수명은 요구사항대로 구현; 설정값 존재를 기능 검증으로 오인 금지
- Cookie 기반 상태 변경 요청의 CSRF 유지, SPA Token 생성·갱신·전송을 실제 Filter 통과 테스트로 확인
- OAuth state 검증, 고정된 Redirect 허용 대상, 초대 검증 전 제한된 가입 상태
- 이메일로 자동 계정 병합 금지, Provider 고유 식별자 사용
- CORS는 인증 대체 수단이 아님; Credential과 wildcard Origin 혼용 금지
- 공용 캐시·운영 로그에는 개인정보 금지; 업무용 세션 속성은 최소 사용자 ID·권한만 보관. Spring SecurityContext와 OAuth 임시 인증 상태는 별도 인증 책임으로 최소화하고 Provider 원본 사용자 응답을 그대로 저장하지 않음
- OAuth authorized-client Token 저장은 서버 전용 접근·만료·삭제 정책을 갖춘 저장소로 분리하고 필요 없는 장기 Token 보관 금지
- Secret은 Backend 전용 Local 환경 설정; Frontend 공개 변수나 인계·테스트 Fixture·문서에 복사 금지
- Actuator는 현재 공개 경로와 운영 공개 범위를 구분하고 운영 보호 여부 검증

## 채택 기준: Transaction·JPA·DB

- Use Case의 짧은 변경 구간에 Spring `@Transactional`, 필요한 조회에는 readOnly 사용
- 외부 기사·AI·검색·SMTP 호출을 DB Transaction이나 Lock 안에서 실행하지 않음
- Transaction/Async Proxy 자기 호출, 새 Thread로의 Transaction 자동 전파에 의존 금지
- 예외별 rollback 조건 확인; 예외를 잡아 부분 Commit하는 경로 방지
- MySQL과 Redis는 한 Transaction이 아니므로 실패 순서와 보상·중복 처리 기준을 명시
- JPA `open-in-view=false`, `ddl-auto=validate`, DB UTC 유지
- 시점은 Instant와 주입 Clock, 업무 날짜는 명시적인 업무 시간대로 계산
- Entity의 길이·정밀도·NULL·Enum·연관관계를 현재 Schema와 대조
- 조건부 갱신·UNIQUE 제약 등으로 실제 경쟁 조건 보호; 무조건 Lock 추가 금지
- 조회별 Fetch Join/EntityGraph/Projection을 선택하고 N+1, 무제한 조회, 불안정한 정렬 검사
- readOnly는 쓰기 권한 차단 장치가 아님; Cascade/orphanRemoval은 소유·삭제 책임 확인 후 적용
- 초기 SQL Local 전용, 후속 변경은 `DATABASE_SCHEMA.md`의 Version SQL·Commit·PR 절차 준수
- 적용된 SQL 수정·자동 DDL·DB Schema 이력 Table 생성 금지; 파괴적 작업은 기존 승인 규칙 준수
- Enum ordinal 저장 금지; 표준 채택으로 기존 Schema를 자동 변경하지 않음

## 채택 기준: Redis·비동기·외부 호출

- Session·캐시·이용량·인증 제한·작업 상태·Lock의 Key 공간과 수명 분리
- 공용 캐시에 기사 원문·개인정보 금지; 인증 전용 임시 저장소는 최소 식별자와 검증용 digest만 보관
- TTL 누락과 읽기에 의한 의도하지 않은 수명 연장 방지
- 건강·제목 캐시 분리, 모델·정책·출처 목록 버전으로 무효화
- 실행기 Thread·Queue 크기는 유한하게 설정; 수용 실패 시 접수 성공·이용량 차감 처리 금지
- 비동기 Polling 채택; 시간·소유권·상태 전환은 Local 결정 문서 적용
- 작업 만료·Worker 종료·늦은 결과에서 실패→성공 역전과 중복 차감 방지
- 단일 EC2에서도 Blue/Green 실행기가 공존하므로 Redis 상태 전환·Lock 소유권 확인
- 이용량·Lock 검증 불가 시 새로운 AI 호출 차단; DB와 Redis 성공 처리 순서 테스트
- JSON 등 명시적인 직렬화 모델 사용, 변경 시 이전 Version 데이터 호환성 검토
- KEYS/전체 Key 삭제 금지; 테스트 정리도 해당 실행 Namespace만 대상
- 외부 Client는 기존 MVC와 맞는 동기 HTTP Client를 우선 검토; 비동기라는 이유만으로 WebFlux·MQ 추가 금지
- URL 정규화, HTTPS·허용 Host·Port·DNS/IP·매 Redirect·응답 크기·시간 검사 필수
- 일반 분석의 자동 재시도 금지; 요구사항에 명시된 근거 링크 재확인·자동 재분석만 별도 경로로 허용
- AI 출력 구조와 고정 판정 계산, 출처 주소를 서버에서 재검증

## 채택 기준: 로그와 주석

- 사용자 취소 INFO, Provider 일시 오류 WARN, 서버 설정·내부 장애 ERROR
- 보안 검증 실패 WARN, 반복 변조·내부 검증 장애는 근거와 함께 ERROR
- Provider·내부 오류 분류·비민감 요청 추적 ID 중심, 동일 예외 중복 로그 금지
- Authorization Code·Token·Cookie·전체 이메일·전화번호·기사 원문·개인정보 로그 금지
- Micrometer MDC 키를 보존; 로그 패턴만으로 실제 분산 추적이 있다고 주장하지 않음
- 역할이 드러나는 Class/Method 이름, 짧은 한국어 명사형 주석과 마침표 생략
- 파일 첫 부분 역할 주석, 비자명한 설계 의도만 설명; JSON·생성물·Lockfile 주석 제외

## 채택 기준: 테스트와 검증

| 범위 | 기준 |
| --- | --- |
| Unit | Spring 없이 판정·상태·차감·시간 경계 테스트, Clock 고정 |
| MVC | MockMvc로 실제 Filter·Validation·상태·Header·오류 계약 검증 |
| Application | 소유권·중복·실패 시 기존 결과 보존·계층 경계 |
| MySQL | Native MySQL 8.0.30의 별도 테스트 DB·제한 계정, 실제 FK/UNIQUE/CHECK·트랜잭션·쿼리 검증 |
| Redis | 앱과 분리된 동일 이미지의 테스트 전용 인스턴스, 실행별 Namespace, 실제 TTL·경쟁·장애 검증 |
| 외부 연동 | 기본 Mock/Fixture, 실제 Provider 요청은 승인된 Smoke Test로 구분 |

MySQL Docker 도입은 하지 않는다. 현재 Testcontainers 의존성은 사용 완료 증거가 아니며 자동으로 제거하거나 테스트 환경을 대체하지 않는다. H2를 MySQL 호환성 증거로 사용하지 않는다.

통합 테스트는 전용 환경의 식별·버전·대상을 확인한 후 실행한다. Local 설정에 테스트 접속값을 두고 앱/운영 DB를 기본 fallback으로 사용하지 않는다. 초기 SQL은 별도 공급하므로 Clean Clone에 포함된 것으로 가정하지 않는다. 환경이 없으면 해당 검증을 NOT RUN으로 보고하고 성공 처리·조용한 Skip 금지. CI 구성과 테스트 인스턴스 생성은 구현 작업에서 별도로 진행한다.

`*Test`는 기존 테스트 실행 대상에 맞춘다. `*IT`를 만들 때 실제 Maven 실행 연결을 검증하고 파일 존재만으로 통합 검증을 보고하지 않는다. Java 17·Boot 4.1.0의 실제 Test Module/import를 확인하고 필요한 의존성 변경은 기존 승인 규칙을 따른다.

구현 검증은 관련 테스트 → `pwsh -NoProfile -File scripts/agent/verify.ps1 -Scope backend` → 필요한 통합 검증 순서다. 문서만 수정할 때는 Docs 검증과 Local 계약 검증을 사용한다. 완료 전 `backend-review.md`와 `code-review.md`의 Self Review·Diff Review 필수. 실행 결과는 PASS/FAIL/NOT RUN/NOT APPLICABLE로 기록한다.

## 적용 경계와 근거

이 문서 적용은 개발 기준의 확정이다. API 실행, 인증 Handler, DB 적용, 테스트 환경 구축과 CI 연결은 별도의 구현·검증 대상이다. 실제 운영 도메인이나 Credential은 사용자 준비 전까지 생성하지 않는다.

- [Spring 오류 응답](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring CSRF와 SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
- [Spring Boot 테스트](https://docs.spring.io/spring-boot/reference/testing/index.html)
