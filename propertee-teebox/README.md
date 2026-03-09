# TeeBox

TeeBox is the ProperTee execution service module. It exposes an HTTP admin UI and JSON API for submitting ProperTee scripts, tracking runs, monitoring external tasks, and performing manual task control.

## What It Provides

- `POST /api/runs` to submit a script from the configured `scriptsRoot`
- `GET /api/runs` and `GET /api/tasks` for run/task listing
- `/admin` HTML UI for operators
- external task tracking through `TaskEngine`
- persisted run/task indexes with archive and purge support

## Main Components

- [`TeeBoxMain.java`](/Users/journey/Flatide/propertee-java/propertee-teebox/src/main/java/com/propertee/teebox/TeeBoxMain.java)
  - process entry point
- [`TeeBoxServer.java`](/Users/journey/Flatide/propertee-java/propertee-teebox/src/main/java/com/propertee/teebox/TeeBoxServer.java)
  - HTTP routing and API/admin handlers
- [`RunManager.java`](/Users/journey/Flatide/propertee-java/propertee-teebox/src/main/java/com/propertee/teebox/RunManager.java)
  - run lifecycle and task lookup
- [`RunRegistry.java`](/Users/journey/Flatide/propertee-java/propertee-teebox/src/main/java/com/propertee/teebox/RunRegistry.java)
  - persistent run storage and indexing
- [`AdminPageRenderer.java`](/Users/journey/Flatide/propertee-java/propertee-teebox/src/main/java/com/propertee/teebox/AdminPageRenderer.java)
  - server-rendered admin UI

## Quick Start

```bash
./gradlew teeBoxZip
./gradlew runTeeBox \
  -Dpropertee.teebox.scriptsRoot=$PWD/propertee-teebox/demo/teebox \
  -Dpropertee.teebox.dataDir=/tmp/propertee-teebox-data
```

Open `http://127.0.0.1:18080/admin`.

## GitHub Download

`propertee-teebox-dist.zip` is published on GitHub Releases when a repository tag such as `v0.3.1` is pushed.

```bash
git tag v0.3.1
git push origin v0.3.1
```

## Configuration

Primary settings use the `propertee.teebox.*` prefix:

- `propertee.teebox.bind`
- `propertee.teebox.port`
- `propertee.teebox.scriptsRoot`
- `propertee.teebox.dataDir`
- `propertee.teebox.maxRuns`
- `propertee.teebox.apiToken`
- `propertee.teebox.runRetentionMs`
- `propertee.teebox.runArchiveRetentionMs`

## Related Docs

- Deployment bundle: [`deploy/teebox/README.md`](/Users/journey/Flatide/propertee-java/propertee-teebox/deploy/teebox/README.md)
- Demo scripts: [`demo/teebox/README.md`](/Users/journey/Flatide/propertee-java/propertee-teebox/demo/teebox/README.md)
- Evolution plan: [`demo/teebox/PLAN.md`](/Users/journey/Flatide/propertee-java/propertee-teebox/demo/teebox/PLAN.md)
