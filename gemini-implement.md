# CampusLink KH Implementation & Testing Guide

This document provides a guide for testing the implemented features of the CampusLink KH project.

## Project Summary
- **Architecture**: Modular Monolith
- **Framework**: Spring Boot 3
- **Security**: JWT (Access + Refresh Tokens)
- **Database**: PostgreSQL with Liquibase migrations

## Prerequisites
1. **PostgreSQL**: Ensure you have a running PostgreSQL instance.
2. **Database Config**: Check `src/main/resources/application.yaml` for database credentials.
3. **Postman**: Download and install Postman.

## How to Test

### 1. Database Setup
The project uses **Liquibase**. When you start the application, it will automatically run the migrations located in `src/main/resources/db/changelog`.
- **Changelog Master**: `db.changelog-master.yaml`
- **Tables created**: `users`, `refresh_tokens`, and Liquibase internal tables (`databasechangelog`, `databasechangeloglock`).

### 2. Run the Application
```bash
./gradlew bootRun
```

### 3. API Testing with Postman
I have created a Postman collection file: `CampusLink_KH.postman_collection.json` in the root directory.

#### Steps to Import and Use:
1. Open Postman and click **Import**.
2. Select the `CampusLink_KH.postman_collection.json` file.
3. The collection will appear as "CampusLink KH API".
4. **Environment Variables**:
   - `baseUrl`: Default is `http://localhost:8081`.
   - `accessToken`: Automatically populated after a successful Login.
   - `refreshToken`: Automatically populated after a successful Login.

#### Test Scenarios:

| Feature | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **Registration** | `POST` | `/api/v1/auth/register` | Create a new user account. |
| **Login** | `POST` | `/api/v1/auth/login` | Authenticate and get tokens. Stores tokens in variables. |
| **Refresh Token**| `POST` | `/api/v1/auth/refresh` | Use the refresh token to get a new access token. |
| **Get Profile** | `GET` | `/api/v1/users/me` | Retrieve profile of the logged-in user (Requires Bearer Token). |

### 4. Security Flow
1. **Register** a user first.
2. **Login** with the same credentials. The "Tests" script in Postman will automatically save the `accessToken` and `refreshToken` to the collection variables.
3. Use **Get My Profile**. It is configured to use the `{{accessToken}}` variable as a Bearer token.
4. If the access token expires, use **Refresh Token** to obtain a new one.

### 5. Package Structure
- `shared`: Contains security, exception handling, and common utilities.
- `infrastructure`: Contains JPA and Liquibase configurations.
- `modules`: Contains business modules (Auth, User, Chat, etc.).

## Troubleshooting
- **401 Unauthorized**: Ensure you have logged in and the `accessToken` is valid.
- **Database Errors**: Ensure PostgreSQL is running and the database `campuslink` exists.
- **Liquibase Errors**: Check `databasechangelog` table if migrations are stuck.
