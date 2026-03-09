# ProperTee Mock Server Deployment

This bundle runs the mock admin server without Gradle.

## Build the bundle

```bash
./gradlew mockServerZip
```

Output:

```text
build/distributions/propertee-mockserver-dist.zip
```

## Deploy on another server

1. Copy `propertee-mockserver-dist.zip` to the target server.
2. Unzip it.
3. Edit `conf/mockserver.properties`.
4. Create the configured `scriptsRoot` and `dataDir` directories.
5. Start the server:

```bash
./bin/run-mockserver.sh
```

The script accepts extra CLI arguments and respects:

- `PROPERTEE_MOCK_CONFIG`
- `JAVA_HOME`
- `JAVA_OPTS`

## Notes

- The deployment jar is `lib/propertee-mockserver.jar`.
- System properties in `JAVA_OPTS` override values from `conf/mockserver.properties`.
- The server exposes `/admin` HTML UI and `/api/*` JSON endpoints.
