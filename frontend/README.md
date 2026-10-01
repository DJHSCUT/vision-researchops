# Vision ResearchOps frontend

Vue 3 + Vite + Vue Router + Axios + Element Plus. JavaScript only; no Pinia or mock data.

## Start locally

1. Start MySQL and use the existing `docs/sql/schema.sql` if the database has not been initialized. Do not rerun that script against an existing database.
2. In the backend terminal, set `DB_PASSWORD` to your local MySQL password, then run from the repository root:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Alternatively, set `DB_PASSWORD` in your IDE run configuration and start `VisionResearchopsApplication`. The backend uses port 8080 by default.

3. In a separate terminal:

   ```powershell
   cd C:\Documents\Projects\vision-researchops\frontend
   npm install
   npm run dev
   ```

   If npm is missing from that terminal's PATH, use `& 'C:\Program Files\nodejs\npm.cmd' install` and `& 'C:\Program Files\nodejs\npm.cmd' run dev`.

4. Open http://127.0.0.1:5173/projects. Port 5173 is strict so the address will not silently change if the port is occupied.

```powershell
npm run build
```

`dist/` is the production bundle. Vite's development proxy forwards `/api` to `http://localhost:8080`. A production host must also forward `/api` to the backend and fall back to `index.html` for Vue Router history URLs. Vite preview is only for inspecting the built frontend; it is not a production deployment configuration.

## Backend contract, verified against source

| Method | Path | Success | Body |
| --- | --- | --- | --- |
| GET | `/api/projects` | 200 | `{ code: 200, message: "success", data: Project[] }` |
| GET | `/api/projects/{id}` | 200 | `{ code: 200, message: "success", data: Project }` |
| POST | `/api/projects` | 201 | `{ code: 201, message: "项目创建成功", data: Project }` |
| PATCH | `/api/projects/{id}` | 200 | `{ code: 200, message: "success", data: Project }` |
| DELETE | `/api/projects/{id}` | 204 | No response body |

`Project` contains `id`, `name`, `description`, `status`, `createdAt`, `updatedAt`.

- Create accepts `name` (nonblank, up to 100 characters) and `description` (optional, up to 500 characters). The backend assigns `ACTIVE`.
- Edit accepts optional `name`, `description`, `status`. The form sends only changed fields and never sends an empty object. A missing or null description is normalized to an empty form value; clearing an existing description sends `""`, since the backend treats `null` as no change.
- The backend has no status enum or validation. The UI offers `ACTIVE` and states seen in real API records; it does not invent additional states. On the detail page, the options are `ACTIVE` plus the current project's actual state.
- Business errors and form validation return HTTP 400/404 with `{ code, message, data: null }`. The interceptor prioritizes the server message, with fallback handling for 500, proxy failures, timeout, malformed responses and network errors.
- The existing exception handler does not normalize malformed JSON, invalid path ID types, or unexpected server errors. The frontend handles HTTP failures regardless of whether they use the unified envelope.
- Date values are Java `LocalDateTime`: display the supplied wall-clock time without assigning a timezone.
- Search and overview counts are derived only from the real fetched project list. There is no pagination endpoint.

## Source map

```text
frontend/
├── .gitignore
├── README.md
├── index.html
├── package.json
├── package-lock.json
├── vite.config.js
└── src/
    ├── App.vue
    ├── main.js
    ├── api/project.js
    ├── assets/main.css
    ├── components/
    │   ├── ProjectDialog.vue
    │   └── StatusBadge.vue
    ├── layouts/MainLayout.vue
    ├── router/index.js
    ├── utils/
    │   ├── project.js
    │   └── request.js
    └── views/
        ├── ProjectsView.vue
        ├── ProjectDetailView.vue
        └── NotFoundView.vue
```

- `main.js` / `App.vue`: initialize Vue, router, Element Plus and the shared layout.
- `MainLayout.vue`: product header, Projects navigation and a constrained content area.
- `ProjectsView.vue`: fetch real projects, display overview/cards, search, refresh, delete confirmation and form entry points. Edit fetches the current record before opening the form.
- `ProjectDetailView.vue`: fetch by route ID, render Overview/Metadata and show dedicated failure/404 states. Route changes refetch data.
- `ProjectDialog.vue`: create/edit validation, changed-field PATCH submission and success handling; preserves form input when saving fails.
- `StatusBadge.vue`: shared status appearance.
- `project.js`: readable dates, observed status choices and PATCH field comparison.
- `request.js`: Axios instance, envelope decoding, 204 handling and centralized error messages.
- `api/project.js`: the five project API operations.
- `router/index.js` / `NotFoundView.vue`: list/detail routes, root redirect and unknown-page handling.
- `main.css`: restrained typography, spacing, card styles, responsive layout and Element Plus overrides.
- `vite.config.js`: Vue plugin, development server and same-origin API proxy.
- `package.json` / `package-lock.json`: dependencies, scripts and reproducible installed versions.

## Manual acceptance

- List: compare real backend records, verify search, refresh and timestamps. Check empty state only when the database really has no projects.
- Create: validate required name and length limits, save, verify success message and refreshed list.
- Detail: open a card, verify all six project fields, then refresh the browser directly at its URL.
- Edit: change one field, verify the Network request contains only that field; clear description; try saving without changes.
- Status: verify only `ACTIVE` and real observed states appear in the select.
- Delete: cancel once; confirm deletion of a disposable project; verify HTTP 204 and refreshed list.
- Parameter errors: blank name is rejected locally; use DevTools or an API client to send an invalid request and verify the backend 400 message. Temporarily bypassing form validation is not part of the application.
- 404: open `/projects/9223372036854775807` provided that ID does not exist, and verify the backend message. Also test a stale project edited/deleted by another client.
- Availability: stop the backend, refresh, verify error state and feedback, restart it and retry.
- Layout: inspect at 1920×1080 and a normal laptop width; check a long project name and description.

No backend source or API changes are required. No login or future research modules are implemented.

## Verification in this session

- `npm install` completed: 85 packages added, audit reported 0 vulnerabilities.
- Direct installed packages: Vue 3.5.43, Vue Router 4.6.4, Axios 1.20.0, Element Plus 2.14.7, Vite 7.3.6, @vitejs/plugin-vue 6.0.9. The lockfile records all transitive packages.
- `npm run build` passed. Only used Element Plus components are registered, and view routes are lazy loaded.
- `npm run dev` started successfully at `http://127.0.0.1:5173/`.
- Browser verification confirmed the creation dialog, required-name validation and backend-unavailable feedback. The available layout was inspected at 1920×1080 and 1366×768, with no captured browser console errors or warnings. Project cards and successful detail content still require real backend records for visual acceptance. PATCH comparison checks passed for unchanged fields, a name-only change, clearing a description, a status-only change, observed states and date formatting.
- `http://localhost:8080/api/projects` refused connection, and the current execution process has no `DB_PASSWORD`. Therefore successful live CRUD, backend 400/404 messages and database persistence require manual verification after starting the backend. No mock data was used.
- The browser's `/api` request used Vite's proxy. No CORS error was observed; full live API verification remains pending.
- Source review found DELETE 204 has no envelope, status has no enum, and unexpected exceptions are not normalized by `GlobalExceptionHandler`. The frontend accommodates these existing semantics without changing the backend.
- No backend files were modified, and no Git commit was executed.
