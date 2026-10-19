<!-- Vercel과 Oracle Always Free 배포 운영 절차 -->
# 무료 Prototype 배포 Runbook

## 고정 구조

```text
Browser HTTPS
  → Vercel Hobby Frontend
  → /api·/oauth2·/oauth 동일 출처 Rewrite
  → DuckDNS HTTPS
  → Oracle A1 Nginx
  → Spring Boot Blue 8081 또는 Green 8082
```

MySQL Community 8.4 LTS의 배포 시점 최신 패치는 Oracle VM Host에 직접 설치한다. Local 8.0.30 기준 Schema와 JPA의 8.4 호환 검증을 배포 전에 완료한다. Redis·Prometheus·Grafana·Alertmanager만 Docker Compose로 실행한다. Oracle PAYG와 Vercel 유료 Plan으로 전환하지 않으며 무료 한도 도달 시 자동 유료 확장 대신 기능 제한이나 중단을 허용한다.

## 운영 값 준비

- Vercel Project Root: `frontend`
- Vercel 운영 URL
- Backend DuckDNS Subdomain과 Token
- Oracle Home Region의 Always Free 표시가 있는 A1 Shape
- Blue Port `8081`, Green Port `8082`
- Git에서 제외된 운영 환경 변수와 Secret

실제 값은 Repository의 Placeholder에 기록하지 않는다. DuckDNS Host가 확정되면 `frontend/vercel.json`을 `frontend/vercel.runtime.json`으로 복사한 뒤 Backend Origin을 치환하고, Nginx Template의 `__BACKEND_DUCKDNS_HOST__`도 배포 단계에서 치환한다.

DuckDNS Host와 Blue/Green Port는 자격 증명이 아니라 외부 또는 내부 운영 식별값이다. 숨김만으로 보안을 확보할 수는 없지만 운영 설정 혼입을 막기 위해 Git 추적 Template에는 Placeholder만 유지한다. 치환 결과인 `frontend/vercel.runtime.json`, `infra/nginx/news-verification.conf`, `infra/nginx/active-backend.conf`는 Git에서 제외하고 배포용 임시 Directory 또는 Oracle의 `/etc/nginx/news-verification/`에서만 생성한다.

다음 값은 실제 운영 Secret이며 Git 추적 파일, `VITE_*`, Vercel 정적 Frontend Build, Nginx Template, 명령행 인자와 로그에 기록하지 않는다.

- DuckDNS Token과 향후 Origin 인증용 공유 Secret
- TLS Private Key
- MySQL·Redis·Grafana 비밀번호
- OAuth Client Secret과 Gmail App Password
- Gemini·PubMed API Key와 Slack Webhook
- 암호화·HMAC·IP 비식별 Key와 초대 코드

Backend Secret은 Oracle의 Repository 밖 권한 제한 환경 파일이나 Secret File에서 Process 실행 시 주입한다. TLS Private Key는 Let's Encrypt Directory 밖으로 복사하지 않는다. Vercel에 서버 전용 Secret이 필요해지면 Project Secret으로만 등록하고 `VITE_` 접두사를 사용하지 않는다. `VITE_*`는 Browser에서 누구나 읽을 수 있는 공개 설정 전용이다.

Git에서 제외된 `backend/src/main/resources/application*.yml`도 Build 시 JAR에 포함되므로 실제 Secret을 직접 작성하지 않는다. 이 파일에는 `${ENVIRONMENT_VARIABLE}` 참조만 두고, 운영 값은 Repository 밖에서 주입한다. Git에서 제외된 `frontend/.env.local` 역시 `VITE_*` 값을 Browser Bundle에 포함하므로 Secret 저장소로 사용하지 않는다.

Clean Clone CI에서는 Git 추적 Template과 노출 경계만 검사한다. 배포용 JAR와 Vercel Runtime 설정을 준비한 뒤에는 다음 명령으로 Git 제외 설정까지 반드시 검사한다.

```powershell
pwsh -NoProfile -File scripts/agent/verify-production-routing.ps1 -RequireRuntimeConfig
```

## Vercel 경계

- `/api/:path*`는 Backend의 동일 경로로 전달
- `/oauth2/:path*`는 OAuth 로그인 시작 경로로 전달
- `/oauth/:path*`는 Provider Callback 경로로 전달
- 나머지 경로는 React Router를 위해 `index.html`로 전달
- `/api`·OAuth 외부 Rewrite Cache는 `x-vercel-enable-rewrite-caching: 0`으로 명시적 비활성화
- 운영 Frontend Build에는 `VITE_BACKEND_BASE_URL`을 설정하지 않음

Git에서 제외된 `vercel.runtime.json`은 Vercel Git 자동 배포가 읽지 않는다. Backend Origin을 Repository에 남기지 않는 현재 정책에서는 Git 자동 배포를 연결하지 않고, `frontend`에서 Runtime 설정을 명시한 CLI 배포만 사용한다. Preview와 운영 배포에서 추적 중인 `vercel.json`을 직접 사용하지 않는다.

```powershell
pwsh -NoProfile -File scripts/agent/prepare-production-routing.ps1
Set-Location frontend
pwsh -NoProfile -File ..\scripts\agent\verify-production-routing.ps1 -RequireRuntimeConfig
$deploymentUrl = vercel deploy --prod --skip-domain --local-config vercel.runtime.json
# 배포 URL의 /api와 OAuth 경로를 확인한 뒤 운영 Domain으로 승격
vercel promote $deploymentUrl
```

`--local-config vercel.runtime.json`을 생략하면 추적 중인 Placeholder 설정이 사용되므로 배포하지 않는다. 향후 Git 자동 배포가 필요하면 Runtime 파일을 안전하게 생성하는 별도 CI 설계를 승인받은 뒤 전환한다.

