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
- Write: `suspend fun setX(value)` — callers must invoke from a coroutine scope

**Encrypted fields** (EncryptedSharedPreferences-backed, unchanged):
- Read: `val apiUrl: StateFlow<String>`, `val apiKey: StateFlow<String>`, `val apiModel: StateFlow<String>`
- Write: `fun setApiUrl(url: String)`, `fun setApiKey(key: String)`, `fun setApiModel(model: String)` (synchronous, `commit()`)

**New field:**
- `val onboardingCompleted: Flow<Boolean>` + `suspend fun setOnboardingCompleted(value: Boolean)`

### Caller Adaptation Patterns

**Composables with local mutable state** (`RemoteModelSection`, `LocalModelSection`):
Currently initialize local `mutableStateOf` from `appSettings.*.value`. With `Flow<T>`, switch to `collectAsState(initial = default)` for initial value. The two-way binding pattern (local state for immediate UI feedback + write-through to AppSettings) stays the same, but initialization uses `collectAsState()` instead of `.value`.

```kotlin
// Before:
var localPrompt by remember { mutableStateOf(appSettings.localPrompt.value) }

// After:
val savedPrompt by appSettings.localPrompt.collectAsState(initial = "")
var localPrompt by remember { mutableStateOf(savedPrompt) }
LaunchedEffect(savedPrompt) { localPrompt = savedPrompt }
```

Note: `LaunchedEffect` is needed because `remember { mutableStateOf(...) }` captures the initial value only once. Without it, `localPrompt` stays at the default empty string until the user types.

**Suspend setters from non-coroutine callbacks** (`onValueChange`, `onCheckedChange`):
Wrap in `scope.launch {}`. `RemoteModelSection` already has `rememberCoroutineScope()`. `LocalModelSection` needs to add one.

```kotlin
// Before:
onCheckedChange = { appSettings.setUseRemoteServer(it) }

// After:
onCheckedChange = { scope.launch { appSettings.setUseRemoteServer(it) } }
```

**`ImageAnalyzer.analyze()`** (already `suspend`):
Use `.first()` to read DataStore-backed flows. Encrypted fields still use `.value`.

```kotlin
// Before:
val systemPrompt = appSettings.systemPrompt.value.ifBlank { ... }

// After:
val systemPrompt = appSettings.systemPrompt.first().ifBlank { ... }
```

**`isRemoteModelConfigured()`**:
Becomes `suspend fun` — reads `useRemoteServer.first()`. Encrypted fields (`apiUrl`, `apiModel`) still use `.value`. Only caller (`ImageAnalyzer.analyze()`) is already `suspend`.

**`MainActivity` start destination** (`onboardingCompleted`):
Use `collectAsState(initial = false)` in the composable. While first emission arrives, app shows History (the `false` default), then recomposes if onboarding wasn't completed. This matches existing behavior since onboarding-incomplete users see the onboarding route anyway.

### No Migration

App is pre-release. Old SharedPreferences files (`app_settings`, `honest_price_prefs`) are removed from code without migration logic. The `init` block's `migrateSensitiveField()` and system prompt cleanup code are also removed.

## Files Changed

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add `androidx.datastore:datastore-preferences` dependency |
| `data/AppSettings.kt` | Replace plain SharedPreferences with DataStore; add `onboardingCompleted`; remove `init` migration block; `isRemoteModelConfigured()` becomes `suspend`; expose `Flow<T>` for non-sensitive fields |
| `MainActivity.kt` | Read `onboardingCompleted` from `appSettings.onboardingCompleted.collectAsState()`; remove `honest_price_prefs` access |
| `ui/onboarding/OnboardingScreen.kt` | Call `scope.launch { appSettings.setOnboardingCompleted(true) }` instead of raw SharedPreferences |
| `ui/settings/RemoteModelSection.kt` | Only `useRemote` and `systemPrompt` switch to `collectAsState()` + `LaunchedEffect`; only `setUseRemoteServer` and `setSystemPrompt` wrap in `scope.launch {}`. Encrypted fields (`apiUrl`, `apiKey`, `apiModel`) and their setters stay unchanged. |
| `ui/settings/LocalModelSection.kt` | `localPrompt` switches to `collectAsState()` + `LaunchedEffect`; add `rememberCoroutineScope()`; `setLocalPrompt` wraps in `scope.launch {}` |
| `ui/settings/SettingsScreen.kt` | Unaffected — delegates to section composables |
| `ui/settings/LanguageSection.kt` | Unaffected — does not use `AppSettings` |
| `ui/settings/ExportSection.kt` | Unaffected — does not use `AppSettings` |
| `analyzer/ImageAnalyzer.kt` | Use `.first()` for DataStore-backed fields; `isRemoteModelConfigured()` call becomes `suspend` |
| `analyzer/ImageAnalyzerTest.kt` | Mock `Flow<T>` properties instead of `StateFlow<T>` for non-sensitive fields |

## Decisions

- **No encrypted DataStore**: EncryptedSharedPreferences stays for credentials — no official encrypted DataStore exists, and the current solution is battle-tested.
- **Single DataStore instance**: All 4 non-sensitive keys consolidated into one `app_preferences` DataStore.
- **No migration**: Pre-release app, no users to migrate. All migration code in `init` block removed.
- **`Flow<T>` over `StateFlow<T>`**: Callers adapt via `collectAsState()` or `.first()`. No `stateIn()` / `CoroutineScope` in `AppSettings`.
