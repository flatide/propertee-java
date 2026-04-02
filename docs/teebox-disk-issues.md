# TeeBox 디스크 점유 문제 분석 (2026-03-12)

## 요약

TeeBox 서버 실행 중 디스크 I/O가 급격히 증가하는 구조적 문제 다수 발견.
핵심 원인: 로그 줄/스레드 상태 변경마다 전체 JSON 파일 재작성 + 백그라운드 정리 없음.

## 발견된 문제

### 1. [CRITICAL] 로그 줄마다 전체 JSON 파일 재작성

**위치:** `RunRegistry.java:104-112`, `RunStore.java:37-53`

- `appendLog()` → `saveRunLocked()` → `runStore.save()` → runId.json 전체 재작성 + index.json 전체 재작성
- 스크립트 1,000줄 출력 → 1,000회 × 2파일 = 2,000회 전체 재작성
- 링 버퍼(200줄)는 메모리만 제한 — 디스크 I/O 횟수는 무제한

### 2. [CRITICAL] 스레드 상태 변경마다 전체 JSON 재작성

**위치:** `RunManager.java:212-228`, `RunRegistry.java:115-130`

- 스레드 create/update/complete/error 콜백마다 `upsertThread()` → `saveRunLocked()`
- multi 블록 5스레드 × 1,000 step = 5,000회 이상 전체 JSON 재작성

### 3. [CRITICAL] 백그라운드 정리(cleanup) 스케줄러 없음

**위치:** `RunManager.java:350-355, 402-404`

- `maintainRuns()`와 `maintainTasks()`가 API 호출 시에만 실행
- API 활동 없으면 archiving/purge가 영원히 실행 안 됨
- 서버 장시간 운영 시 `runs/`, `tasks/` 디렉토리 무한 증가

### 4. [HIGH] Task stdout/stderr 파일 무제한 성장

**위치:** `TaskEngine.java:606-613`

- 프로세스 출력이 크기 제한 없이 파일로 리다이렉트
- archiving(기본 24시간 후)까지 stdout.log/stderr.log 무한 성장
- 장시간 프로세스의 100MB+ 로그가 디스크에 누적

### 5. [HIGH] Index 파일의 O(N) 전체 재작성 패턴

**위치:** `RunStore.java:124-143`, `TaskEngine.java:412-433`

- 매 상태 변경마다: 전체 로드 → 1건 업데이트 → 전체 정렬 → 전체 재작성
- 항목 N개 시 매번 O(N) 비용

### 6. [MEDIUM] ScriptRegistry 버전 영구 보존

**위치:** `ScriptRegistry.java:106-113`

- 버전 삭제/purge API 없음
- 반복 배포 시 `.tee` 파일 무한 누적

## 실제 시나리오 (스크립트 1개, 3스레드, 500줄 출력)

| 이벤트 | 횟수 | 디스크 쓰기 |
|---|---|---|
| PRINT (stdout) | 500 | runId.json × 500 + index.json × 500 |
| Thread 생성 | 3 | runId.json × 3 + index.json × 3 |
| Thread 업데이트 | ~100 | runId.json × 100 + index.json × 100 |
| Thread 완료 | 3 | runId.json × 3 + index.json × 3 |
| markStarted/Completed | 2 | runId.json × 2 + index.json × 2 |
| **합계** | | **~608회 × 2파일 ≈ 1,216회 디스크 write** |

## 수정 계획

1. **RunRegistry 디바운싱**: appendLog/upsertThread를 즉시 저장하지 않고 dirty 플래그 + 주기적 flush
2. **RunStore index 분리**: save()에서 index 업데이트를 분리, 별도 flush
3. **백그라운드 정리 스케줄러**: RunManager에 ScheduledExecutorService 추가
4. **Task stdout 크기 경고**: TaskEngine observe()에서 파일 크기 health hint 추가
