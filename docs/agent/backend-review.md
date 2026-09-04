<!-- Spring 백엔드와 Redis 변경 리뷰 기준 -->
# Backend Review Checklist

공통 절차·심각도·수정 Loop는 `code-review.md`를 따른다. 아래는 리뷰 정책이며 현재 구현 완료 사실이 아니다. 실제 변경과 연결된 항목만 적용하고, 해당 기술이나 경로가 없으면 `NOT APPLICABLE`로 기록한다.

## Repository 근거

- `backend/pom.xml`: Spring Security, OAuth2 Client, JPA, Redis와 Redis Session 의존성
- `backend/src/main/java/com/newsverification/config/SecurityConfig.java`: 현재 보안 구성 확인 위치
- `MVP_REQUIREMENTS.md` 9절·14절: 인증·세션·공용 캐시 요구사항
- `docs/agent/project-context.md` 위험 표: Redis 장애 시 이용량·Lock 확인 불가에 따른 새 AI 분석 중단 정책

의존성 또는 요구사항의 존재를 기능 구현 증거로 간주하지 않는다. 세션·캐시 외 인증번호, 이용량과 Lock 등의 실제 구현 여부는 리뷰 시 확인한다.

## API와 비즈니스 책임

- 요청·응답·HTTP 상태·오류 표현의 확정 계약 일치
- 필수값·형식·범위·경계값과 실패·취소·데이터 없음 경로
- 기존 계층별 책임 유지, Controller·Service·Repository 구조 임의 강제 금지
- 중복 요청이 실제 데이터 규칙을 깨는지 확인 후 필요한 대응 검토
- 내부 예외와 민감정보의 응답 노출 여부

## Transaction과 동시성

- 원자적으로 완료되어야 할 변경 경계와 예외 발생 시 부분 저장
- Spring Proxy 적용과 실제 호출 경로, 자기 호출의 영향
- 예외를 잡아 정상 반환하면서 의도하지 않은 Commit 발생 여부
- 외부 호출이 Transaction과 DB Lock을 오래 유지하는지 확인
- 경쟁 요청의 발생 조건과 보호해야 할 데이터 규칙 명시
- 근거 없이 Transaction, Lock이나 Retry 추가 요구 금지

## JPA와 Native MySQL

- Entity와 Schema의 타입·NULL·Unique·관계 제약 일치
- Cascade·연관관계 변경에 따른 의도하지 않은 삭제
- 변경된 조회 경로의 Lazy Loading·N+1·무제한 조회
- 필요한 Pagination·정렬의 안정성과 조회 조건·Index 적합성
- 이전·신규 애플리케이션과 Schema 호환성
- `ddl-auto: validate`와 기존 Schema 관리 정책 유지
- 초기 SQL Local 전용·후속 SQL Git 관리 경계와 적용·복구 조건 확인

## Security와 OAuth

- 서버 인증·인가와 사용자별 자원 접근 권한 검사
- 기존 CSRF·Session·보안 검증 약화 여부
- Callback 등록값, 실제 처리 경로와 Redirect 목적지 검증
- State 검증과 실패 분류, 실패를 성공·정상 취소로 오인하는 경로
- Token·인증 Code·Cookie·개인정보의 응답과 로그 노출
- 신규 소셜 사용자의 초대 코드 검증 전 권한 부여 여부

## Redis

| 영역 | 관련 변경에서 확인할 기준 |
| --- | --- |
| Key | 기능·환경 Namespace 충돌, 원문·개인정보·Secret의 불필요한 포함 |
| TTL | 저장·갱신 시 만료 누락, 의도하지 않은 만료 연장과 영구 Key |
| 직렬화 | 타입·직렬화 형식 변경 시 기존 값과 이전·신규 Version 호환성 |
| 캐시 | 무효화 누락, 만료 직후 동시 재계산, 사용자별 데이터의 공용 캐시 혼입 |
| 세션 | 로그아웃·비밀번호 변경 시 만료, 일반·유지 로그인 시간과 권한 변경 반영 |
| 이용량·인증 제한 | 조회·증가·만료 설정 사이 경쟁 조건, 중복 차감과 제한 우회 |
| 인증번호 | 재발송 시 이전 번호 무효화, 만료와 시도 횟수의 일관성 |
| Lock | 획득·만료·해제의 원자성과 소유권, 처리 시간 초과와 중복 실행 |
| 장애 | Timeout·재시도, 제한·Lock 확인 실패 시 새 AI 요청 차단 |
| 성능 | 요청 경로의 전체 Key 탐색, 무제한 조회, 큰 값과 과도한 호출 |
| DB 경계 | DB Rollback 후 Redis에 성공 상태가 남는지, 실패 순서별 일관성 |
| 검증 | 만료·동시 요청·장애·기존 데이터 호환성을 검증하는 실제 경계 |

- DB Transaction과 Redis 변경이 자동으로 하나의 원자적 작업이 된다고 가정하지 않음
- Lock이 만료된 이전 요청이 새 소유자의 Lock을 해제하지 않는지 확인
- 공용 분석 캐시의 기능별 구분, 버전별 무효화 기준과 개인정보 제외 확인
- Mock 테스트만으로 Redis의 원자성·TTL·경쟁 조건이 검증됐다고 보고하지 않음
- 전체 Key 조회·삭제, 실제 서비스 데이터 변경을 리뷰 목적으로 실행하지 않음
- Redis 사용을 이유로 Lua, 분산 Lock Library나 추가 도구를 자동 도입하지 않음

## 외부 호출·오류·로그

- Timeout·Retry 정책, 재시도로 인한 중복 처리와 추가 비용
- 실패를 삼키거나 성공 값으로 위장하는 경로
- 중복 로그·과도한 로그와 민감정보 노출
- 오류 분류와 요청 추적 정보 보존, 설정 누락 원인 식별

## 테스트

- 요구사항과 실제 동작을 검증하는 정상·실패·경계 사례
- Mock 호출 확인만으로 실제 응답·저장·권한 결과를 놓치는지 확인
- 중요 변경에 대한 Transaction·DB 제약·Security Filter·Redis 실제 동작 검증
- 외부 환경이 없으면 해당 통합 검증을 `NOT RUN`으로 명시
- 무관한 전체 테스트 실행이나 새 테스트 도구 도입을 강제하지 않음
