# ProperTee — Java 21+ Virtual-Thread 런타임 설계도 (Strategy B)

> 상태: 설계 초안 (검토용). 구현 전 합의용 문서.
> 대상: TeeBox가 사용할 **별도 프로젝트**. 레거시 expression-evaluator 서버는 동결된 `propertee-java` v1.0.0(Java 7/8)을 계속 사용.

## 0. 목적과 범위

**목표.** v1.0.0(Java 7/8, stepper 기반)에 남아 있는 eager seam을 근본적으로 제거한 **완전 협력형 런타임**을 만든다. virtual thread(Project Loom, Java 21 정식)를 사용해 "콜스택 어디서든 중단"을 달성한다.

v1.0.0에 남아 있는 seam(이 런타임이 닫으려는 대상):
| # | seam | 성격 |
|---|---|---|
| 1 | expression 내부 `SLEEP`(`x = f()` 등) → blocking | 동시성 |
| 2 | `multi` setup phase eager | 동시성 |
| 3 | expression-call 안 긴 loop의 fairness | 동시성 |
| 4 | async statement-replay 선행 부작용 2회 실행 | **정확성** |

**소비처.** TeeBox(독립 서버, JDK 21+ 자유). **비목표:** Java 7/8 호환(포기), 문법 변경(동일 유지), 1단계에서 진짜 병렬(§9 옵션으로 후술).

## 1. 핵심 모델 — virtual thread + 단일 바톤(cooperative)

- ProperTee 논리 스레드 1개 = **Java virtual thread 1개**.
- **단일 바톤**(permit 1 `Semaphore` 또는 `LockSupport park/unpark`)으로 "한 번에 하나의 스레드만 인터프리터 코드를 실행"을 강제 → 기존 **purity·무락·결정론** 의미를 그대로 보존.
- yield 지점(`SLEEP` / blocking I/O / spawn join / 문장·반복 경계)에서 바톤을 반납 → 스케줄러가 다음 READY 스레드를 unpark.
- **Java 콜스택 자체가 continuation**이 되므로 표현식 한복판·중첩 함수·루프 내부 어디서든 중단 가능. → seam 1·2 해결, mid-expression 해결.

> 왜 platform thread가 아니라 vthread인가: 과거 우려(스레드당 OS 자원, 수백 개 blocked thread)가 사라진다. vthread는 park해도 carrier를 점유하지 않아 수천~수만 개가 저렴. blocking I/O도 carrier를 막지 않는다.

## 2. 인터프리터 단순화 (삭제 중심)

이 런타임은 코드를 **더하기보다 빼는** 리팩터링이다.

**삭제:** `Stepper`/`StepResult`/`SchedulerCommand`, `StatementListStepper`·`IfStepper`·`LoopStepper`(계열)·`UserCallStepper`, `AsyncPendingException` + statement replay, `CommandThenDoneStepper`/`ImmediateStepper`, 기존 `Scheduler`의 round-robin `step()` 루프, `activeThread` 스코프 라우팅 핵.

**복원:** `visit*`가 값을 직접 반환하는 **평범한 재귀 tree-walk**. `evalBlock`/loop/`callUserFunction`이 그냥 재귀 호출. `if`/loop/함수 본문 특별 취급 없음. `SLEEP`/spawn/async/yield만 Coop 런타임 primitive를 호출.

## 3. 스케줄러 primitive (Coop)

```
Coop.yield()              // 다른 READY 스레드 있으면 바톤 반납·재획득 (fairness, seam 3)
Coop.sleep(ms)            // wake-time 등록 → 바톤 반납 → park; 타이머 만료 시 READY (seam 1)
Coop.blocking(supplier)   // 바톤 반납 → 이 vthread에서 블로킹 호출(SHELL/HTTP/DB)
                          //   → 결과 수령 → 바톤 재획득 → 그 자리에서 값 반환 (replay 없음, seam 4)
Coop.spawnMulti(specs, monitor)  // 자식 vthread 생성 → 부모는 join에서 바톤 반납·park
                                 //   → 자식 협력 실행 → 완료 시 결과 수집 후 부모 READY
```

- 문장 경계·루프 반복 경계에서 `Coop.yield()` 삽입 → 현재의 per-statement/per-iteration BOUNDARY와 동일 입도의 round-robin 유지.
- `Coop.blocking`이 핵심: async가 **replay 없이** 콜스택 보존 상태로 중단·재개 → seam 4(정확성) 근본 제거. mid-expression async도 자연 동작.

## 4. `multi` = StructuredTaskScope

