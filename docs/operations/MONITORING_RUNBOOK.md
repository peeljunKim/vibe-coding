<!-- 기사체크 운영 경보 확인과 복구 절차 -->
# 모니터링 장애 대응 Runbook

## 공통 확인 순서

1. Grafana `기사체크 서비스 개요`에서 경보와 연결된 패널 확인
2. Prometheus `Alerts`에서 발생 시각·Label·지속 시간 확인
3. `docker compose ps`로 Redis·Prometheus·Grafana·Alertmanager 상태 확인
4. Backend `/actuator/health`와 `/actuator/prometheus`의 내부 접근 상태 확인
5. 원인 조치 후 Prometheus 경보가 `resolved`로 전환되는지 확인

운영 Prometheus·Grafana·Alertmanager Port는 외부에 공개하지 않는다. 로그를 공유할 때 Secret, Cookie, Session ID, 기사 원문과 사용자 정보를 제외한다.

## NewsVerificationBackendUnavailable

- 조건: Backend Target이 2분 동안 `DOWN`
- 심각도: `critical`
- 사용자 영향: 모든 API와 분석 접수·조회 불가 가능성
- Grafana 확인: Backend 상태, HTTP 요청률, HTTP 5xx
- 대응: Backend Process와 Port 확인 → 최근 배포 로그 확인 → 기존 Blue Version 상태 확인
- 복구 확인: `up{job="news-verification-api"} == 1`과 Actuator Health `200`
- 롤백: 신규 Version Health Check 실패 시 Traffic을 기존 Version으로 유지

## NewsVerificationAnalysisRedisUnavailable

- 조건: 5분 동안 Redis 장애로 분석 접수 실패 3건 이상이 2분간 지속
- 심각도: `critical`
- 사용자 영향: 신규 분석 접수 차단, 기존 Session·Cache 기능 영향 가능성
- Grafana 확인: 분석 작업 접수, Backend 상태
- 대응: Redis Container Health와 인증 설정 확인 → Redis 재시작 여부 판단 → Backend 연결 회복 확인
- 복구 확인: 신규 `redis_unavailable` 증가 중단과 정상 분석 접수
- 롤백: Redis 설정 변경이 원인이면 직전 설정으로 복원

## NewsVerificationAnalysisQueueSaturated

- 조건: 5분 동안 Queue 포화 거절 3건 이상이 2분간 지속
- 심각도: `warning`
- 사용자 영향: 일부 신규 분석 요청이 `503`으로 거절됨
- Grafana 확인: 분석 작업 접수, Worker 처리 결과, Worker p95 처리 시간
- 대응: Worker 실패·지연 확인 → 장시간 작업 확인 → 새 요청 급증 여부 확인
- 복구 확인: `queue_full` 증가 중단과 Worker 처리 시간 정상화
- 롤백: 최근 Worker 변경 이후 발생했으면 직전 Application Version 유지

## NewsVerificationWorkerFailureRateHigh

- 조건: 10분 최소 5건 처리 중 실패율 25% 초과가 5분간 지속
- 심각도: `warning`
- 사용자 영향: 건강 또는 제목 분석 결과 생성 실패 증가
- Grafana 확인: Worker 처리 결과, 기사 추출 결과
- 대응: `feature` Label 확인 → 첫 공통 예외 분류 확인 → 관련 Mock·외부 Adapter 상태 확인
- 복구 확인: 같은 기능의 실패율 25% 이하 유지
- 롤백: 최근 분석 Adapter 변경이 원인이면 직전 Version 유지

## NewsVerificationWorkerLatencyHigh

- 조건: 10분 최소 5건 처리 중 p95가 75초 초과한 상태가 5분간 지속
- 심각도: `warning`
- 사용자 영향: 90초 Deadline 초과와 분석 실패 가능성 증가
- Grafana 확인: Worker p95 처리 시간, Worker 처리 결과
- 대응: `feature` Label 확인 → 기사 추출·근거 검색·분석 단계 지연 확인 → 외부 호출 Timeout 확인
- 복구 확인: p95가 75초 이하로 회복하고 Deadline 실패가 증가하지 않음
- 롤백: 최근 처리 경로 변경이 원인이면 직전 Version 유지

## NewsVerificationArticleExtractionFailureRateHigh

- 조건: 10분 최소 10건 추출 중 실패율 30% 초과가 5분간 지속
- 심각도: `warning`
- 사용자 영향: 특정 또는 복수 언론사의 분석 시작 실패
- Grafana 확인: 기사 추출 결과의 `failure` 분류
- 대응: 실패 분류 확인 → 지원 언론사별 공개 접근·HTML 구조 확인 → 실패 언론사 일시 중단 판단
- 복구 확인: 실패율 30% 이하와 대상 언론사 Fixture·제한 Smoke PASS
- 롤백: 최근 추출기 변경이 원인이면 직전 Version 유지

## 이메일 수신자 관리

기본 수신자는 `reportcheck104@gmail.com`이다. 여러 수신자는 `infra/alertmanager/alertmanager.yml`의 `to` 값에 쉼표로 구분해 추가한다. Gmail App Password는 `MAIL_APP_PASSWORD` 환경 변수에서 Docker Secret으로 전달하며 Git 추적 파일과 로그에 기록하지 않는다.

## Slack 채널 역할

- `#monitoring-alerts`: 자동 경보와 복구 알림 전용
- `#incident-response`: 장애 원인, 담당 작업, 조치와 복구 결과 기록

`News Verification Alerts` Incoming Webhook은 `#monitoring-alerts`에 연결한다. Webhook URL은 Git에서 제외된 `.env`의 `SLACK_WEBHOOK_URL`에서 Docker Secret으로 전달하며 Git 추적 설정, 메시지 또는 로그에 기록하지 않는다.

Webhook을 새로 발급하거나 교체할 때는 다음 Local 수신 화면으로 저장한다.

```powershell
pwsh -NoProfile -File scripts/agent/setup-slack-webhook.ps1 -ReceiveFromBrowser
```
