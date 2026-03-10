# START_TASK / CANCEL_TASK 재검토 메모

## 배경
`79_task_cancel` 실패를 통해 확인된 핵심 문제는 단순한 타이밍 조정이 아니라, `START_TASK`가 반환할 때 어떤 프로세스를 "제어 대상"으로 보고 있는지 불명확했다는 점이다. helper shell, `setsid`, launcher PID를 추적하면 `CANCEL_TASK`가 실제 작업 프로세스를 놓칠 수 있다.

현재 구현은 이 점을 바로잡아 `START_TASK`가 실제 `command.sh` shell PID를 추적하고, 반환 전에 해당 PID가 제어 가능한 상태인지 확인하도록 수정돼 있다.

## 실제 실패 메커니즘
CI에서 실제로 문제를 만든 경로는 과거 `setsid` 기반 helper 경로였다.

- Linux에서 `setsid` helper가 개입하면 `$!`이 가리키는 PID가 실제 command shell이 아니라 중간 helper가 될 수 있다.
- 이 경우 `launcherPid`는 존재하더라도 `CANCEL_TASK`가 종료해야 하는 실질 작업 프로세스와 다를 수 있다.
- `command.pid` 기록과 PID 검증이 helper 기준으로 이뤄지면 `isOurProcess()`가 실패하거나, 반대로 잘못된 clean exit로 관측돼 `completed`로 오분류될 수 있다.

즉 핵심 문제는 "500ms가 짧다"가 아니라 "반환 전에 어떤 PID를 신뢰하느냐"였다. 그래서 현재 구현은 helper 체인을 줄이고 실제 `command.sh` shell PID를 직접 추적하는 쪽으로 바뀌었다.

## 이번 이슈에서 실제로 거친 수정
이번 문제는 한 번에 해결된 것이 아니라 몇 단계의 수정 끝에 현재 형태로 정리됐다.

- `resolveTrackedPid()` 대기 시간 조정과 fail-fast 강화 시도
- `START_TASK` 반환 전에 launch barrier를 두는 시도
- 최종적으로 `runner/setsid` helper 경로를 제거하고, 실제 `command.sh` shell PID를 직접 추적하도록 단순화

현재 구현에서 중요한 포인트는 마지막 단계다. 즉, `START_TASK` 계약은 "아무 PID나 하나 확보"가 아니라 "실제 제어할 command shell PID를 확보"하는 것으로 보는 편이 맞다.

## 현재 의미
- `START_TASK(...)`
  - detached launch를 수행한다.
  - task id를 반환하기 전에 추적 PID가 실제로 살아 있고 제어 가능한지 확인한다.
  - 아주 짧게 끝난 작업이면 종료 상태를 먼저 정리한 뒤 반환한다.
- `CANCEL_TASK(taskId)`
  - 추적 PID와 process group 정보를 사용해 종료를 시도한다.
  - 성공 반환 의미는 "실제로 살아 있던 task를 종료 상태로 전환했다"에 가깝다.

즉, 현재 `START_TASK`는 단순 fire-and-forget이 아니라 "즉시 취소 가능한 task handle 생성"에 더 가깝다.

## 더 깊이 검토할 부분

### 1. 반환 계약 고정
문서와 테스트에서 `START_TASK`의 의미를 명시해야 한다.

권장 계약:
- 반환 시점에 `TASK_STATUS(taskId).alive == true` 또는 이미 종료 상태여야 한다.
- 반환 직후 `CANCEL_TASK(taskId)`는 race 없이 동작해야 한다.
- 이 계약이 깨지면 버그로 본다.

### 2. PID가 아니라 "소유권" 모델
현재도 결국 PID 기반 추적이다. 장기적으로는 다음을 더 엄격히 볼 필요가 있다.
- 추적 PID가 실제 command shell인지
- 그 PID가 task의 process group leader인지
- 자식/손자 프로세스가 shell 밖으로 이탈했을 때 어떻게 볼지

특히 shell command 안에서 다시 background job을 띄우는 패턴은 운영 정책으로 금지하거나, 별도 규칙으로 다뤄야 한다.

### 3. shell 의존 경계
`START_TASK("...")`가 raw shell command를 직접 받는 한, shell grammar와 프로세스 트리 복잡성이 계속 따라온다.

장기 권장 방향:
- 자주 쓰는 작업은 `RUN_TASK("name", args)` 식의 등록형 adapter로 승격
- `START_TASK`는 escape hatch로 유지
- 운영 환경에서는 허용 command/script 범위를 제한

### 4. 상태 모델
현재 status는 `running/completed/failed/killed/lost/detached` 중심이다. 여기서 더 중요한 것은 상태 이름보다 전이 규칙이다.

