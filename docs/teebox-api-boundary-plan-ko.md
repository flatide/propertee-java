# TeeBox Client / Publisher / Admin API 경계안

## 목적
실제 운영에서는 다른 서버가 TeeBox에 스크립트를 등록하고 실행을 요청하며 현재 상태를 조회하게 된다. 반면 `TASK kill`, `detached task`, raw process 정보 같은 운영 제어는 TeeBox 내부 관리자 기능으로 제한하는 편이 안전하다.

따라서 장기적으로 TeeBox API는 단일 `/api` 공간이 아니라 권한과 목적이 다른 세 층으로 분리하는 것이 맞다.

- `client`: 실행 요청, run 상태 조회, 결과 조회
- `publisher`: 스크립트 등록, 버전 관리, 활성 버전 전환
- `admin`: task/process 관찰, kill, detached 관리, 운영 디버깅

## 현재 상태
현재 구현은 단일 `/api` 혼합 경로를 제거하고, `client / publisher / admin` namespace를 분리한 상태다.

- run 제출/결과 조회: `/api/client/...`
- 스크립트 등록/활성화: `/api/publisher/...`
- run/task 관찰/kill: `/api/admin/...`

이제 남은 과제는 namespace 자체가 아니라 운영 정책이다.

- `scriptPath` 직접 실행을 언제까지 허용할지
- token scope를 어떻게 배포/회전할지
- upstream 서버에 어떤 API만 공개할지

## 목표 경계

### 1. Client API
업무 서버가 사용하는 공개 API다. 여기서는 "무엇을 실행할지"와 "run 결과가 무엇인지"만 다룬다.

권장 namespace:
- `/api/client/...`

권장 endpoint:
- `POST /api/client/runs`
- `GET /api/client/runs/{runId}`
- `GET /api/client/runs/{runId}/result`
- `GET /api/client/runs/{runId}/status`
- 선택: `GET /api/client/runs/{runId}/tasks-summary`

여기서는 허용하지 않는 것:
- task kill
- run task kill
- pid / pgid / hostInstanceId 노출
- raw stdout/stderr tail
- detached task 목록

실행 요청 예시:
```json
{
  "scriptId": "calibre-drc",
  "version": "2026.03.10.1",
  "props": {"cell": "TOP", "deck": "gf180"},
  "clientRequestId": "job-12345",
  "maxIterations": 1000,
  "warnLoops": false
}
```

핵심은 `scriptPath`가 아니라 `scriptId + version` 또는 `scriptId + channel`로 실행하는 것이다.

### 2. Publisher API
다른 서버가 스크립트를 "등록"해야 한다면, 이 기능은 일반 client API와 분리하는 편이 맞다. 권한이 더 크고, 검증/버전 정책도 다르기 때문이다.

권장 namespace:
- `/api/publisher/...`

권장 endpoint:
- `POST /api/publisher/scripts`
- `GET /api/publisher/scripts`
- `GET /api/publisher/scripts/{scriptId}`
- `POST /api/publisher/scripts/{scriptId}/versions`
- `POST /api/publisher/scripts/{scriptId}/activate`
- `GET /api/publisher/scripts/{scriptId}/versions/{version}`

등록 요청 예시:
```json
{
  "scriptId": "calibre-drc",
  "version": "2026.03.10.1",
  "content": "PRINT(\"hello\")",
  "description": "GF180 DRC run",
  "labels": ["calibre", "drc"],
  "validateOnly": false
}
```

등록 시 최소 동작:
- parse 성공 여부 확인
- 스크립트 digest 계산
- metadata 저장
- 중복 `scriptId/version` 거부
- 필요하면 `validateOnly` 지원

활성 버전 전환 예시:
```json
{
  "activeVersion": "2026.03.10.1"
}
```

### 3. Admin API
운영자가 TeeBox 내부에서만 쓰는 API다. 여기서는 task/process 관찰과 kill을 허용한다.

권장 namespace:
- `/api/admin/...`

권장 endpoint:
- `GET /api/admin/runs`
- `GET /api/admin/runs/{runId}`
- `GET /api/admin/runs/{runId}/threads`
- `GET /api/admin/runs/{runId}/tasks`
- `POST /api/admin/runs/{runId}/kill-tasks`
- `GET /api/admin/tasks`
- `GET /api/admin/tasks/{taskId}`
- `POST /api/admin/tasks/{taskId}/kill`
- 선택: `GET /api/admin/tasks/detached`

