<!-- Figma 기반 Frontend 구현과 검증 규칙 -->
# Frontend 구현 규칙

이 문서는 Frontend UI, Figma 구현과 OAuth 버튼 작업에만 읽는다. 확인하지 못한 정보는 추측하지 않고 `[확인에 필요한 구체적인 정보가 필요합니다.]` 형식으로 사용자에게 요청한다.

## 기준과 범위

우선순위는 사용자 확정 요구사항 → 대상 Figma Frame → 실제 코드 → Build·설정 → 테스트 → Harness 문서 → 명시적 추론 순서다. 문서와 코드가 다르면 현재 동작은 코드 기준으로 보고하고 차이를 명시한다.

- Figma 디자인과 확인된 문구·상태·인터랙션의 임의 변경 금지
- 기존 구조, 공통 Component와 Design Token 우선 재사용
- 관련 없는 수정, 새 Dependency와 불필요한 추상화 금지
- 한 번만 쓰는 UI의 불필요한 공통 Component화 금지
- Figma 좌표를 복사한 absolute 배치 남용 금지
- Flexbox, Grid와 정상 문서 흐름 중심 구현
- 필요한 주석만 짧고 쉬운 한국어 명사형으로 작성
- 새 관리 대상 파일은 문법이 허용할 때 첫 부분에 짧은 역할 주석 작성
- JSON, Lockfile, Binary와 생성물의 억지 주석 금지

현재 MVP의 시각 구현과 비교 범위는 Desktop이다. Figma Frame 크기를 1차 비교 Viewport로 사용한다. Tablet과 Mobile은 별도 디자인 없이 임의 구현하지 않고 `NOT APPLICABLE`로 기록한다. Desktop 너비 변화에서는 명백한 레이아웃 파손만 방지한다.

## Context Loading

1. `AGENTS.md`, `.ai/MEMORY.md`, `.ai/RULES.md`, `.ai/PLAN.md` 확인
2. 정확한 Figma Node의 Design Context와 Screenshot 확인
3. 관련 Frontend 구현 → 설정 → 테스트 탐색
4. 인증 작업에만 Backend OAuth 구성과 `MVP_REQUIREMENTS.md` 관련 절 확인
5. 관련 없는 Page와 Domain 탐색 중단

대상 Node가 여러 Frame을 포함하면 Frame 목록과 범위를 먼저 보고한다. 범위를 확정할 수 없으면 구현을 시작하지 않는다.

## Figma 분석

구현 전에 실제 대상에서 다음 항목을 확인한다.

- Page·Frame·Layout과 Component 계층
- Header, Navigation, Main, Footer
- Button, Input, Card, Modal, Tab, Dropdown과 Toast
- Default, Hover, Focus, Active와 Disabled
- Loading, Empty, Error와 Success
- Width, Height, Spacing, Alignment와 Typography
- Color, Border, Radius와 Shadow
- Icon, Image, Asset와 Variant
- Prototype 연결과 사용자 흐름

Figma에 없는 상태나 동작이 실제 기능에 필요하면 사용자에게 요청한다. Screenshot만 보고 구현하지 않고 Design Context를 기준으로 한다. Figma Asset은 실제 Export를 사용하며 직접 SVG나 대체 이미지를 만들지 않는다.

## OAuth Redirect

Google Identity Services JavaScript Callback 방식과 혼합하지 않고 Spring Security OAuth2 Redirect 방식을 사용한다.

### 로그인 시작 URL

- Google: `http://localhost:8080/oauth2/authorization/google`
- Naver: `http://localhost:8080/oauth2/authorization/naver`
- Kakao: `http://localhost:8080/oauth2/authorization/kakao`

Frontend는 Provider URL을 직접 조합하지 않고 Backend 시작 URL로 전체 페이지 이동한다. Local Backend 기준 주소는 Git에서 제외된 Frontend 환경 설정의 `VITE_BACKEND_BASE_URL`을 사용한다.

### Provider Callback

- Google: `http://localhost:8080/oauth/google`
- Naver: `http://localhost:8080/oauth/naver`
- Kakao: `http://localhost:8080/oauth/kakao`
- Naver 연결 해제: `http://localhost:8080/oauth/naver/disconnect`

Spring Security Callback 처리 경로와 Provider Console 등록값을 일치시킨다. 로그인 Callback과 Naver 연결 해제 Callback의 책임을 분리한다.

### Frontend 완료 경로

- 기존 회원 성공: `http://localhost:5173/`
- 신규 소셜 사용자: `http://localhost:5173/signup/social/invite`
- 사용자 취소: `http://localhost:5173/login?oauth=cancelled`
- 인증 실패: `http://localhost:5173/login?oauth=failed`

신규 소셜 사용자는 인증 후 초대 코드만 입력한다. 기존 Routing 또는 Figma와 경로가 충돌하면 임의로 변경하지 않고 보고한다.

### Secret 경계

- Client ID와 Client Secret의 코드 하드코딩 금지
- Client Secret은 Backend 전용 환경 설정으로 관리
- Frontend로 Client Secret 전달 금지
- Authorization Code, Token, Session ID, Cookie와 CSRF Token 로그 금지
- 실제 Provider 호출은 Local 자격 증명 준비와 사용자 승인 후 실행

## OAuth 버튼

