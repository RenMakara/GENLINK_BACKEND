# CampusLink KH

CampusLink KH is a multi-service platform for campus operations. This repository hosts the backend services, shared components, and infrastructure tooling that power the platform.

## What is in this repository
- Microservices for account, customer, and pipeline domains
- Spring Cloud services (gateway, Eureka, BFFs, IAM, config server)
- Shared libraries under `common/`
- Centralized configuration in `config-repo/`
- Deployment assets in `deployment/`

## Architecture (high level)
- Microservices with Spring Boot
- Service discovery and gateway via Spring Cloud
- Centralized configuration via a config server backed by `config-repo/`
- Supporting infrastructure via Docker Compose in `deployment/`

## Getting started (local)
1. Review service-specific `build.gradle` files for requirements.
2. Start supporting infrastructure from `deployment/` if needed (databases, Kafka, Redis).
3. Run the services you need from their respective folders.

## Configuration
- Shared and service-specific configs live in `config-repo/`.
- Environments are separated (e.g., `dev`, `qa`, `prod`) by YAML files.

## Deployment
- Docker Compose files for local infrastructure are under `deployment/`.
- Each service can be built and deployed independently.

## Contributing
- Keep changes focused and well-documented.
- Update or add tests where applicable.

## License
- Add your license information here.

## Contact
- Add project contact or maintainer info here.

