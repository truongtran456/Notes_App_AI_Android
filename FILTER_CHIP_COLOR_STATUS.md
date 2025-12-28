# Filter Chip Color Fix

## Issue Report
User requested that label filter chips in Notes page should use the same color as FAB + button: #6B4EFF

## Root Cause
The `FilterTabAdapter` (used in Notes page) was using `bg_date_time_chip` drawable for selected state, which has color #9787FF (light purple). It should use `bg_filter_pill_selected` which matches the FAB button color.

## Fix Applied ✅

### 1. Updated FilterTabAdapter
**File: `app/src/main/java/com/philkes/notallyx/presentation/view/main/FilterTabAdapter.kt`**

Changed line 64 to use the correct drawable:
```kotlin
val selectedDrawable = ContextCompat.getDrawable(context, R.drawable.bg_filter_pill_selected)
```

### 2. Updated Filter Pill Selected Color
**File: `app/src/main/res/drawable/bg_filter_pill_selected.xml`**

Changed color from #6D3FEF to #6B4EFF:
```xml
<solid android:color="#6B4EFF" />
```

### 3. Updated FAB Button Color
**File: `app/src/main/res/drawable/bg_fab_gradient.xml`**

Changed color from #6D3FEF to #6B4EFF:
```xml
<solid android:color="#6B4EFF" />
```

## Color Scheme Summary
- **#6B4EFF** (Purple): FAB + button, selected filter chips in Notes page, primary selected states
- **#6D3FEF** (Dark Purple): Primary actions, icons
- **#9787FF** (Light Purple): Date/time chips in HomeToday, dates, hints, secondary elements
- **#E8E8E8** (Light Gray): Unselected filter chips

## Result
Now the FAB + button and selected filter chips in Notes page both use #6B4EFF, creating a consistent visual experience.

