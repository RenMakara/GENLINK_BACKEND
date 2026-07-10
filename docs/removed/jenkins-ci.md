# Jenkins CI Pipeline

This document explains the Jenkins pipeline for the CampusLink KH backend project.

The pipeline is CI only. It does not deploy to a server.

## What The Pipeline Does

```text
Checkout source code
Prepare Gradle wrapper
Validate Java and Gradle
Run automated tests
Generate JaCoCo coverage report
Run SonarQube analysis
Wait for SonarQube Quality Gate
Build the Spring Boot JAR
Archive test, coverage, and JAR artifacts
```

## Files Added For CI

| File | Purpose |
|---|---|
| `Jenkinsfile` | Defines the Jenkins pipeline stages |
| `build.gradle` | Adds JaCoCo coverage and SonarQube Gradle support |
| `docs/jenkins-ci.md` | Explains the CI setup for the team |

## Jenkins Requirements

Install or configure these on the Jenkins server:

- JDK 21
- Git
- Jenkins Pipeline support
- Jenkins JUnit result publishing
- SonarQube Scanner for Jenkins plugin, if using SonarQube
- A configured SonarQube server in Jenkins named `SonarQube`

The Jenkins global tool name for JDK 21 does not need to be hardcoded in the pipeline because the project Gradle toolchain requests Java 21. The Jenkins machine still needs a working Java 21 installation.

## Required Jenkins Plugins

Recommended Jenkins plugins:

```text
Pipeline
Git
JUnit
SonarQube Scanner
Workspace Cleanup, optional
```

## SonarQube Setup

In Jenkins, configure SonarQube:

```text
Manage Jenkins
→ System
→ SonarQube servers
→ Add SonarQube
```

Use this server name:

```text
SonarQube
```

Create a SonarQube token and save it in Jenkins credentials. Jenkins will expose the SonarQube URL and token inside the `withSonarQubeEnv('SonarQube')` block.

The Gradle SonarQube project is configured as:

```text
sonar.projectKey=campuslink-kh-backend
sonar.projectName=CampusLink KH Backend
```

## Quality Gate

The pipeline waits for the SonarQube Quality Gate.

If the Quality Gate fails, Jenkins stops the pipeline.

Typical Quality Gate checks:

- New blocker bugs
- New critical vulnerabilities
- Code smells
- Duplicated code
- Coverage on new code

For `waitForQualityGate` to work correctly, configure a SonarQube webhook pointing back to Jenkins:

```text
http://JENKINS_URL/sonarqube-webhook/
```

## Jenkins Job Setup

Recommended job type:

```text
Pipeline from SCM
```

Use these values:

```text
SCM: Git
Repository URL: your backend repository URL
Branch: main, develop, or your working branch
Script Path: Jenkinsfile
```

## Pipeline Parameters

The pipeline has one parameter:

| Parameter | Default | Purpose |
|---|---:|---|
| `RUN_SONAR` | `true` | Runs SonarQube analysis and Quality Gate |

If SonarQube is not ready yet, run the job with:

```text
RUN_SONAR=false
```

This allows Jenkins to build and test the backend first.

## Spring Profile In Jenkins

Jenkins CI should not use `dev`, `stack`, or `prod` for normal tests.

The project has test configuration here:

```text
src/test/resources/application-test.yaml
```

That test config uses:

- H2 in-memory database
- Test JWT values
- Test mail values
- Liquibase disabled

This keeps Jenkins independent from local PostgreSQL.

## Common Commands

Run tests and coverage locally:

```powershell
.\gradlew.bat clean test jacocoTestReport
```

Build the backend JAR locally:

```powershell
.\gradlew.bat bootJar
```

Run SonarQube locally, if you have a token:

```powershell
.\gradlew.bat sonar `
  -Dsonar.host.url=http://localhost:9000 `
  -Dsonar.token=your-token
```

## Troubleshooting Java

If Jenkins or your local terminal shows this error:

```text
JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

Make sure Java 21 is installed and visible to the shell.

For this machine, Java was found here:

```text
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
```

Temporary PowerShell fix for the current terminal:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
```

For Jenkins, configure the same Java 21 installation on the Jenkins agent or add it in:

```text
Manage Jenkins
→ Tools
→ JDK installations
```

## Pipeline Stages Explained

### Checkout

Jenkins pulls the source code from Git.

### Prepare Gradle Wrapper

On Linux Jenkins agents, Jenkins makes sure `gradlew` is executable:

```text
chmod +x ./gradlew
```

### Validate Tools

Jenkins prints:

```text
java -version
gradlew --version
```

This helps the team quickly see if Java or Gradle is misconfigured.

### Test and Coverage

Jenkins runs:

```text
gradlew clean test jacocoTestReport
```

This creates:

```text
build/test-results/test
build/reports/jacoco/test
```

### SonarQube Analysis

Jenkins sends code quality, test result, and coverage information to SonarQube.

### Quality Gate

Jenkins waits for SonarQube to approve or reject the code quality result.

### Build Jar

Jenkins builds the backend JAR:

```text
build/libs/*.jar
```

The JAR is archived by Jenkins so the team can download it from the build result.

## What This Pipeline Does Not Do Yet

This first version intentionally does not:

- Deploy to a server
- Push a Docker image
- Run the production profile
- Start PostgreSQL with Docker Compose
- Run database migration against production

Those steps should be added later after the team is comfortable with CI.

## Team Explanation

Use this short explanation for teammates:

```text
Our Jenkins pipeline checks the backend before deployment. It pulls the code, verifies Java 21, runs tests, generates coverage, sends code quality results to SonarQube, checks the Quality Gate, and builds the Spring Boot JAR. It does not deploy anything yet.
```

