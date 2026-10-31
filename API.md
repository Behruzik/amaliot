# GEADEZIST backend API

The repository started as an empty Spring Boot 4 / Java 17 application. This API uses JSON, UTC ISO-8601 timestamps, bearer JWTs, and zero-based page numbers. Swagger UI is available at `/swagger-ui.html`, with the OpenAPI JSON at `/v3/api-docs`, when the application is running.

Swagger documents the endpoint groups, request/response DTOs, and JWT bearer authentication. Public registration/login/category endpoints work without authorization. For protected endpoints, sign in through `POST /api/auth/login`, copy the returned token, select **Authorize**, and paste the token without the `Bearer ` prefix. Access is still enforced by Spring Security and role checks; Swagger does not grant privileges.

## Setup

- Copy `.env.example` to `.env` for local settings. Spring loads `.env` at startup; `.env` is ignored by Git, while the template contains no credentials or personal data. The default datasource is a file-backed H2 database in ./data; data survives restarts. The explicit demo profile uses disposable in-memory H2 with synthetic data. Flyway automatically applies all migrations in `src/main/resources/db/migration`.
- Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to point the app at PostgreSQL in deployment.
- Set `JWT_SECRET_BASE64` to a random base64 encoded secret of at least 32 bytes, and set `JWT_TOKEN_MINUTES` as needed. Keep the key in `.env` or the deployment secret manager, never in source control.
- On first startup, the data loader requires `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`, and `SUPER_ADMIN_PHONE`. Supply them through `.env` or the deployment secret manager. Change the initial password after signing in.
- Set `UPLOAD_DIR` to persistent storage in deployment. Avatars and question images accept PNG, JPEG, and WebP content up to 3 MB and are stored as files, not database blobs.
- Password recovery returns `503 PASSWORD_RESET_PROVIDER_NOT_CONFIGURED`; no SMS or email provider exists in this repository.

## Authentication and profile

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | Create a user with unique Gmail and phone values; nickname is optional, password must be at least 6 characters, offer acceptance is required |
| POST | `/api/auth/login` | Public | Sign in with email (`email` field; old `phone` alias is accepted); five failed attempts lock the identifier for 15 minutes in this app instance |
| POST | `/api/auth/logout` | User | Revoke the presented bearer token |
| POST | `/api/auth/password/change` | User | Change password and invalidate all existing tokens for that account |
| POST | `/api/auth/password/forgot` | Public | Explicitly reports the missing reset provider |
| GET/PATCH | `/api/profile` | User | Read or update only the caller's profile; PATCH accepts `professionId` from `GET /api/professions` |
| POST | `/api/profile/avatar` | User | Upload a validated avatar image |
| GET | `/api/profile/dashboard` | User | Account creation date, active duration, exam totals, directions, and certificates |
| GET | `/api/recommendations` | User | Profession-matched exam readiness and active question count |

New user emails are trimmed, lowercased, required to end in `@gmail.com`, and unique in the database. Nicknames are optional. Passwords must contain at least 6 characters. The application stores a versioned PBKDF2-HMAC-SHA256 verifier (600,000 iterations, 256 bits) with a separate random salt for each account; the original password is never stored and users continue to log in with the password they chose. A database unique index prevents duplicate stored verifiers. Existing BCrypt and legacy 18-character PBKDF2 passwords are checked and upgraded automatically after the next successful login. Roles are `USER`, `STAFF`, `ADMIN`, and `SUPER_ADMIN`. Public registration always creates `USER`. Only `SUPER_ADMIN` can assign `ADMIN`; an `ADMIN` can assign `USER` or `STAFF`. The Super Admin role is reserved for the startup data loader. Existing phone-based accounts remain supported for login.
Registration also accepts required `phone`, and optional `nickname`, `gender` (`FEMALE`, `MALE`, or `OTHER`), first/last name, and `confirmPassword` fields. Phone is normalized and unique. When password confirmation is sent, it must match the password. Profile updates can change the phone and nickname; location selections are validated against the managed location catalog. The full profession selector is `GET /api/professions`; `PATCH /api/profile` accepts its selected ID as `professionId`. The initial migration seeds 10 trade professions: electrician, plumber, welder, carpenter, bricklayer, plasterer, painter, tiler, roofer, and drywall installer. Once selected, the authenticated `GET /api/categories` list shows only that profession and exam start rejects a different category. Staff must add at least 40 active questions per profession before its exam can be taken. Complete first/last name, birth date, region, district, address, and offer acceptance are also required before starting an exam; the certificate uses the first/last name or nickname. Phone checking currently validates format and uniqueness; SMS OTP delivery is not configured.