Provider Console의 운영 Callback은 Vercel 운영 Origin의 `/oauth/google`, `/oauth/naver`, `/oauth/kakao`를 등록한다. Backend의 `APP_BASE_URL`도 같은 Vercel 운영 Origin을 사용한다.

## Nginx와 HTTPS 경계

- Vercel에서 DuckDNS Backend까지 HTTPS 사용
- Nginx에서 Let's Encrypt 인증서와 개인키 사용
- Nginx에서 Spring Boot Loopback Port로만 Proxy
- `/actuator/*` 외부 차단
- MySQL `3306`, Redis `6379`, Prometheus `9090`, Alertmanager `9093`, Grafana `3000` 외부 공개 금지
- Docker Host Port는 `127.0.0.1`에만 Bind하고 외부 관리가 필요하면 SSH Tunnel 사용
- Oracle Network Security List는 `80`, `443`과 제한된 관리용 SSH만 허용

## 운영 전 Client 주소 경계

현재 비회원 이용량과 요청 제한은 Backend가 확인한 원격 주소를 비식별 신호로 사용한다. Vercel은 `x-vercel-forwarded-for`와 `x-forwarded-for`에 Client 주소를 제공하지만, 공개 DuckDNS Origin의 직접 요청은 같은 Header를 위조할 수 있다.

준비한 Nginx Template은 `X-Articlecheck-Origin`을 확인한 뒤 Middleware가 재작성한 `X-Articlecheck-Client-IP`만 전달한다. 요청자가 보낸 `Forwarded`는 제거하고, `X-Forwarded-For`를 덧붙이지 않고 교체한다. Host는 확정된 Frontend Host로 고정하며 Origin Secret은 Spring으로 전달하지 않는다. API·OAuth 경로의 접근 로그는 비활성화한다. Nginx 오류 로그에도 Cookie·Secret을 추가하지 않고 OAuth Query가 포함되는지 실제 운영 검증에서 확인한다.

`prepare-production-routing.ps1`은 다음 Process 환경 변수로 세 Runtime 파일을 생성한다. 실제 값은 출력하지 않고 기존 파일을 덮어쓰지 않으며 Git 제외와 파일 권한을 검사한다. Nginx Runtime 파일에는 Origin Secret이 포함되므로 Oracle의 Repository 밖 `/etc/nginx/news-verification/`로 옮겨 최소 권한으로 관리하고 `nginx -T` 출력을 공유하거나 CI Artifact로 저장하지 않는다. Template은 Nginx `http` Context에서 include한다.

| 변수 | 입력 내용 |
| --- | --- |
| `BACKEND_DUCKDNS_HOST` | 확정된 DuckDNS Host 이름만 입력 |
| `FRONTEND_PUBLIC_ORIGIN` | 확정된 Vercel HTTPS Origin, 경로·Query 제외 |
| `ORIGIN_PROXY_SECRET` | 별도로 준비한 256-bit 난수의 소문자 64자리 Hex 값 |
| `ACTIVE_BACKEND_PORT` | `8081` 또는 `8082`, 기본 `8081` |

Vercel 서버 환경 변수에도 같은 `ORIGIN_PROXY_SECRET`을 등록하고 `VITE_`를 사용하지 않는다. Secret을 명령행 인자나 Shell 기록에 입력하지 않는다. Backend Spring Boot는 Loopback에서만 실행하고 Secure Cookie와 기존 CSRF·권한 검사를 유지한다. ACME 인증서 발급·갱신 방식은 VM 준비 후 별도로 구성한다.

**현재 적용 차단:** Vercel Middleware는 아직 연결되지 않았다. 공식 Helper `@vercel/functions` Dependency 승인 후 API·OAuth 경로에만 적용하고 사용자 입력 인증·IP Header 제거, 플랫폼 IP 검증, Secret 주입을 구현해야 한다. 준비된 Nginx만 먼저 적용하면 API·OAuth 요청이 404로 차단된다. `-RequireRuntimeConfig`는 이 상태에서 배포를 거부한다.

실제 Preview에서 IPv4·IPv6, Header 위조, 다중 Set-Cookie, CSRF, OAuth Redirect·Callback과 직접 DuckDNS 요청 차단을 확인한다. Naver 서버 간 연결 해제 Callback이 필요하면 공급자 검증 방식부터 확정하며 인증 예외를 임의로 추가하지 않는다. Hobby 한도 초과 또는 Middleware 장애 때 직접 Origin 경로로 우회하지 않는다.

롤백은 이전 Vercel 배포와 호환되는 Nginx 설정을 함께 복원한다. Origin 인증만 끄거나 이전의 무인증 Template을 운영에 다시 적용하지 않는다.

## Blue Green 전환

1. 비활성 Port에 새 Spring Boot Version 실행
2. Loopback에서 `/actuator/health`가 `UP`인지 확인
3. `active-backend.conf`를 새 Port로 원자 교체
4. `nginx -t` 성공 확인
5. Nginx Reload
6. Vercel 경유 API·로그인·OAuth Callback Smoke 확인
7. 실패 시 이전 Port로 `active-backend.conf` 복원 후 Reload

Blue와 Green은 동일 MySQL과 Redis를 공유한다. Schema 변경은 이전·신규 Version이 동시에 사용할 수 있어야 한다.

## 현재 미실행

- Vercel Project 생성과 Local CLI 연결
- Oracle A1 VM 생성
- DuckDNS Subdomain과 인증서 발급
- 운영 Secret 주입
- 실제 Blue Green Traffic 전환
- 운영 OAuth Callback 등록
- Vercel 경유 요청의 Origin 인증과 비회원 Client 주소 신뢰 방식 확정
