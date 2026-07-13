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
- 포함: `ScriptTest` 전체 픽스처 스위트(SHELL 태스크 픽스처 `72_shell`/`78_task_basic`/`80_task_unique_ids` 포함, 수 초 소요)

  참고: 단일 픽스처만 고르는 `--tests "...testScript[NN_name]"` 형식은 Gradle 9.3.1의 `--tests`가 파라미터 표시명을 매치하지 못해 동작하지 않는다(클래스 단위로 실행). 과거 이 문서가 지시하던 `79_task_cancel` 픽스처는 START_TASK 계열 제거(d71f35c) 때, `TaskEngineTest` 클래스는 플랫폼 태스크 러너의 core 제거(e6b5880) 때 각각 삭제되어 더 이상 존재하지 않는다.

기본 실행 명령:
```bash
scripts/remote-linux-test.sh user@linux-host
```

기본 원격 테스트 커맨드:
```bash
./gradlew --no-daemon :propertee-core:test --tests com.flatide.propertee.tests.ScriptTest
```

전체 core 회귀로 넓히려면:
```bash
REMOTE_TEST_PROFILE=all-core scripts/remote-linux-test.sh user@linux-host
```

회귀 범위를 `ScriptTest` 스위트로만 좁히려면:
```bash
REMOTE_TEST_CMD='./gradlew --no-daemon :propertee-core:test --tests com.flatide.propertee.tests.ScriptTest' \
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
- `ScriptTest` 스위트(SHELL 태스크 픽스처 포함)와 TeeBox smoke를 내부망 Linux에서 정기적으로 확인

## 제한
- 현재는 SSH 기반 ad-hoc 실행이다.
- 장기적으로는 내부망 Jenkins 또는 self-hosted runner로 옮기는 편이 더 낫다.
- 원격 서버가 외부 인터넷이 안 되면 Gradle dependency cache가 미리 준비돼 있어야 한다.
