# ✨ Nút "Áp dụng" - Tính năng mới

## 🎯 Mục đích

Cho phép user **chọn và áp dụng** bản Gốc hoặc Tóm tắt vào nội dung ghi chú, sau đó **ẩn card** và quay về trạng thái ban đầu.

## 📍 Vị trí

Nút "Áp dụng" nằm ở **góc phải header**, cạnh segmented control (GỐC/TÓM TẮT).

```
┌──────────────────────────────────────────┐
│  [✓ GỐC]  [TÓM TẮT]     [✓ Áp dụng]      │
└──────────────────────────────────────────┘
```

## 🎨 Thiết kế

### Visual
- **Background**: Tím (#9787FF)
- **Text**: Trắng, size 13sp, text "Áp dụng"
- **Icon**: Check (✓) màu trắng, size 18dp
- **Shape**: Bo góc 22dp (pill shape)
- **Height**: 44dp (cùng height với segmented control)

### Animation
- **Scale animation** khi ấn: 1.0 → 0.92 → 1.0
- **Duration**: 100ms mỗi phase
- Tạo cảm giác "nhấn" rất tự nhiên

## ⚙️ Hoạt động

### Khi user ấn nút "Áp dụng":

1. **Lấy text đang hiển thị**
   - Nếu đang chọn GỐC → lấy text gốc
   - Nếu đang chọn TÓM TẮT → lấy text tóm tắt

2. **Lưu trạng thái vào SharedPreferences** ⭐ MỚI
   ```kotlin
   // Lưu với key theo noteId
   prefs.edit()
       .putString("summary_text_$noteId", summaryText)
       .putString("original_text_$noteId", originalText)
       .putBoolean("is_using_summary_$noteId", isShowingSummary)
       .apply()
   ```

3. **Áp dụng vào EnterBody**
   ```kotlin
   binding.EnterBody.setText(selectedText)
   binding.EnterBody.visibility = View.VISIBLE
   ```

4. **Ẩn card summary**
   ```kotlin
   slideView.visibility = View.GONE
   ```

5. **Lưu note vào database** ⭐ MỚI
   ```kotlin
   lifecycleScope.launch {
       saveNote()
   }
   ```

6. **Hiển thị thông báo**
   - "Đã áp dụng bản tóm tắt" (nếu chọn summary)
   - "Đã giữ nguyên bản gốc" (nếu chọn gốc)

### Khi user ra vào lại note: ⭐ MỚI

1. **Kiểm tra SharedPreferences**
   - Có summary đã lưu không?
   - Đang dùng bản nào (gốc/tóm tắt)?

2. **Restore lại card nếu có**
   ```kotlin
   slideView.setTexts(originalText, summaryText)
   slideView.visibility = View.VISIBLE
   
   // Chọn đúng bản đang dùng
   if (isUsingSummary) {
       slideView.selectSummary(animated = false)
   } else {
       slideView.selectOriginal(animated = false)
   }
   ```

3. **Giữ nguyên trạng thái**
   - Card vẫn hiển thị
   - Segmented control chọn đúng bản
   - User có thể tiếp tục xem/chuyển đổi

## 💻 Code Implementation

### Layout (view_ai_slide_compare.xml)

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/ButtonApply"
    android:layout_width="wrap_content"
    android:layout_height="44dp"
    android:text="Áp dụng"
    android:textSize="13sp"
    android:textColor="@android:color/white"
    app:cornerRadius="22dp"
    app:backgroundTint="#9787FF"
    app:icon="@drawable/ic_check"
    app:iconSize="18dp"
    app:iconTint="@android:color/white" />
```

### Kotlin (AiSlideCompareView.kt)

```kotlin
// Callback
var onApplyClicked: ((selectedText: String, isShowingSummary: Boolean) -> Unit)? = null

// Setup
buttonApply.setOnClickListener {
    // Animation
    it.animate()
        .scaleX(0.92f).scaleY(0.92f)
        .setDuration(100)
        .withEndAction {
            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        .start()
    
    // Invoke callback
    val selectedText = getCurrentText()
    onApplyClicked?.invoke(selectedText, isShowingSummary)
}
```

### Usage (EditNoteActivity.kt)

```kotlin
slideView.onApplyClicked = { selectedText, isShowingSummary ->
    // Áp dụng text
    binding.EnterBody.setText(selectedText)
    binding.EnterBody.visibility = View.VISIBLE
    
    // Ẩn card
    slideView.visibility = View.GONE
    
    // Khôi phục layout
    val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
    binding.ContentLayout.setPadding(
        binding.ContentLayout.paddingLeft,
        binding.ContentLayout.paddingTop,
        binding.ContentLayout.paddingRight,
        paddingBottomPx
    )
    
    // Reset state
    inlineSummaryVisible = false
    inlineSummaryOriginalText = null
    inlineSummaryCurrentText = null
    
    // Toast
    val message = if (isShowingSummary) {
        "Đã áp dụng bản tóm tắt"
    } else {
        "Đã giữ nguyên bản gốc"
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
```

## 🎯 User Flow

1. User ấn nút AI Summary
2. Card hiển thị với 2 bản: Gốc và Tóm tắt
3. User xem qua lại bằng cách:
   - Click vào GỐC/TÓM TẮT
   - Hoặc swipe trái/phải
4. User quyết định chọn bản nào
5. **User ấn nút "Áp dụng"** ⭐
6. Card biến mất, text được áp dụng vào EnterBody
7. **Trạng thái được lưu vào SharedPreferences** ⭐ MỚI
8. **User ra vào lại → Card tự động hiển thị lại với đúng bản đã chọn** ⭐ MỚI

## 💾 Persistence (Lưu trữ trạng thái) ⭐ MỚI

### SharedPreferences Keys:
```kotlin
"summary_text_$noteId"      // Text tóm tắt
"original_text_$noteId"     // Text gốc
"is_using_summary_$noteId"  // true = đang dùng summary, false = đang dùng gốc
```

### Restore Logic:
```kotlin
private fun restoreAISummaryState() {
    val prefs = getSharedPreferences("ai_summary_state", Context.MODE_PRIVATE)
    val noteId = notallyModel.id
    
    val summaryText = prefs.getString("summary_text_$noteId", null)
    val originalText = prefs.getString("original_text_$noteId", null)
    val isUsingSummary = prefs.getBoolean("is_using_summary_$noteId", false)
    
    if (summaryText != null && originalText != null) {
        // Hiển thị lại card với đúng trạng thái
        slideView.setTexts(originalText, summaryText)
        slideView.visibility = View.VISIBLE
        
        if (isUsingSummary) {
            slideView.selectSummary(animated = false)
        } else {
            slideView.selectOriginal(animated = false)
        }
    }
}
```

### Clear State:
Trạng thái sẽ bị xóa khi:
- User ấn "Restore Original" (quay về gốc hoàn toàn)
- User ấn "Apply Replace" (áp dụng và không cần card nữa)

## ✅ Lợi ích

1. **Rõ ràng**: User biết chính xác khi nào áp dụng thay đổi
2. **Kiểm soát**: User có thể xem kỹ trước khi quyết định
3. **Linh hoạt**: Có thể chọn giữ nguyên gốc hoặc dùng tóm tắt
4. **Trực quan**: Nút nổi bật, dễ nhận biết
5. **Feedback**: Toast thông báo rõ ràng hành động đã thực hiện
6. **Persistent**: Trạng thái được lưu, ra vào lại vẫn giữ nguyên ⭐ MỚI
7. **Seamless**: Không mất công làm lại khi quay lại note ⭐ MỚI

## 🎨 Design Rationale

### Tại sao đặt ở góc phải?
- Theo thói quen đọc từ trái sang phải
- Nút action chính thường ở bên phải
- Không che segmented control (phần quan trọng)

### Tại sao dùng màu tím?
- Nhất quán với theme app (#9787FF)
- Nổi bật trên background trắng
- Tạo hierarchy: segmented control (nhạt) < nút áp dụng (đậm)

### Tại sao có icon check?
- Tăng nhận diện: "check" = "xác nhận"
- Giảm text, gọn gàng hơn
- Phù hợp với Material Design

## 🚀 Kết quả

Tính năng này giải quyết hoàn hảo yêu cầu:
- ✅ Có nút để chọn áp dụng
- ✅ Bố trí hợp lý (góc phải header)
- ✅ Ẩn card và quay về ban đầu
- ✅ Text được thay bằng bản đã chọn
- ✅ Có thể giữ nguyên gốc hoặc dùng tóm tắt
- ✅ **Lưu trạng thái vào SharedPreferences** ⭐ MỚI
- ✅ **Ra vào lại vẫn giữ nguyên card với đúng bản đã chọn** ⭐ MỚI
- ✅ **Không mất công làm lại khi quay lại note** ⭐ MỚI

---

**Thiết kế hiện đại, UX mượt mà, code clean, persistent state!** 🎉💾
