You are a Senior Backend Security Engineer and Spring Boot Architect.

I want you to help me refactor and upgrade my authentication system for my project called "CampusLink KH" based on my new Version 2 project flow.

Project Context:
- Project Name: CampusLink KH
- Architecture: Modular Monolith Architecture
- Backend: Spring Boot 3 (Java 21)
- Security: Spring Security + JWT
- Database: PostgreSQL
- ORM: Spring Data JPA
- Authentication: JWT (RS256)
- Mobile App: Flutter (GetX)
- Token Storage: flutter_secure_storage
- Deployment: Google Cloud

Current Authentication Status:
I already implemented:

1. RS256 JWT signing using RSA private/public keys
2. BCrypt password hashing
3. Refresh token rotation
4. Refresh token persistence in database
5. Firebase Google Sign-In
6. JPA Auditing
7. Spring Security authentication

Current Problems / Risks:
1. CORS allows "*"
2. RSA keys are not secured properly
3. Firebase credentials rely on environment variables
4. Missing rate limiting
5. Missing security audit logs
6. No device tracking
7. No logout revocation flow

My NEW Authentication Requirements (Version 2):

Authentication Flow:
Register
→ Validate Input
→ Create User
→ Generate OTP
→ Verify OTP
→ Activate Account
→ Generate JWT Token
→ Save Secure Token
→ Navigate to Home

Login
→ Validate Credentials
→ Generate JWT Access Token
→ Generate Refresh Token
→ Secure Storage
→ Home

JWT Token Flow:
API Request
→ Attach JWT Access Token
→ Validate Token
→ Refresh if expired
→ Retry Request
→ Force Logout if refresh expired

Required Features:
1. Local Login (email/password)
2. Google Sign-In (Firebase)
3. OTP Verification (Register + Forgot Password)
4. Refresh Token Rotation
5. Logout with token revocation
6. Role-based system (USER, ADMIN)
7. Account Status:
   - PENDING
   - ACTIVE
   - BANNED
8. Secure RS256 JWT
9. BCrypt password hashing
10. Secure refresh token storage
11. Rate limiting for:
   - register
   - login
   - verify otp
   - forgot password
   - refresh token
12. Security audit logs
13. Secure CORS policy
14. Global exception handling
15. Production-ready security

Please redesign my authentication module using clean architecture and modular monolith principles.

I want you to generate:

1. Recommended package structure
2. Entity design
3. DTOs
4. Authentication flow architecture
5. Spring Security configuration
6. JWT implementation strategy
7. Refresh token strategy
8. OTP strategy
9. Security audit log strategy
10. Rate limiting implementation
11. API endpoint design
12. Database schema
13. Best practices for production
14. Security vulnerabilities I should fix
15. Migration strategy from my old auth system to Version 2

Important Constraints:
- Keep Modular Monolith architecture
- Do NOT overengineer into microservices
- Prioritize competition-ready delivery
- Must remain maintainable for a 5-member team
- Follow clean code principles
- Follow Spring Boot best practices
- Use Java 21
- Use Spring Boot 3
- Use PostgreSQL
- Prefer production-grade but practical implementation

Output everything in a practical step-by-step engineering approach with folder structure, code recommendations, and architecture diagrams where needed.

Also update my backend database versioning strategy.

I use Liquibase with SQL changelog format, not YAML/XML.

Please design a production-ready Liquibase SQL migration structure for my CampusLink KH authentication module.

Project Stack:
- Spring Boot 3
- Java 21
- PostgreSQL
- Modular Monolith Architecture
- Liquibase SQL formatted changelogs

Required tables:
1. users
2. refresh_tokens
3. otp_codes
4. security_audit_logs
5. user_profiles
6. user_settings

Please generate:

1. Recommended folder structure
2. master changelog SQL include structure
3. SQL formatted Liquibase changelogs using:
   --liquibase formatted sql
   --changeset author:id
   --rollback ...
4. PostgreSQL-compatible SQL
5. Primary keys
6. Foreign keys
7. Unique constraints
8. Indexes
9. Check constraints for enum-like fields
10. created_at and updated_at columns
11. Safe rollback strategy
12. Naming convention for future migrations
13. Best practices for Liquibase SQL in a Spring Boot modular monolith

Important:
- Use SQL format only
- Do not use YAML or XML changelog examples
- Avoid destructive migrations
- Make migrations idempotent where possible
- Keep structure maintainable for a 5-member team