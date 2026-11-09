# Contributing to Rodeo

First off, thank you for taking the time to contribute! Contributions of all kinds are welcome—whether you are fixing bugs, improving documentation, or adding new features.

Please take a moment to review this document to make the contribution process easy and effective for everyone involved.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

## How Can I Contribute?

### Reporting Bugs

Before submitting a bug report, please check the existing issues to see if the problem has already been reported.

When filing a bug report, please include:
- A clear and descriptive title.
- Steps to reproduce the behavior.
- Expected vs. actual behavior.
- Any relevant configuration file details (with sensitive secrets redacted).
- Logs, error outputs, or screenshots if applicable.

### Suggesting Enhancements

We are always looking to improve Rodeo! If you have ideas for new features or enhancements:
- Open an issue describing the proposed feature.
- Explain why this feature would be useful to other users.
- Provide examples of how the feature would look or work.

### Submitting Pull Requests

1. **Fork the Repository**: Create your own fork of Rodeo and clone it locally.
2. **Create a Branch**: Create a descriptive branch name from `main` (e.g., `feature/add-oauth-support` or `bugfix/fix-token-validation`).
3. **Write Code and Tests**:
   - Ensure your code follows existing patterns and formatting.
   - Write comprehensive unit tests for any new behavior.
   - Run tests locally before pushing (`mvn clean verify`).
4. **Keep Commits Clean**: Write descriptive commit messages.
5. **Open a Pull Request**: Submit your pull request to the `main` branch of the upstream repository. Ensure the CI builds and tests pass.

## Development Setup

Rodeo is built with Java 21 and Spring Boot 4.

### Prerequisites

- Java Development Kit (JDK) 21 or newer
- Apache Maven 3.9+
- Docker (for running container-based integration/unit tests)

### Commands

- **Build project**: `mvn clean package`
- **Run tests**: `mvn clean verify`
- **Run application locally**: `java -jar target/rodeo-0.1.0-SNAPSHOT.jar --rodeo.config=path/to/config.toml`

Thank you for contributing to Rodeo!
