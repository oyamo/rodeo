# rodeo

Push-triggered pipeline runner for Git repositories. On a push to a configured branch, rodeo clones the branch and runs the stages declared in the repository's `rodeo.toml`, each in its own container.

![Java](https://img.shields.io/badge/language-Java%2021-orange)
![Coverage](https://img.shields.io/badge/coverage-89%25-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.9-6DB33F)
[![Docs](https://img.shields.io/badge/docs-oyamo.github.io%2Frodeo-blue)](https://oyamo.github.io/rodeo/)

## Example

```toml
on = ["main"]

# a shell script file, using the server's default image
[frontend]
build = "./scripts/build-frontend.sh"

[frontend.deploy]
run = "./scripts/deploy-frontend.sh"
needs = ["build", "payment-service.deploy"]
env_file = "deploy/production.env"
network = true

# five commands, run in order, in the stage's own image
[payment-service]
image = "maven:3.9-eclipse-temurin-21"

build = [
  "mvn -B -q clean",
  "mvn -B verify",
  "mvn -B -DskipTests package",
  "mkdir -p dist",
  "cp target/*.jar dist/",
]

[payment-service.deploy]
run = "./scripts/deploy-payment.sh"
needs = ["build"]
network = true

# docker build and deploy, on the server, where the Docker daemon runs
[auth-service.build]
run = [
  "docker build -t auth-service:$RODEO_COMMIT .",
  "docker tag auth-service:$RODEO_COMMIT auth-service:latest",
]
host = true

[auth-service.deploy]
teardown = "docker compose down"
run = "docker compose up -d auth-service"
needs = ["build"]
host = true
```

## Quick start

```bash
mvn -B package
java -jar target/rodeo-0.1.0-SNAPSHOT.jar --rodeo.config=/etc/rodeo/config.toml
```