## Categories and question bank

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/professions` | Public | List all active professions for the profile selector |
| GET | `/api/categories` | Public / User-filtered | List active test categories; an authenticated user with a selected profession sees only that profession |
| GET/POST | `/api/admin/categories` | Staff / Admin / Super Admin | List or create categories |
| PUT/DELETE | `/api/admin/categories/{id}` | Staff / Admin / Super Admin | Update or deactivate a category |
| GET | `/api/admin/questions` | Staff / Admin / Super Admin | Filter and page questions |
| GET/POST | `/api/admin/questions/{id}` / `/api/admin/questions` | Staff / Admin / Super Admin | Read or create a question |
| PUT/DELETE | `/api/admin/questions/{id}` | Staff / Admin / Super Admin | Update or deactivate a question |
| POST | `/api/admin/questions/{id}/image` | Staff / Admin / Super Admin | Upload a question image |

Question answer types are `SINGLE`, `MULTIPLE`, and `NUMERIC`. Numeric answers are compared by absolute difference against the configured tolerance. Question and option correctness fields are only returned by the staff/admin question endpoints.

## Test attempts and results

Every new attempt contains exactly 40 randomly selected active questions from its chosen category. The category must have at least 40 active questions; supplementary questions from other directions are not used. A non-expired attempt with at least 35 correct answers automatically receives a certificate for that category. Expired attempts do not receive certificates. If an administrator later cancels a passing attempt, its certificate is marked invalid.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/tests/categories/{categoryId}/attempts` | User | Start an attempt after profile, category, question bank, and retake checks |
| GET | `/api/tests/attempts/{attemptId}` | Owner | Read the attempt snapshot and saved answers |
| GET | `/api/tests/attempts/saved` | User | List unfinished attempts that can be resumed |
| POST | `/api/tests/attempts/{attemptId}/save` | Owner | Mark an unfinished attempt as saved for later; answers are already persisted automatically |
| POST | `/api/tests/attempts/{attemptId}/resume` | Owner | Reopen an unfinished attempt with its saved answers |
| PUT | `/api/tests/attempts/{attemptId}/questions/{questionId}/answer` | Owner | Save or replace one answer |
| POST | `/api/tests/attempts/{attemptId}/finish` | Owner | Finish and calculate results; repeat calls are idempotent |
| GET | `/api/tests/results` | User | Page the caller's completed results |
| GET | `/api/tests/results/{attemptId}` | Owner | Read one completed result |
| GET | `/api/certificates` | User | List the caller's valid certificates |
| GET | `/api/certificates/{certificateNumber}` | Owner | Read a certificate issued to the caller |
| GET | `/api/certificates/verify/{certificateNumber}` | Public | Verify a certificate and see whether it is still valid |
| GET | `/api/admin/attempts` | Staff / Admin | Search/filter attempts by person, category, and status |
| GET | `/api/admin/attempts/{id}` | Staff / Admin | Read result and answer-key details |
| POST | `/api/admin/attempts/{id}/approve` | Admin / Super Admin | Approve a completed pending result |
| POST | `/api/admin/attempts/{id}/cancel` | Admin / Super Admin | Cancel a completed pending result |
| POST | `/api/admin/retake-grants` | Admin / Super Admin | Grant a one-use retake for a user/category |
| GET | `/api/admin/archive` | Staff / Admin | List archived results, with the same search and pagination filters |
| POST | `/api/admin/attempts/{id}/archive` | Admin | Archive a completed attempt without changing its status |
| POST | `/api/admin/attempts/{id}/restore` | Admin | Restore an archived attempt to the regular results list |

`GET /api/admin/attempts` returns active (not archived) results by default; pass `archived=true` to query the archive through that endpoint. Archiving is stored separately as `archivedAt`, so it does not replace `PENDING`, `APPROVED`, `CANCELLED`, or `EXPIRED`. Archive/restore actions are audited.

User save/resume is separate from the administrator's archive of completed results. Answers are committed on every answer request and appear in the saved-attempt list. Saving does not pause or extend the exam deadline; a timed-out attempt is finalized as `EXPIRED` and cannot be resumed.