Provider별 공식 브랜드 정책을 우선하며 모든 버튼을 Google 스타일로 통일하지 않는다. Figma와 공식 정책이 충돌하면 현재 표현, 공식 요구사항, 변경 이유와 예정 표현을 구현 전에 보고한다.

### Google

- Spring Security Redirect 흐름 유지
- GIS JavaScript 인증 흐름 사용 금지
- 공식 사전 승인 Asset 또는 브랜드 규격에 맞는 HTML 버튼 사용
- 표준색 G 로고, 문구, 크기, 색상과 여백 임의 변경 금지
- 공식 가이드: `https://developers.google.com/identity/branding-guidelines?hl=ko`

### Naver

- 공식 로그인 Asset, 로고, 색상, 문구와 여백 사용
- 로고 직접 제작과 변형 금지
- 공식 가이드: `https://developers.naver.com/docs/login/bi/bi.md`

### Kakao

- 공식 로그인 Asset, 심볼, 색상과 문구 사용
- 심볼 직접 제작과 변형 금지
- 공식 가이드: `https://developers.kakao.com/docs/ko/kakaologin/design-guide`

공식 Asset을 확보할 수 없으면 임의 제작하지 않고 `[해당 Provider의 공식 로그인 Asset 파일이 필요합니다.]`라고 요청한다.

## 오류 표현과 Logging 경계

Frontend는 내부 원인을 노출하지 않는다.

- 사용자 취소: `로그인이 취소되었습니다. 다시 시도할 수 있습니다.`
- 일반 실패: `로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.`

Backend 분류 기준은 다음과 같다.

| 상황 | Frontend | Log |
| --- | --- | --- |
| 사용자 로그인·동의 취소 | `oauth=cancelled` | `INFO` |
| Provider 일시 오류 | `oauth=failed` | `WARN` |
| OAuth 설정·서버 오류 | `oauth=failed` | `ERROR` |
| State 검증 실패 | `oauth=failed` | `WARN`, 반복 공격·내부 장애는 `ERROR` |
| Token 교환·사용자 정보 조회 실패 | `oauth=failed` | `WARN`, 잘못된 서버 설정은 `ERROR` |

로그에는 Provider, 내부 오류 분류, 요청 추적 ID와 비민감 상태만 기록한다. 전체 이메일과 Provider 원본 사용자 정보는 기록하지 않는다.

## 접근성

- 동작은 `button`, 이동은 `a` 또는 기존 Router `Link` 사용
- Input과 Label 연결, Image `alt` 처리
- Keyboard Navigation과 명확한 Focus 상태 제공
- Semantic HTML과 스크린 리더 오류 전달
- 상태를 색상만으로 전달하지 않음
- OAuth 버튼에 접근 가능한 이름 제공

## 작업 Loop

1. Context와 Figma 확인
2. 현재·기대 동작과 `Confirmed / Inferred / Required` 보고
3. 영향 파일, 위험과 검증 계획 작성
4. 최소 단위 구현
5. 관련 검증 실패 시 첫 의미 있는 오류 분석
6. 원인과 직접 관련된 최소 수정 후 같은 검증 재실행
7. 자동 검증 통과 후 Browser와 Figma 비교
8. Self Review, Diff Review와 Definition of Done 확인

같은 실패가 세 번 반복되면 추측성 수정을 중단하고 사실, 시도, 결과, 원인 후보와 필요한 정보를 보고한다. 테스트 삭제, Skip과 Assertion 완화로 실패를 숨기지 않는다.

Dependency, Lockfile, `SecurityConfig`, 인증·인가 정책 또는 Public API 변경에는 영향과 Rollback 방법을 먼저 설명하고 사용자 승인을 받는다.

## 검증 순서

Repository에 실제 존재하는 명령만 사용한다.

1. 관련 Test
2. TypeScript와 Lint
3. Frontend 전체 Test와 Build
4. Runtime과 Console 오류
5. OAuth Redirect, 취소와 실패 상태
6. Desktop Layout과 Figma 비교
7. Routing과 Global CSS Regression
8. Secret 검사와 `git diff --check`
9. 전체 Diff Review

실행하지 않은 검증은 `PASS`로 기록하지 않는다. 상태는 `PASS / FAIL / NOT RUN / NOT APPLICABLE`만 사용한다.

## 완료 조건

- 요구사항과 대상 Figma 구현
- Figma 임의 변경 없음
- 기존 Pattern과 최소 변경 원칙 준수
- OAuth 브랜드와 Callback 일치
- 신규 소셜 사용자의 초대 코드 흐름 반영
- 관련 Test, TypeScript, Lint와 Build 성공
- Runtime과 Console 오류 없음
- Desktop Layout, Interaction과 접근성 확인
- Figma 비교와 Regression 확인
- Secret, Self Review와 Diff Review 완료
- 알려진 실패와 미실행 검증 공개

## 완료 보고

다음 항목만 간결하게 보고한다.

- 구현 내용
- 생성·수정 파일
- 재사용·신규 Component
- 구현한 Interaction과 API
- OAuth Provider별 시작·Callback·성공·실패 처리
- Test, TypeScript, Lint, Build와 Browser 검증 결과
- 접근성, Figma 비교, Regression과 Secret 검사 결과
- Figma와 다른 부분
- `NOT RUN` 항목과 이유
- 추가로 필요한 정보
