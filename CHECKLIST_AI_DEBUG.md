# Checklist AI Selection Issue - Debug

## Problem Description
User reported that in checklist mode, the AI feature is not correctly detecting ticked items:
1. When all items are ticked → AI doesn't process anything (shows empty error)
2. When strikethrough (2 gạch) is used on text → AI processes that strikethrough text instead of ticked items

## Expected Behavior
- AI should process only the ticked/checked items in the checklist
- Strikethrough formatting should not affect which items are selected for AI processing

## Code Investigation

### Current Logic (EditListActivity.kt)
```kotlin
private fun processVocabAIForOption(option: AIOption) {
    val checkedItems =
        items
            .toMutableList()
            .filter { it.checked }  // Filter only checked items
            .map { it.body.toString().trim() }
            .filter { it.isNotBlank() }
    
    val checkedVocabItems = checkedItems.joinToString("\n")
    
    if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
        showToast(R.string.ai_error_empty_note)
        return
    }
    // ... process AI
}
```

This logic appears correct - it filters items by `it.checked` property.

## Debug Steps Added

Added logging to `processVocabAIForOption`:
- Total items count
- Checked items count
- Checked items content
- Final checkedVocabItems string

## To Debug:
1. Build and run the app
2. Open a checklist
3. Tick some items
4. Press AI button
5. Check logcat for "EditListActivity" tags

Look for:
```
D/EditListActivity: Total items: X
D/EditListActivity: Checked items count: Y
D/EditListActivity: Checked items: [item1, item2, ...]
D/EditListActivity: checkedVocabItems: 'text here'
```

## Possible Causes:
1. `items` list not properly synchronized with UI
2. `checked` property not being set correctly when user ticks items
3. Some other text selection logic interfering
4. Strikethrough spans affecting text extraction

## Next Steps:
- Review logcat output to see actual values
- Check ListItemAdapter to see how `checked` property is updated
- Verify that ticking items in UI actually updates the model
