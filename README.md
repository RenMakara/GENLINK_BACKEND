# CampusLink KH

CampusLink KH is a monolithic platform for campus operations. This repository contains the core Spring Boot application, shared modules, and infrastructure tooling that support the system.

## What is in this repository
- The main application code under `src/`
- Shared utilities under `common/`
- Centralized configuration in `config-repo/`
- Deployment assets in `deployment/`

## Architecture (high level)
- Monolithic Spring Boot application
- Centralized configuration via a config server backed by `config-repo/`
- Supporting infrastructure via Docker Compose in `deployment/`

## Getting started (local)
1. Review the root `build.gradle` for requirements.
2. Start supporting infrastructure from `deployment/` if needed (databases, Kafka, Redis).
3. Run the application from the project root.

## Configuration
- Shared and environment-specific configs live in `config-repo/`.
- Environments are separated (e.g., `dev`, `qa`, `prod`) by YAML files.

## Deployment
- Docker Compose files for local infrastructure are under `deployment/`.
- The application can be built and deployed as a single unit.

## Contributing
- Keep changes focused and well-documented.
- Update or add tests where applicable.

## License
- Add your license information here.

## Contact
- Add project contact or maintainer info here.