- 워커별 vthread를 `scope.fork(...)`. 부모는 `scope.join()`에서 **협력적으로** 대기(바톤 반납).
- 결과 수집·에러 전파·취소를 STS가 **구조적으로** 보장 → 현재 `Scheduler`의 자식 관리/`resultCollection` in-place 갱신 로직을 상당 부분 대체.
- **setup phase도 협력형**(콜스택 실행) → seam 2 해결. 중첩 `multi` setup의 `SLEEP`도 outer 워커를 안 막음.
- **monitor:** 인터벌 타이머가 바톤 하에 monitor 블록을 잠깐 실행(read-only, 라이브 result 읽기). 동작 동일.
- **purity/결과 포맷 불변:** 워커는 스냅샷 read만, global write 금지, 결과 `{status, ok, value}` 동일.

## 5. per-thread 컨텍스트 = ScopedValue

- `activeThread` 라우팅 핵 대신, 각 vthread가 자신의 `ScopeStack`/글로벌 스냅샷을 `ScopedValue`로 보유.
- 단일 바톤이라 공유 인터프리터 가변 상태에 대한 동시 접근이 없음 → 안전. `ScopedValue`는 불변·구조적이라 ThreadLocal보다 깔끔.

## 6. 결정론 & yield 지점

- 출력 재현성을 위해 yield는 **명시 지점에서만**: 문장 경계, 루프 반복 경계, `SLEEP`/async/spawn.
- vthread 자체 스케줄링의 비결정성은 **바톤이 차단**(한 번에 하나) → 순서는 스케줄러가 yield 지점에서 round-robin으로 결정. 기존 `.expected`가 그대로 conformance 기준.

## 7. 재사용 자산

- 문법 `ProperTee.g4`(파서 재생성), builtins 로직, `TypeChecker`/값 모델, `Result`/`PlatformProvider`/`TaskRunner` 인터페이스.
- **테스트 스위트** — `tests/*.tee` + `*.expected` 85쌍, `CooperativeNestingTest`, `SleepNestingTest` → 신규 엔진의 **의미 동치 검증 하네스**. (이게 최강 안전망.)

## 8. 마이그레이션 / 패리티 전략

1. 문법 + builtins + 값모델 포팅(거의 그대로), JDK 21 toolchain Gradle.
2. Coop 런타임 + 재귀 인터프리터 구현.
3. 기존 `.tee/.expected` **전체 통과**로 의미 동치 확보.
4. seam 타이밍 테스트 추가: `x = f()` 내부 `SLEEP`, 중첩 `multi` setup `SLEEP`, mid-expression `a + f()`가 이제 **오버랩(~1x)** 인지.
5. **async replay 제거 검증:** "async 직전 선행 부작용"이 2회→1회로 바뀌는 회귀 테스트(정확성 개선의 증거).

## 9. 위험 & 미해결

- **vthread pinning**(`synchronized`/native): 단일 바톤 모델에선 영향 경미(한 vthread pin이 전체를 막지 않음). JFR `jdk.VirtualThreadPinned`로 모니터.
- **kill/timeout:** vthread interrupt + STS timeout. 현재 TaskRunner kill 의미와 매핑.
- **fairness 무한 CPU 루프:** `Coop.yield()` 지점으로 완화하되 loop iteration limit 유지.
- **진짜 병렬(옵션, 후속):** purity model상 워커 병렬 실행은 의미상 안전하나, 공유 인터프리터 상태를 thread-confined(ScopedValue/per-thread scope)로 만들어야 함. I/O 바운드인 TeeBox엔 매력적이나 **1단계 비목표** — 협력형 동치 확보 후 별도 단계로 평가.

## 10. 프로젝트 구조 & 단계

- **별도 repo:** 예) `propertee-jvm21`(또는 `propertee-loom`). group은 `com.flatide` 유지, artifact 분리. TeeBox가 의존 대상을 이쪽으로 전환(안정화 후).
- **모듈:** `core`(grammar + 재귀 interpreter + Coop runtime), `cli`.
- **단계:**
  - PA. JDK21 Gradle 골격 + 문법/builtins/값모델 포팅 + 기존 `.tee/.expected` 반입
  - PB. 재귀 인터프리터(stepper 제거판)
  - PC. Coop 런타임(바톤, `sleep`, `yield`, `blocking`)
  - PD. `multi`/monitor를 StructuredTaskScope로
  - PE. conformance(.expected 전체 통과) + seam 타이밍 테스트 + async replay-제거 테스트
  - PF. 문서/릴리스(0.1.0부터)

---

### 부록: seam 해결 매핑 요약

| seam | v1.0.0 | vthread 런타임 |
|---|---|---|
| 1 expression 내 SLEEP | blocking | `Coop.sleep` (콜스택 park) ✅ |
| 2 multi setup eager | blocking | setup도 협력 실행 ✅ |
| 3 fairness CPU 루프 | 점유 | `Coop.yield` 지점 ✅(완화) |
| 4 async replay 부작용 | 2회 실행 | `Coop.blocking`(replay 제거) ✅ |
| mid-expression 일반 | eager | 콜스택 중단 ✅ |
