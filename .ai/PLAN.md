<!-- 현재 Frontend 작업 대상과 진행 상태 -->

# 현재 작업 계획

## Target

- Figma file: `qo0ztGDqf3MrinTOyyySy9`
- Figma Section: `13:446`
- 구현 확인 Node: `13:533`, `13:565`, `13:719`
- PNG 기준 추가 화면: 건강 결과, 제목 결과, 저장 기록, 회원가입 3단계, 신고 관리자
- 결과 화면 최신 Node: `15:114`
- URL: `https://www.figma.com/design/qo0ztGDqf3MrinTOyyySy9/기사체크-·-데스크톱-UX-UI?node-id=13-446`
- 화면 범위: Desktop 구현과 시각 비교

## Current Status

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
- 일반 로그인·도움말·계정 찾기·회원가입 Backend 연결: NOT RUN
- Browser Runtime과 Console 오류 확인: PASS
- Browser 회원가입 단계 전환: PASS
- 실행 코드의 화면 더미 데이터 제거와 화면별 입력 모델 정의: PASS
- 데이터 미제공·빈 목록 상태의 Desktop 레이아웃 유지: PASS
- 1024·1280·1440px에서 8개 Route 가로 넘침 검사: PASS
- Backend API 실제 데이터 연결: NOT RUN
- Frontend Test 11개, TypeScript, Lint, 변경 파일 Format과 Build: PASS
- Native MySQL 8.0.30 서비스 실행과 초기 Schema 파일 13개 Table 정적 확인: PASS
- 빈 Local Database에 초기 Schema 실제 적용과 `information_schema` 확인: PASS
- Local 애플리케이션 계정의 DML 전용 권한과 DDL 차단 확인: PASS
- Spring JPA DataSource와 `ddl-auto: validate` 애플리케이션 기동: PASS
- JPA Entity와 실제 Table Mapping 검증: NOT APPLICABLE (현재 Entity 없음)

## Next Loop

1. Figma MCP 호출 가능 시 PNG 구현과 실제 Design Context 차이 재검증
2. 관련 Backend 기능 구현 후 저장·삭제·신고·인증 동작 연결
3. 첫 JPA Entity 구현 시 실제 Table Mapping 검증 추가

## Backend 표준화 상태

- Backend 개발 표준과 Harness 연결·Docs 검증: PASS
- Astra·Sol 협업 역할과 Local Custom Agent TOML 문법: PASS
- 명시적 Sol 모델 호출을 통한 교차 검토: PASS
- Custom Agent 파일 자동 로딩·실행: NOT RUN (별도 CLI Sandbox의 인증·연결 환경 제약)
- API 상세 계약·결정: Git 제외 Local 문서에서 관리
- 실제 Backend 업무 구현·통합 테스트 환경 구성: NOT RUN
- 기존 Frontend Target과 Next Loop 유지; Backend 작업 시 개발 표준과 관련 Local 계약 우선 확인

## Required Before Live OAuth

[실제 Provider 연동 검증 전에 OAuth Client ID와 Client Secret의 Backend 전용 Local 환경 설정 입력이 필요합니다.]

Frontend 환경 설정에는 공개 값만 저장하며, `VITE_` 변수에 Client Secret 등 비밀값을 넣지 않는다.

## Native MySQL Application

- Local Database와 애플리케이션 계정 설정: Git에서 제외된 `.env`
- Root 인증 저장: 사용하지 않음
- 초기 Schema와 JPA 기동 재검증: `scripts/agent/verify-native-mysql.ps1`