특히 명확히 해야 할 점:
- `killed`는 terminal state여야 한다.
- `completed`는 exit code 파일이 확인된 clean exit일 때만 사용한다.
- `lost`는 조회 시점 transient 오판이 아니라, 재기동 후에도 정체를 확인할 수 없을 때에 가깝게 써야 한다.

추가로 현재 구현과 목표 상태 정의의 차이도 문서에 남겨둘 필요가 있다.
- 목표 정의: `lost`는 재기동 후에도 정체를 확인할 수 없는 경우에 가까워야 한다.
- 현재 구현: `finalizeExitedTask()` 경로에서 exit code 파일을 짧은 grace window 안에 읽지 못하면 `lost`가 찍힐 수 있다.

즉, 현재는 transient I/O 지연이나 flush 지연에도 `lost`가 설정될 가능성이 있다. 이 부분은 향후 우선순위가 높은 상태 전이 정리 대상이다.

### 5. 즉시 취소와 graceful 취소 분리
현재 `CANCEL_TASK`는 운영상 강한 종료에 가깝다. 이후 필요하면 다음을 분리하는 편이 낫다.
- `CANCEL_TASK`: graceful cancel (`TERM` 중심)
- `KILL_TASK`: 강제 종료 (`KILL` 보장)

특히 HPC/EDA 도구는 종료 훅이나 출력 flush가 중요할 수 있으므로, 장기적으로는 의미 분리가 필요하다.

### 6. 플랫폼 가정 점검
현재 구현은 `/bin/sh`, `nohup`, `kill`, `ps`에 기대고 있다. 폐쇄망 Linux 운영에는 맞지만, 다음은 계속 확인해야 한다.
- 배포 대상 OS가 GNU/Linux로 고정되는지
- `ps -o lstart`, `pgid` 조회 형식이 일관적인지
- 컨테이너/namespace 환경에서 PID/PGID 조회가 깨지지 않는지

구체적으로 현재 `getProcessStartTime()`은 `ps -o lstart=` 출력을 `SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH)`로 파싱한다. 이건 배포판별 `ps` 출력 형식, locale, timezone 표기 차이에 민감하다.

현재는 same-instance task에 대해 `ownedTaskIds` fallback이 있어 어느 정도 완화되지만, 이 fallback이 없는 경로, 예를 들어 서버 재시작 후 adopted task 판정에서는 `isOurProcess()`가 항상 false가 될 수 있다. 이건 단순 파서 문제가 아니라 섹션 2의 "소유권 모델"과 직접 연결된다.

### 7. 테스트 강화
현재 실패를 잡은 회귀 테스트는 의미가 있다. 여기에 아래를 추가하면 더 견고해진다.
- `START_TASK` 직후 즉시 `CANCEL_TASK`를 여러 번 반복하는 stress test
- 매우 짧게 끝나는 task (`echo hi`)와 즉시 cancel 경쟁
- shell 내부에서 child를 background로 띄우는 패턴
- stdout/stderr를 많이 쓰는 long-running task
- launch 후 곧바로 host-side `killTask()`를 다른 engine instance에서 호출하는 경로

추가로 launch 전략 자체를 테스트 가능한 구조로 유지하는 것도 중요하다.
- 과거 CI 실패는 `setsid` helper 경로에서만 드러났다.
- macOS처럼 `setsid` 경로가 없던 환경에서는 동일 문제가 재현되지 않았다.
- 따라서 향후 session helper / process-group helper 경로가 다시 들어간다면, launch strategy를 주입 가능하게 만들고 각 경로를 unit test로 강제하는 편이 좋다.

## 장기 방향
ProperTee에서 외부 작업은 "shell 문자열 실행"이 아니라 "관리 가능한 작업 핸들"로 다뤄지는 쪽이 맞다.

즉 장기적으로는:
1. `START_TASK` 계약을 명시적으로 유지
2. raw shell보다 등록형 task adapter를 확대
3. 상태 전이와 kill semantics를 더 엄격히 문서화
4. 운영자가 보는 관찰 데이터(`pid`, `pgid`, `lastOutputAgeMs`, `timeoutExceeded`)를 계속 강화

## 결론
이번 이슈는 `sleep` 타이밍 문제가 아니라 `START_TASK`의 반환 의미가 약했던 문제였다. 현재 수정으로 즉시 제어 가능성은 강화됐지만, 외부 프로세스 모델은 여전히 shell과 PID semantics에 기대고 있다. 따라서 앞으로의 핵심은 타이밍 튜닝이 아니라 다음 두 가지다.

- `START_TASK`가 무엇을 보장하는지 계약을 고정하는 것
- raw shell 실행을 점진적으로 등록형 작업 모델로 밀어내는 것
