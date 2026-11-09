# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-SNAPSHOT] - 2026-11-09

### Added
- GitHub and GitLab push webhook event handlers.
- Container-based execution stages declared in pipeline `rodeo.toml`.
- GitHub status reporter to update commit build state.
- Automated GitHub Actions workflows for building Docker images, publishing packages, and running documentation site.
- Comprehensive unit tests covering signature/token verifiers, webhook controllers, services, and response handlers.
- Continuous Integration (CI) GitHub Actions workflow for automated test runs and code coverage.
- Community health files including `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and issue/PR templates.
- Example pipeline and configuration templates (`rodeo-example.toml`, `config-example.toml`).
