# 💾 Tính năng Lưu trạng thái AI Summary

## 🎯 Vấn đề đã giải quyết

**Trước đây:**
- User ấn Summary AI → Xem card → Ấn Áp dụng
- Ra vào lại note → Card biến mất, chỉ còn text đã áp dụng
- Nếu chưa ấn Áp dụng → Ra vào lại → Mất hết, phải làm lại từ đầu

**Bây giờ:**
- User ấn Summary AI → Xem card → Ấn Áp dụng
- **Ra vào lại note → Card vẫn hiển thị với đúng bản đã chọn** ✅
- **Nếu chưa ấn Áp dụng → Ra vào lại → Card vẫn giữ nguyên** ✅

## 🔧 Cách hoạt động

### 1. Lưu trạng thái (khi ấn Áp dụng)

```kotlin
// Lưu vào SharedPreferences với key theo noteId
val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
val noteId = notallyModel.id

prefs.edit()
    .putString("summary_text_$noteId", summaryText)
    .putString("original_text_$noteId", originalText)
    .putBoolean("is_using_summary_$noteId", isShowingSummary)
    .apply()

// Lưu note vào database
lifecycleScope.launch {
    saveNote()
}
```

### 2. Restore trạng thái (khi vào lại note)

```kotlin
override fun setStateFromModel(savedInstanceState: Bundle?) {
    super.setStateFromModel(savedInstanceState)
    updateEditText()
    
    // Restore AI summary state
    restoreAISummaryState()
}

private fun restoreAISummaryState() {
    val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
    val noteId = notallyModel.id
    
    val summaryText = prefs.getString("summary_text_$noteId", null)
    val originalText = prefs.getString("original_text_$noteId", null)
    val isUsingSummary = prefs.getBoolean("is_using_summary_$noteId", false)
    
    if (summaryText != null && originalText != null) {
        // Hiển thị lại card
        slideView.setTexts(originalText, summaryText)
        slideView.visibility = View.VISIBLE
        
        // Chọn đúng bản đang dùng
        if (isUsingSummary) {
            slideView.selectSummary(animated = false)
        } else {
            slideView.selectOriginal(animated = false)
        }
        
        // Ẩn EnterBody
        binding.EnterBody.visibility = View.GONE
        
        // Setup callback
        setupApplyButtonCallback(slideView, summaryText, originalText)
    }
}
```

## 📊 Data Structure

### SharedPreferences Keys:

| Key | Type | Mô tả |
|-----|------|-------|
| `summary_text_$noteId` | String | Text tóm tắt AI |
| `original_text_$noteId` | String | Text gốc ban đầu |
| `is_using_summary_$noteId` | Boolean | true = đang dùng summary<br>false = đang dùng gốc |

### Ví dụ:
```kotlin
// Note có id = 123
"summary_text_123" = "Đây là bản tóm tắt ngắn gọn..."
"original_text_123" = "Đây là nội dung ghi chú gốc rất dài..."
"is_using_summary_123" = true
```

## 🔄 Lifecycle

```
┌─────────────────────────────────────────────┐
│  User ấn AI Summary                         │
│  → Card hiển thị (Gốc + Tóm tắt)           │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User chọn bản nào (Gốc/Tóm tắt)           │
│  → Xem qua lại, so sánh                     │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User ấn "Áp dụng"                          │
│  → Lưu vào SharedPreferences                │
│  → Lưu vào Database                         │
│  → Ẩn card                                  │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User ra khỏi note                          │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│  User vào lại note                          │
│  → setStateFromModel() được gọi             │
│  → restoreAISummaryState() được gọi         │
│  → Card tự động hiển thị lại                │
│  → Chọn đúng bản đã dùng                    │
└─────────────────────────────────────────────┘
```

## 🧹 Cleanup (Xóa trạng thái)

Trạng thái sẽ bị xóa khi:

### 1. User restore về gốc hoàn toàn
```kotlin
private fun restoreOriginalFromInlineSummary() {
    // ... restore logic ...
    
    // Xóa trạng thái
    prefs.edit()
        .remove("summary_text_$noteId")
        .remove("original_text_$noteId")
        .remove("is_using_summary_$noteId")
        .apply()
}
```

### 2. User apply replace (không cần card nữa)
```kotlin
private fun applyInlineSummaryReplace() {
    // ... apply logic ...
    
    // Xóa trạng thái
    prefs.edit()
        .remove("summary_text_$noteId")
        .remove("original_text_$noteId")
        .remove("is_using_summary_$noteId")
        .apply()
}
```

## ✅ Lợi ích

1. **Persistent**: Trạng thái không bị mất khi ra vào lại
2. **Seamless**: User không phải làm lại từ đầu
3. **Flexible**: Có thể tiếp tục xem/chuyển đổi sau khi quay lại
4. **Efficient**: Không cần gọi AI lại, tiết kiệm thời gian và chi phí
5. **User-friendly**: UX mượt mà, không gây khó chịu

## 🎯 Use Cases

### Case 1: Ấn Áp dụng rồi ra vào lại
```
1. User ấn Summary AI
2. Chọn "Tóm tắt"
3. Ấn "Áp dụng"
4. Ra khỏi note
5. Vào lại note
→ Card hiển thị, "Tóm tắt" được chọn ✅
```

### Case 2: Chưa ấn Áp dụng, ra vào lại
```
1. User ấn Summary AI
2. Xem qua lại giữa Gốc và Tóm tắt
3. Chưa quyết định, ra khỏi note
4. Vào lại note
→ Card vẫn hiển thị, giữ nguyên trạng thái ✅
```

### Case 3: Ấn Áp dụng, chỉnh sửa, ra vào lại
```
1. User ấn Summary AI
2. Chọn "Tóm tắt"
3. Ấn "Áp dụng"
4. Chỉnh sửa text một chút
5. Ra khỏi note
6. Vào lại note
→ Card hiển thị với text đã chỉnh sửa ✅
```

## 🔒 Data Safety

- **Isolated**: Mỗi note có key riêng, không ảnh hưởng lẫn nhau
- **Lightweight**: Chỉ lưu text, không lưu binary data
- **Cleanup**: Tự động xóa khi không cần nữa
- **No Database Migration**: Không cần thay đổi schema database

## 🚀 Performance

- **Fast**: SharedPreferences rất nhanh
- **No Network**: Không cần gọi API lại
- **Instant Restore**: Hiển thị ngay lập tức khi vào note
- **Memory Efficient**: Chỉ load khi cần

---

**Tính năng này làm cho AI Summary trở nên thực sự hữu ích và user-friendly!** 🎉💾
