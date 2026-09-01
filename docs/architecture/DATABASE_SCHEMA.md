<!-- GitHub 기반 Native MySQL Schema 변경 절차 -->
# Native MySQL Schema 변경 관리

## 관리 기준

- 변경 이력의 Source of Truth: GitHub의 Version SQL, Commit, Pull Request
- DB 내부 변경 이력 Table: 사용하지 않음
- 자동 Migration Library: 현재 Flyway와 Liquibase Dependency 없음
- 애플리케이션 자동 DDL: JPA `ddl-auto: validate` 유지
- Local Database와 계정 이름: Git에서 무시되는 `.env`에서만 관리

## 현재 Schema

업무 Entity와 업무 Table이 아직 없으므로 Git에 적용할 Schema SQL도 없다. Database와 Local 애플리케이션 계정 생성은 `scripts/agent/setup-local-mysql.ps1`의 Local 설정 절차이며 Schema 변경 이력에 포함하지 않는다.

첫 업무 Entity가 추가될 때 실제 Table 생성 SQL을 `V0001`로 시작한다.

## 파일 규칙

```text
infra/mysql/schema/V{4자리 순번}__{짧은_설명}.sql
```

- 적용된 SQL 수정 금지
- 실제 Schema 변경마다 새 Version SQL 추가
- 파일 첫 부분에 역할, 이유, 내용, 호환성, Rollback 조건 기록
- DB 내부 이력 Table 생성과 기록 SQL 금지
- Secret, 실제 사용자 데이터, Database·계정 이름 기록 금지

## GitHub 기록 항목

Commit 또는 Pull Request에 다음 내용을 기록한다.

- 변경 이유
- 생성·변경 대상
- 이전·신규 애플리케이션 호환성
- Local 빈 Database 적용 결과
- 관련 Backend Test 결과
- 운영 적용 순서와 Rollback 조건

## 변경 Loop

1. 관련 Entity와 이전·신규 애플리케이션 Version 확인
2. Table·Nullable Column·Index 추가 중심의 호환 SQL 작성
3. 빈 Local Database에 Version 순서대로 적용
4. Backend `verify`와 애플리케이션 시작 검증
5. SQL과 검증 결과를 동일 Pull Request에 포함
6. 운영 적용 전 Backup과 복구 명령 확인
7. 사용자 승인 후 운영 DB 적용
8. 적용 결과를 Pull Request 또는 Release 기록에 추가

Table·Column 삭제, 이름 변경, Type 축소, 대량 데이터 변환은 자동 수행하지 않는다. Blue/Green 양쪽 Version이 새 Schema를 사용할 수 없는 변경은 확장 → 애플리케이션 전환 → 정리 순서로 분리한다.

## Local Database와 계정

Local 설정은 마스킹 입력 절차를 사용한다.

```powershell
pwsh -NoProfile -File scripts/agent/setup-local-mysql.ps1
```

Script는 Database, 애플리케이션 계정과 DML 권한을 준비하고 실제 값을 Git에서 무시되는 `.env`에만 저장한다. Schema DDL은 Root 관리 절차에서만 적용하고 애플리케이션 계정에는 DDL 권한을 부여하지 않는다.

## Version 기준

- Local 기준: Windows Service와 전용 Client MySQL 8.0.30
- 사용 제외: Scoop 기본 Client MySQL 9.7.1
- 배포 기준: Local과 동일한 Native MySQL 8.0.30

배포 전 GitHub의 전체 Version SQL을 같은 MySQL Version의 빈 Database에 순서대로 적용해 검증한다.
