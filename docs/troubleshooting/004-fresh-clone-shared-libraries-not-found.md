# 004 — Fresh Clone: Shared Libraries Not Found

## Symptom
```
Could not find com.rtrs:rtrs-common:1.0.0.
Could not find com.rtrs:rtrs-events:1.0.0.
Could not find com.rtrs:rtrs-security:1.0.0.

Execution failed for task ':compileJava'.
> Could not resolve all files for configuration ':compileClasspath'.
```

## Root Cause
Shared libraries (`rtrs-common`, `rtrs-events`, `rtrs-security`) are Maven modules.
Gradle services resolve them from the local `.m2` repository via `mavenLocal()`.
On a fresh clone, the `.m2` cache does not exist — the libs have never been built on
this machine — so Gradle cannot find them.

## Solution
Run the setup script from the project root to build and install all three shared libs
into the local Maven repository in the correct order.

**Windows:**
```bat
.\setup.bat
```

**Unix / Mac:**
```bash
bash setup.sh
```

## Prerequisites
Both must be installed and on PATH before running the script:

```bash
java -version   # Must be Java 21
mvn -version    # Must be Maven 3.x+
```

**Maven not installed on Windows?**
```powershell
choco install maven
```
Then close and reopen PowerShell before retrying.

## Affected
All developers on a fresh clone. Not a recurring issue — only needed once per machine.