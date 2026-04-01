# SharedPreferences to DataStore Migration

**Date:** 2026-04-01
**Status:** Approved

## Problem

Direct SharedPreferences access in composables and across the app. No type-safety, no async API, deprecated approach per Google guidelines.

## Design

### Two Storage Backends in `AppSettings`

1. **Preferences DataStore** (`app_preferences`) — non-sensitive settings:
   - `use_remote_server: Boolean` (default: `false`)
   - `system_prompt: String` (default: `""`)
   - `local_prompt: String` (default: `""`)
   - `onboarding_completed: Boolean` (default: `false`)

2. **EncryptedSharedPreferences** (`secure_settings`) — sensitive credentials, unchanged:
   - `api_url: String`
   - `api_key: String`
   - `api_model: String`

### Public API

**Non-sensitive fields** (DataStore-backed):
- Read: `val useRemoteServer: Flow<Boolean>`, `val systemPrompt: Flow<String>`, etc.
- Write: `suspend fun setUseRemoteServer(enabled: Boolean)`, etc.

**Encrypted fields** (EncryptedSharedPreferences-backed, unchanged):
- Read: `val apiUrl: StateFlow<String>`, `val apiKey: StateFlow<String>`, `val apiModel: StateFlow<String>`
- Write: `fun setApiUrl(url: String)`, `fun setApiKey(key: String)`, `fun setApiModel(model: String)` (synchronous, `commit()`)

**New field:**
- `val onboardingCompleted: Flow<Boolean>` + `suspend fun setOnboardingCompleted(value: Boolean)`

### No Migration

App is pre-release. Old SharedPreferences files (`app_settings`, `honest_price_prefs`) are removed from code without migration logic.

## Files Changed

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add `androidx.datastore:datastore-preferences` dependency |
| `data/AppSettings.kt` | Replace plain SharedPreferences with DataStore; add `onboardingCompleted`; expose `Flow<T>` for non-sensitive fields |
| `MainActivity.kt` | Read `onboardingCompleted` from `AppSettings` flow; remove `honest_price_prefs` access; pass `AppSettings` where needed |
| `ui/onboarding/OnboardingScreen.kt` | Call `appSettings.setOnboardingCompleted(true)` instead of raw SharedPreferences |
| `ui/settings/SettingsScreen.kt` | Adapt to `Flow<T>` with `collectAsState()` |
| `ui/settings/RemoteModelSection.kt` | Adapt to `Flow<T>` with `collectAsState()` |
| `ui/settings/LocalModelSection.kt` | Adapt to `Flow<T>` with `collectAsState()` |
| `analyzer/ImageAnalyzer.kt` | Adapt to `Flow<T>` (use `.first()` or collect) |
| `analyzer/ImageAnalyzerTest.kt` | Adapt test mocks for `Flow<T>` API |

## Decisions

- **No encrypted DataStore**: EncryptedSharedPreferences stays for credentials — no official encrypted DataStore exists, and the current solution is battle-tested.
- **Single DataStore instance**: All 4 non-sensitive keys consolidated into one `app_preferences` DataStore.
- **No migration**: Pre-release app, no users to migrate.
