# UC07 — AI description draft and review

## Flow

The admin selects and saves a POI, optionally enters verified editorial notes, and requests a suggestion. The backend sends only the POI name, current source description, and notes to Gemini 2.5 Flash. The response is saved in `ai_description_draft` as `DRAFT`; it does not modify the POI. The admin can edit and save the draft, accept it (which updates the POI and starts the existing content pipeline), or discard it. The latest attempt is retained with its status and provider error.

Gemini output is requested in Vietnamese at 200–300 words and checked before it is accepted as a draft. Calls have a 30-second timeout. Quota, key, network, provider and output-validation errors are saved as `FAILED`; the existing POI remains untouched and the admin may retry or edit the POI manually.

## API

- `POST /api/admin/pois/{poiId}/ai-description` — generate and persist a draft; JSON body `{ "notes": "..." }`.
- `GET /api/admin/pois/{poiId}/ai-description/latest` — retrieve the most recent attempt.
- `PUT /api/admin/pois/{poiId}/ai-description/drafts/{draftId}` — edit a generated draft.
- `POST /api/admin/pois/{poiId}/ai-description/drafts/{draftId}/accept` — apply draft text to the POI and run the content pipeline.
- `POST /api/admin/pois/{poiId}/ai-description/drafts/{draftId}/discard` — discard draft without changing the POI.

Migration `V13__ai_description_drafts.sql` creates durable draft history. V12 is intentionally untouched.

## Local configuration (PowerShell)

Set the key in the same terminal used to start the backend. Do not put it in frontend variables or commit it:

```powershell
$env:GEMINI_API_KEY = "YOUR_KEY_FROM_GOOGLE_AI_STUDIO"
$env:GEMINI_MODEL = "gemini-2.5-flash"
.\mvnw.cmd spring-boot:run
```

If the backend terminal is closed, set the variables again in its replacement terminal. An absent/invalid key is reported in the saved failed draft; it does not prevent the backend from starting.