## Support

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/support/requests` | User | Submit `PHONE_CALL`, `APPLICATION`, or `FEEDBACK` with a subject and message |
| GET | `/api/support/requests/contact` | Public | Read the Support phone number and contact methods |
| GET | `/api/support/requests/mine` | User | Read the caller's support requests |

`GET /api/recommendations` requires a selected profession. It returns that profession's test with active-question count and readiness (40 questions required).
| GET | `/api/support/requests?search={nickname}&status=OPEN` | Staff / Admin / Super Admin | Search by nickname, email, or phone and optionally filter by status |
| PATCH | `/api/support/requests/{id}` | Staff / Admin / Super Admin | Update status (`OPEN`, `IN_PROGRESS`, `CLOSED`) and staff reply |

Set `SUPER_ADMIN_PHONE` in `.env` or the deployment secret manager to configure the seeded Super Admin phone.

The server snapshots questions, options, and grading keys when an attempt starts. Keys are never included in user test responses. The server uses the persisted deadline and saved answers to grade; any request after the deadline finishes the attempt as `EXPIRED`, and a scheduled scan closes overdue attempts every 15 seconds by default (`TEST_EXPIRY_SCAN_MS` controls the interval). Normal submission becomes `PENDING` until Admin/Super Admin review. Only completed pending results can be approved or cancelled. Cancelled attempts do not block another attempt; other completed attempts use the category's retake delay unless an Admin/Super Admin grant is available.

## Administration and operating notes

- `GET /api/admin/dashboard` calculates counts from the database.
- `GET /api/admin/users` supports `search`, `role`, `active`, `page`, and `size`. Only `ADMIN` and `SUPER_ADMIN` can create, update, or deactivate user/staff accounts; only `SUPER_ADMIN` can assign `ADMIN`. Staff is the primary user-support role and can search users/attempts, manage exam categories/questions, and handle support requests. Staff cannot change account roles, approve/cancel attempts, or grant retakes.
- The repository has no official geography dataset. Administrators maintain the location catalog; profiles keep the selected region and district names so existing profile DTOs remain compatible.
- The location catalog and managed content tables start empty. An administrator must add region/district choices and page text; the project does not seed or copy personal or video account data.
- Admin-managed page text uses `ABOUT_WE` and `LOGIN_PAGE` content types. No frontend source is present in this repository, so these endpoints provide the data for the video’s pages without changing the UI.
- `GET /api/locations/regions`, `GET /api/locations/regions/{regionId}/districts`, and `GET /api/locations/districts?regionId=...` provide active location options. Admins manage them through `/api/admin/locations/regions` and `/api/admin/locations/districts`; deletes deactivate records so existing profiles remain valid.
- `GET /api/content/{type}` returns the public text for `ABOUT_WE` or `LOGIN_PAGE`. Admins list and update it through `GET /api/admin/content` and `PUT /api/admin/content/{type}`; `DELETE /api/admin/content/{type}` clears a slot. Changes are written to `audit_logs`.
- Administrative changes to users, categories, questions, location catalogs, page content, retake grants, attempt decisions, and archive state are written to `audit_logs`. Request logging must not include authorization headers, passwords, or profile bodies.
- Login throttling is in process memory. Deployments with multiple instances should also rate-limit `/api/auth/login` at a shared gateway.
- The API's first version uses local filesystem image storage; configure a persistent volume or replace the storage adapter for multi-instance deployments.


## PDF export and verification additions

- `GET /api/tests/results/{attemptId}/pdf`: completed result PDF for its owner.
- `GET /api/certificates/{certificateNumber}/pdf`: certificate PDF for its owner; generated numbers begin with `GEA-`. Revoked certificates are clearly marked.
- Optional `download=true` changes Content-Disposition from inline to attachment.
- PDF responses use `application/pdf`, private no-store caching, embedded Cyrillic/Uzbek fonts. Errors use the standard JSON ApiError, including when Accept is application/pdf.
- Authentication required: 401 without JWT; 404 for another user's document; 409 for an unfinished active attempt. Overdue attempts are completed before rendering their result.
- Login, registration and password operations have a per-process client-IP limit, default 20 requests per 60 seconds. A 429 reply includes Retry-After. The existing per-account failed-login guard is preserved.
- Tests use an isolated H2 database and never load .env. Production requires a configured JWT secret.
- See [SWAGGER_GUIDE_RU.md](docs/SWAGGER_GUIDE_RU.md) for a complete walkthrough including demo credentials, authorization and PDF download.
