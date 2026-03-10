# 원격 Linux 자동 테스트

내부망 Linux 서버에 SSH로 접근할 수 있다면, 로컬에서 원격 테스트를 자동 실행할 수 있다. 현재 저장소에는 두 개의 스크립트를 둔다.

## 전제 조건
- SSH key 기반 로그인 가능
- 원격 서버에 `bash`, `tar`, `java` 설치
- Gradle 테스트를 돌릴 경우 원격 서버에 빌드 가능한 JDK가 있어야 함
- TeeBox smoke 테스트를 돌릴 경우 원격 서버에 `unzip`, `curl`이 있어야 함

## 1. 저장소 업로드 후 원격 테스트 실행

스크립트:
- [remote-linux-test.sh](/Users/journey/Flatide/propertee-java/scripts/remote-linux-test.sh)

기본 동작:
1. 현재 저장소를 tar stream으로 원격 서버에 업로드
2. 원격 디렉터리에서 테스트 실행
3. test results / reports를 로컬 `build/remote-linux/<timestamp>/`로 회수

기본 profile:
- `linux-regression`
- 포함:
  - `ScriptTest.testScript[79_task_cancel]`
  - `TaskEngineTest`

기본 실행 명령:
```bash
scripts/remote-linux-test.sh user@linux-host
```

기본 원격 테스트 커맨드:
```bash
./gradlew --no-daemon :propertee-core:test --tests com.propertee.tests.ScriptTest.testScript[79_task_cancel] --tests com.propertee.tests.TaskEngineTest
```

전체 core 회귀로 넓히려면:
```bash
REMOTE_TEST_PROFILE=all-core scripts/remote-linux-test.sh user@linux-host
```

`79_task_cancel`만 빠르게 확인하려면:
```bash
REMOTE_TEST_CMD='./gradlew --no-daemon :propertee-core:test --tests com.propertee.tests.ScriptTest.testScript[79_task_cancel]' \
scripts/remote-linux-test.sh user@linux-host
```

유용한 환경 변수:
- `REMOTE_TEST_PROFILE`
- `REMOTE_TEST_CMD`
- `REMOTE_SSH_KEY`
- `REMOTE_SSH_PORT`
- `REMOTE_SSH_OPTS`
- `KEEP_REMOTE=1`

예시:
```bash
REMOTE_SSH_KEY=~/.ssh/id_ed25519_flatidetest \
REMOTE_SSH_PORT=22 \
scripts/remote-linux-test.sh journey@192.168.1.107
```

## 2. TeeBox 배포본 원격 smoke 테스트

스크립트:
- [remote-teebox-smoke.sh](/Users/journey/Flatide/propertee-java/scripts/remote-teebox-smoke.sh)

기본 동작:
1. 로컬에서 `teeBoxZip` 빌드
2. 원격 Linux 서버에 zip 업로드
3. 원격에서 unpack
4. `127.0.0.1`에 TeeBox 기동
5. `/admin` 응답 확인
6. 서버 종료 및 로그 회수

실행:
```bash
scripts/remote-teebox-smoke.sh user@linux-host
```

포트 변경:
```bash
TEEBOX_PORT=18081 scripts/remote-teebox-smoke.sh user@linux-host
```

키 지정:
```bash
REMOTE_SSH_KEY=~/.ssh/id_ed25519_flatidetest \
REMOTE_SSH_PORT=22 \
scripts/remote-teebox-smoke.sh journey@192.168.1.107
```

## 권장 운영 방식
- 빠른 회귀 확인: `remote-linux-test.sh`
- 배포본 기동 확인: `remote-teebox-smoke.sh`
- `79_task_cancel`, `TaskEngineTest`, TeeBox smoke를 내부망 Linux에서 정기적으로 확인

## 제한
- 현재는 SSH 기반 ad-hoc 실행이다.
- 장기적으로는 내부망 Jenkins 또는 self-hosted runner로 옮기는 편이 더 낫다.
- 원격 서버가 외부 인터넷이 안 되면 Gradle dependency cache가 미리 준비돼 있어야 한다.
