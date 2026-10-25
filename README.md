# rodeo

Push-triggered pipeline runner for Git repositories. On a push to a configured branch, rodeo clones the branch and runs the stages declared in the repository's `rodeo.toml`, each in its own container.

![Java](https://img.shields.io/badge/language-Java%2021-orange)
![Coverage](https://img.shields.io/badge/coverage-89%25-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.9-6DB33F)
[![Docs](https://img.shields.io/badge/docs-oyamo.github.io%2Frodeo-blue)](https://oyamo.github.io/rodeo/)

## Example

```toml
on = ["main"]

[frontend]
build = "npm ci && npm run build"
deploy = {
  run = "cp -r dist/. /usr/share/nginx/html/",
  needs = ["build"],
  host = true,
}

[payment-service]
image = "maven:3.9-eclipse-temurin-21"
build = [
  "mvn -B clean",
  "mvn -B verify",
  "mvn -B -DskipTests package",
  "mkdir -p dist",
  "cp target/payment-service-*.jar dist/",
]
deploy = {
  teardown = "docker compose stop payment-service",
  run = "docker compose up -d --build payment-service",
  needs = ["build"],
  host = true,
}

[auth-service]
build = {
  run = "docker compose build auth-service",
  host = true,
}
deploy = {
  teardown = "docker compose stop auth-service",
  run = "docker compose up -d auth-service",
  needs = ["build"],
  host = true,
}
```

## Quick start

```bash
mvn -B package
java -jar target/rodeo-0.1.0-SNAPSHOT.jar --rodeo.config=/etc/rodeo/config.toml
```
