# Task 3A Report: Authentication and onboarding backend

## Scope delivered

- Added PostgreSQL/Flyway V1 persistence for `finmate_user` and `finmate_refresh`.
- Implemented signup, login, refresh, logout, `GET /me`, onboarding save, and preferences save under `/api/v1`.
- Added BCrypt password storage, normalized unique email addresses, 15-minute signed JWT access tokens, and 30-day opaque refresh tokens persisted only as SHA-256 hashes.
- Added rotating/revoking HttpOnly `finmate_refresh` cookies with configurable Secure behavior, constrained credentialed CORS, default preferences/privacy, and RFC 7807 responses for validation, duplicate email, and invalid credentials.

## Changed files

- `src/main/java/com/gagastudio/finmate/FinmateApiApplication.java`
- `src/main/java/com/gagastudio/finmate/api/ProblemHandler.java`
- `src/main/java/com/gagastudio/finmate/auth/AuthController.java`
- `src/main/java/com/gagastudio/finmate/auth/AuthDtos.java`
- `src/main/java/com/gagastudio/finmate/auth/AuthService.java`
- `src/main/java/com/gagastudio/finmate/auth/DuplicateEmailException.java`
- `src/main/java/com/gagastudio/finmate/auth/EmailNormalizer.java`
- `src/main/java/com/gagastudio/finmate/auth/FinmateUser.java`
- `src/main/java/com/gagastudio/finmate/auth/FinmateUserRepository.java`
- `src/main/java/com/gagastudio/finmate/auth/InvalidCredentialsException.java`
- `src/main/java/com/gagastudio/finmate/auth/MeController.java`
- `src/main/java/com/gagastudio/finmate/auth/MeDtos.java`
- `src/main/java/com/gagastudio/finmate/auth/PasswordPolicy.java`
- `src/main/java/com/gagastudio/finmate/auth/RefreshToken.java`
- `src/main/java/com/gagastudio/finmate/auth/RefreshTokenHasher.java`
- `src/main/java/com/gagastudio/finmate/auth/RefreshTokenRepository.java`
- `src/main/java/com/gagastudio/finmate/config/FinmateProperties.java`
- `src/main/java/com/gagastudio/finmate/config/SecurityConfiguration.java`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V1__auth_and_onboarding.sql`
- `src/test/java/com/gagastudio/finmate/AuthOnboardingIntegrationTests.java`
- `src/test/java/com/gagastudio/finmate/auth/EmailNormalizerTest.java`
- `src/test/java/com/gagastudio/finmate/auth/PasswordPolicyTest.java`
- `src/test/java/com/gagastudio/finmate/auth/RefreshTokenHasherTest.java`

## Migration

`V1__auth_and_onboarding.sql` creates the user and refresh-token tables, a unique normalized email column, a unique refresh-token hash, expiry/revocation columns, and an active-token lookup index. The test suite applies this migration to PostgreSQL 16 through Testcontainers; no H2 is configured.

## TDD evidence

First behavior: email normalization.

1. RED: `./gradlew test --tests com.gagastudio.finmate.auth.EmailNormalizerTest` failed at test compilation because `EmailNormalizer` did not exist.
2. GREEN: after implementing `EmailNormalizer.normalize`, the same command completed `BUILD SUCCESSFUL`.

The password policy and refresh-token hashing/rotation utilities were then added through the same red-green cycle. Integration tests were added before their matching endpoint handlers: signup first, followed by duplicate signup, failed login, authenticated profile access, onboarding/preferences saves, refresh rotation, logout revocation, validation, and unauthenticated RFC 7807 behavior.

## Commands and exact results

- `./gradlew test --tests com.gagastudio.finmate.auth.EmailNormalizerTest` (RED): `compileTestJava FAILED`, `cannot find symbol EmailNormalizer`.
- `./gradlew test --tests com.gagastudio.finmate.auth.EmailNormalizerTest` (GREEN): `BUILD SUCCESSFUL`.
- `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests` (RED before remaining handlers): 8 tests completed, 6 failed.
- `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests` (GREEN): `BUILD SUCCESSFUL`.
- `./gradlew test` (final): `BUILD SUCCESSFUL in 6s`.

## Review fixes

- Removed the development JWT fallback. `FINMATE_JWT_SECRET` now binds as an empty value when absent and `FinmateProperties` rejects values shorter than 32 UTF-8 bytes, preventing application startup. Test-only properties provide an explicit strong secret and allowed origin.
- Added a shared refresh-cookie factory and tests for HttpOnly, SameSite=Lax, path, default non-Secure behavior, and configured Secure behavior.
- Made refresh-token consumption pessimistically lock the active database row, with a concurrent MockMvc test proving that exactly one simultaneous refresh succeeds.
- Forced signup persistence to flush inside the transaction and translate a unique-email constraint race to `DUPLICATE_EMAIL`; a concurrent MockMvc test covers the race.
- Centralized RFC 7807 generation so validation, malformed JSON, duplicate email, invalid login/refresh, and invalid bearer-token responses include `type`, `title`, `status`, `detail`, `instance`, `code`, and `traceId`; validation responses also include `fieldErrors`.
- Added forged, expired, and malformed JWT tests; added a rejected cross-origin preflight test.
- Removed the redundant active refresh-token index because the unique `token_hash` constraint already supplies the lookup index.

### Review verification

- `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests --tests com.gagastudio.finmate.auth.RefreshCookieFactoryTest --tests com.gagastudio.finmate.config.FinmatePropertiesTest`: `BUILD SUCCESSFUL in 7s`.
- `./gradlew test --rerun-tasks`: `BUILD SUCCESSFUL in 10s`.

## Final Task 3A fixes

- Password policy now measures the UTF-8 encoded byte length from 12 through 72 before BCrypt instead of using Java character count.
- Added a unit boundary test showing four Korean characters are 12 bytes and accepted, while 30 Korean characters are 90 bytes and rejected.
- Added a signup integration test proving the 90-byte password returns an RFC 7807 `VALIDATION_FAILED` response instead of reaching BCrypt and raising a server error.
- Added a positive CORS preflight test proving the configured origin is echoed exactly and `Access-Control-Allow-Credentials` is `true`.

### Final verification

- RED unit: `./gradlew test --tests com.gagastudio.finmate.auth.PasswordPolicyTest.measuresPasswordLengthInUtf8Bytes`: 1 test completed, 1 failed.
- RED integration: `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests.signupRejectsPasswordLongerThanSeventyTwoUtf8BytesAsValidationProblem`: failed with BCrypt `IllegalArgumentException` through `ServletException`.
- Focused GREEN: `./gradlew test --tests com.gagastudio.finmate.auth.PasswordPolicyTest --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests.signupRejectsPasswordLongerThanSeventyTwoUtf8BytesAsValidationProblem --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests.allowsCorsPreflightFromConfiguredOriginWithCredentials`: `BUILD SUCCESSFUL in 6s`.
- Full GREEN: `./gradlew test --rerun-tasks`: `BUILD SUCCESSFUL in 9s`.

## Final login byte guard

- Login password validation now measures 1 through 72 UTF-8 bytes before BCrypt comparison instead of applying a Java character-count maximum.
- Added an integration regression that signs up and successfully logs in with 24 Korean characters (72 bytes), then proves 25 Korean characters (the same 72-byte prefix plus 3 bytes) returns RFC 7807 `VALIDATION_FAILED`.

### Login guard verification

- RED: `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests.loginRejectsPasswordLongerThanSeventyTwoUtf8BytesBeforeBcryptComparison`: the 75-byte password incorrectly returned 200.
- Focused GREEN: `./gradlew test --tests com.gagastudio.finmate.AuthOnboardingIntegrationTests.loginRejectsPasswordLongerThanSeventyTwoUtf8BytesBeforeBcryptComparison --tests com.gagastudio.finmate.auth.PasswordPolicyTest`: `BUILD SUCCESSFUL in 6s`.
- Full GREEN: `./gradlew test --rerun-tasks`: `BUILD SUCCESSFUL in 12s`.
