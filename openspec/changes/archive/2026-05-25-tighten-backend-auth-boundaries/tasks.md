## 1. Root OpenSpec

- [x] 1.1 Add root `backend-auth-boundaries` capability spec.
- [x] 1.2 Add root change artifacts for `tighten-backend-auth-boundaries`.

## 2. Backend OpenSpec

- [x] 2.1 Add matching backend `backend-auth-boundaries` capability spec.
- [x] 2.2 Add matching backend change artifacts for `tighten-backend-auth-boundaries`.

## 3. Implementation

- [x] 3.1 Require administrator authorization for management, operational, and library-mutating routes.
- [x] 3.2 Require authenticated-user authorization for app source and app book entrypoints.
- [x] 3.3 Replace route-local `/api/auth/me` token parsing with the shared current-user helper.
- [x] 3.4 Preserve public read routes and existing user-scoped reader route behavior.

## 4. Verification

- [x] 4.1 Add and update route authorization tests.
- [x] 4.2 Run backend route tests.
- [x] 4.3 Run full backend tests.
