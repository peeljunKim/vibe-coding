<!-- 현재 Frontend 작업 대상과 진행 상태 -->
# 현재 작업 계획

## Target

- Figma file: `qo0ztGDqf3MrinTOyyySy9`
- Figma node: `13:719`
- URL: `https://www.figma.com/design/qo0ztGDqf3MrinTOyyySy9/기사체크-·-데스크톱-UX-UI?node-id=13-719`
- 화면 범위: Desktop 구현과 시각 비교

## Current Status

- Frontend 전용 Harness 문서와 `AGENTS.md` 연결: PASS
- Docs/Harness, Secret와 Diff 검사: PASS
- Figma Design Context와 Screenshot 접근: PASS
- Node 22.18.0, npm 10.9.3과 Java 17.0.11: PASS
- Clean Clone `npm ci`: PASS
- 기존 Frontend Lint, 1개 Test와 Build: PASS
- Figma 화면 구현: NOT RUN
- OAuth Redirect와 Provider 연동: NOT RUN
- Browser·E2E와 Figma 비교: NOT RUN

## Next Loop

1. 대상 Node의 Frame 범위와 상태 확인
2. 관련 Frontend 구조와 재사용 대상 확인
3. `Confirmed / Inferred / Required`와 구현 계획 보고
4. 사용자 승인 대상 확인 후 최소 단위 구현
5. 관련 검증 → Browser 비교 → Self Review → Diff Review

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