여기서만 노출 가능한 정보:
- pid
- pgid
- hostInstanceId
- stdout/stderr tail
- timeoutExceeded / healthHints
- detached task

## 스크립트 등록 모델
실제 운영에서는 `scriptsRoot`에 파일을 미리 배치하는 방식만으로는 부족하다. API 기반 등록을 위해 최소한 다음 개념이 필요하다.

### Script 식별자
- `scriptId`: 논리 이름. 예: `calibre-drc`
- `version`: immutable 버전 문자열. 예: `2026.03.10.1`
- `channel`: 선택적 별칭. 예: `prod`, `staging`, `canary`

### Script 저장 구조
권장 저장 구조:
- `dataDir/scripts/<scriptId>/metadata.json`
- `dataDir/scripts/<scriptId>/versions/<version>.pt`
- `dataDir/scripts/<scriptId>/versions/<version>.json`

metadata 예시:
```json
{
  "scriptId": "calibre-drc",
  "activeVersion": "2026.03.10.1",
  "versions": ["2026.03.10.0", "2026.03.10.1"]
}
```

version metadata 예시:
```json
{
  "scriptId": "calibre-drc",
  "version": "2026.03.10.1",
  "sha256": "...",
  "createdAt": 1770000000000,
  "createdBy": "layout-service",
  "description": "GF180 DRC run",
  "validation": {
    "ok": true,
    "error": null
  }
}
```

### 실행 모델
client는 `scriptPath`를 넘기지 않는다. 대신:
- `scriptId + version`
- 또는 `scriptId + channel`

TeeBox는 이를 내부 파일로 resolve해서 실행한다.

### 전환 단계
운영 전환 시에는 다음 순서를 권장한다.

1. 현재 `scriptPath` 모델 유지
2. publisher API로 `scriptId/version` 저장 도입
3. client run submit에 `scriptId/version` 추가
4. admin과 개발 경로에서만 `scriptPath` 허용
5. 최종적으로 외부 client에서는 `scriptPath` 제거

## 인증 / 권한 모델
토큰은 최소 3종으로 분리하는 편이 맞다.

- `client token`
  - run submit
  - run status/result 조회
- `publisher token`
  - script register
  - activate version
- `admin token`
  - task/process 조회
  - kill
  - detached 관리

중요한 점:
- publisher token은 client token보다 강하다
- admin token은 가장 강하다
- 가능하면 route namespace와 토큰 scope를 1:1로 맞춘다

## 응답 모델 권장안

### Run summary
```json
{
  "runId": "run-20260310-001",
  "scriptId": "calibre-drc",
  "version": "2026.03.10.1",
  "status": "RUNNING",
  "createdAt": 1770000000000,
  "startedAt": 1770000001000,
  "finishedAt": null,
  "hasExplicitReturn": false
}
```

### Run result
```json
{
  "runId": "run-20260310-001",
  "status": "COMPLETED",
  "resultData": {"ok": true, "violations": 0},
  "error": null
}
```

### Task summary for client
client에는 task 상세를 숨기고 필요한 경우 최소 요약만 준다.
```json
{
  "total": 2,
  "running": 1,
  "completed": 1,
  "failed": 0
}
```

## Mock upstream 테스트 관점
이 경계가 잡히면, 다음 단계의 mock upstream은 `client`와 `publisher` API만 쓰면 된다.

검증 대상:
- script register
- activate version
- run submit by `scriptId/version`
- poll status
- fetch result
- auth failure / retry / timeout

반대로 다음은 mock upstream 범위에서 제외하는 편이 맞다.
- task kill
- pid/pgid 조회
- detached 관리

이건 TeeBox admin 테스트 영역이다.

## 구현 우선순위
1. API namespace 분리
   - 현재 `/api`를 `client/admin`으로 먼저 분리
2. script registry 저장 모델 추가
3. publisher API 추가
4. run submit을 `scriptId/version` 기준으로 확장
5. `scriptPath`는 admin/dev 전용 경로로 축소
6. client-side mock harness 추가

## 결론
실운영 경계를 기준으로 보면 TeeBox는 단일 API 서버가 아니라 아래 세 역할을 분리해야 한다.

- client: 실행 요청과 결과 조회
- publisher: 스크립트 등록과 버전 관리
- admin: task/process 운영 제어

특히 `스크립트 등록`은 일반 실행 API와 같은 권한으로 두면 안 된다. `scriptPath` 직접 실행 모델은 초기 단계에서는 유용하지만, 운영 단계에서는 `scriptId/version` 중심 registry 모델로 옮겨가는 것이 맞다.
