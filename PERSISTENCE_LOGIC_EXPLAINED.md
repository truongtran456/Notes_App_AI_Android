# 💾 AI Summary Persistence Logic

## 🎯 Goal

Two different behaviors based on whether user has clicked "Apply":

1. **NOT Applied Yet** → Show card with both versions (Original + Summary)
2. **Already Applied** → Show only the applied text (no card)

## 🔑 Key Flag: `has_applied`

This boolean flag determines the behavior:

```kotlin
has_applied_$noteId = false  // User is still viewing/comparing
has_applied_$noteId = true   // User has made a decision and applied
```

## 📊 State Flow

```
┌─────────────────────────────────────────────┐
│  User clicks AI Summary                     │
│  → Card appears with Original + Summary     │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  Save to SharedPreferences:                 │
│  - summary_text_$noteId                     │
│  - original_text_$noteId                    │
│  - is_using_summary_$noteId = false         │
│  - has_applied_$noteId = FALSE ⭐           │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User exits and returns                     │
│  → restoreAISummaryState() checks:          │
│     has_applied = false                     │
│  → Show card with both versions ✅          │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User clicks "Apply" button                 │
│  → Update SharedPreferences:                │
│     has_applied_$noteId = TRUE ⭐           │
│  → Hide card, show applied text             │
│  → Save to database                         │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User exits and returns                     │
│  → restoreAISummaryState() checks:          │
│     has_applied = true                      │
│  → Show ONLY applied text (no card) ✅      │
└─────────────────────────────────────────────┘
```

## 💻 Code Implementation

### 1. When Summary is Created (NOT applied yet)

```kotlin
private fun showInlineSummaryPreview(...) {
    // ... setup slideView ...
    
    // Save state as "viewing" (not applied)
    val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
    val noteId = notallyModel.id
    prefs.edit()
        .putString("summary_text_$noteId", summaryText)
        .putString("original_text_$noteId", originalText)
        .putBoolean("is_using_summary_$noteId", false)
        .putBoolean("has_applied_$noteId", false) // ⭐ NOT applied yet
        .apply()
}
```

### 2. When User Clicks "Apply"

```kotlin
private fun setupApplyButtonCallback(...) {
    slideView.onApplyClicked = { selectedText, isShowingSummary ->
        val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
        val noteId = notallyModel.id
        
        // Mark as APPLIED
        prefs.edit()
            .putString("summary_text_$noteId", summaryText)
            .putString("original_text_$noteId", originalText)
            .putBoolean("is_using_summary_$noteId", isShowingSummary)
            .putBoolean("has_applied_$noteId", true) // ⭐ NOW applied
            .apply()
        
        // Apply text and hide card
        binding.EnterBody.setText(selectedText)
        slideView.visibility = View.GONE
        
        // Save to database
        lifecycleScope.launch { saveNote() }
    }
}
```

### 3. When Restoring State

```kotlin
private fun restoreAISummaryState() {
    val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
    val noteId = notallyModel.id
    
    val summaryText = prefs.getString("summary_text_$noteId", null)
    val originalText = prefs.getString("original_text_$noteId", null)
    val isUsingSummary = prefs.getBoolean("is_using_summary_$noteId", false)
    val hasApplied = prefs.getBoolean("has_applied_$noteId", false)
    
    if (summaryText != null && originalText != null) {
        if (hasApplied) {
            // ✅ Applied → Show only the applied text
            val appliedText = if (isUsingSummary) summaryText else originalText
            binding.EnterBody.setText(appliedText)
            binding.EnterBody.visibility = View.VISIBLE
            slideView?.visibility = View.GONE
        } else {
            // ✅ Not applied → Show card with both versions
            slideView.setTexts(originalText, summaryText)
            slideView.visibility = View.VISIBLE
            binding.EnterBody.visibility = View.GONE
            
            // Select correct version
            if (isUsingSummary) {
                slideView.selectSummary(animated = false)
            } else {
                slideView.selectOriginal(animated = false)
            }
        }
    }
}
```

## 📋 SharedPreferences Keys

| Key | Type | Purpose |
|-----|------|---------|
| `summary_text_$noteId` | String | AI-generated summary text |
| `original_text_$noteId` | String | Original note text |
| `is_using_summary_$noteId` | Boolean | Which version is selected (true = summary, false = original) |
| `has_applied_$noteId` | Boolean | **Has user clicked "Apply"?** (true = applied, false = still viewing) |

## 🎬 User Scenarios

### Scenario 1: Viewing but not applied

```
1. User clicks AI Summary
2. Card shows: [✓ Original] [Summary] [Apply]
3. User switches to Summary
4. User exits app
5. User returns
→ Card still shows: [Original] [✓ Summary] [Apply] ✅
```

### Scenario 2: Applied summary

```
1. User clicks AI Summary
2. Card shows: [✓ Original] [Summary] [Apply]
3. User switches to Summary
4. User clicks "Apply"
5. Card disappears, EnterBody shows summary text
6. User exits app
7. User returns
→ EnterBody shows summary text (no card) ✅
```

### Scenario 3: Applied original

```
1. User clicks AI Summary
2. Card shows: [✓ Original] [Summary] [Apply]
3. User stays on Original
4. User clicks "Apply"
5. Card disappears, EnterBody shows original text
6. User exits app
7. User returns
→ EnterBody shows original text (no card) ✅
```

## 🧹 Cleanup

State is cleared when:
- User calls `restoreOriginalFromInlineSummary()` (cancel/restore)
- User calls `applyInlineSummaryReplace()` (apply and dismiss)

## ✅ Benefits

1. **Persistent viewing**: Can exit and return while still comparing
2. **Clear decision point**: "Apply" button makes it explicit
3. **No confusion**: After applying, no card appears (clean state)
4. **Flexible**: Can switch between versions before applying
5. **Efficient**: No need to regenerate summary

---

**This logic ensures the UI behavior matches user expectations perfectly!** 🎯✨
