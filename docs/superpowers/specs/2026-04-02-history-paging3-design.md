# Paging 3 for HistoryScreen

## Problem

`getAllScansFlow()` loads all scan records into memory. With thousands of scans this causes memory pressure and slow initial load.

## Solution

Replace the full-list `Flow<List<Scan>>` with Paging 3 `PagingData<ScanListItem>` stream. Date group headers inserted via `insertSeparators()` as static (non-collapsible) separators.

## Changes by Layer

### Dependencies

Add to `libs.versions.toml`:
```toml
paging = "3.3.6"
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
```

Add to `app/build.gradle.kts`:
```kotlin
implementation(libs.androidx.paging.runtime)
implementation(libs.androidx.paging.compose)
```

### Model: `ScanListItem.kt`

New sealed interface in `ui/history/`:
```kotlin
sealed interface ScanListItem {
    data class ScanItem(val scan: Scan) : ScanListItem
    data class DateHeader(val dateKey: String) : ScanListItem
}
```

### ScanDao

New method (existing `getAllScansFlow()` stays):
```kotlin
@Query("SELECT * FROM scans WHERE status != 'PROCESSING' ORDER BY createdAt DESC")
fun getAllScansPaged(): PagingSource<Int, Scan>
```

### ScanRepository / ScanRepositoryImpl

New method — pass-through to DAO:
```kotlin
fun getAllScansPaged(): PagingSource<Int, Scan>
```

### HistoryViewModel

Replace `scans: StateFlow<List<Scan>?>` with:
```kotlin
val scansPaged: Flow<PagingData<ScanListItem>> = Pager(
    config = PagingConfig(pageSize = 30, prefetchDistance = 10),
    pagingSourceFactory = { repository.getAllScansPaged() }
).flow
    .map { pagingData ->
        pagingData.map { ScanListItem.ScanItem(it) }
    }
    .map { pagingData ->
        pagingData.insertSeparators { before, after ->
            val beforeDate = (before as? ScanListItem.ScanItem)?.scan?.dateKey()
            val afterDate = (after as? ScanListItem.ScanItem)?.scan?.dateKey()
            when {
                afterDate != null && beforeDate != afterDate -> ScanListItem.DateHeader(afterDate)
                else -> null
            }
        }
    }
    .cachedIn(viewModelScope)
```

Keep delete/export methods that use `getAllScansFlow()` unchanged.

### HistoryScreen

- Replace `val scans by viewModel.scans.collectAsState()` with `val lazyPagingItems = viewModel.scansPaged.collectAsLazyPagingItems()`
- Remove in-memory `groupBy` date logic
- Remove expand/collapse state (`expandedSections`, animation)
- LazyColumn uses `items(lazyPagingItems.itemCount)` with type check:
  - `DateHeader` renders static date section header
  - `ScanItem` renders existing scan card composable
- Add `loadState` handling: append loading indicator, error retry
- Loading skeleton shown when `lazyPagingItems.loadState.refresh is LoadState.Loading`
- Empty state shown when `lazyPagingItems.loadState.refresh is LoadState.NotLoading && lazyPagingItems.itemCount == 0`

## What Does NOT Change

- `getAllScansFlow()` in DAO/Repository (used by delete-all, export, etc.)
- Scan card visual design
- Delete, navigation, detail overlay logic
- Search/filter (future scope)

## Page Size

30 items per page with 10-item prefetch distance. Covers 2-3 screens of content.
