# AI Summary Card - Modern UI Update

## 🎨 Thiết kế mới (Material You 2024-2025)

Card tóm tắt AI đã được thiết kế lại hoàn toàn với giao diện hiện đại, dễ sử dụng hơn.

### ✨ Tính năng chính

#### 1. **Segmented Control với Tick**
- Header card có 2 nút: **GỐC** và **TÓM TẮT**
- Nút được chọn có:
  - Icon tick (✓) màu tím
  - Background tím nhạt (#1A9787FF)
  - Text đậm màu tím (#9787FF)
- Nút không chọn:
  - Không có tick
  - Background trong suốt
  - Text màu xám (#666666)

#### 2. **Nút Áp Dụng** ⭐ MỚI
- Vị trí: Góc phải header, cạnh segmented control
- Chức năng: Áp dụng bản đang chọn (Gốc hoặc Tóm tắt) vào EnterBody
- Khi ấn:
  - Ẩn card summary
  - Thay text EnterBody bằng bản đã chọn
  - Quay về trạng thái ban đầu (như chưa ấn summary)
  - Hiển thị toast thông báo
- Style:
  - Background tím (#9787FF)
  - Text trắng, size 13sp
  - Icon check trắng
  - Bo góc 22dp
  - Animation scale khi ấn (0.92x)

#### 3. **Animation Mượt Mà**
- **Crossfade**: Nội dung fade out/in với translation nhẹ (12dp)
- **Segmented Control**: Scale + fade animation khi chuyển đổi
- **Duration**: 220ms với AccelerateDecelerateInterpolator
- **Tick Icon**: Scale animation khi xuất hiện/biến mất

#### 3. **Swipe Gesture**
- Vuốt trái → Xem tóm tắt
- Vuốt phải → Quay lại gốc
- Threshold: 100px + velocity 100px/s
- Tự động cập nhật segmented control khi swipe

#### 4. **Hint Nhẹ Nhàng**
- Text: "← Vuốt để xem bản khác →"
- Opacity: 0.35
- Tự ẩn sau 2 lần tương tác
- Chỉ hiển thị 1 lần (lưu trong SharedPreferences)

### 📐 Layout Structure

```
┌──────────────────────────────────────────┐
│  [✓ GỐC]  [TÓM TẮT]     [Áp dụng]        │  ← Header
├──────────────────────────────────────────┤
│                                          │
│  Nội dung (scroll độc lập)               │  ← Body
│                                          │
├──────────────────────────────────────────┤
│  ← Vuốt để xem bản khác →                │  ← Hint
└──────────────────────────────────────────┘
```

### 🎯 Design Principles

1. **Không đè chữ**: Header riêng biệt, không overlap nội dung
2. **Trạng thái rõ ràng**: Tick + màu sắc cho biết đang xem bản nào
3. **Chọn chính thức**: Tick = chọn bản này làm nội dung chính
4. **Swipe phụ trợ**: Swipe chỉ để xem nhanh, không thay thế click
5. **Modern & Clean**: Bo góc lớn (20dp), padding thoáng, màu nhẹ nhàng

### 🔧 Technical Details

#### Files Changed:
- `view_ai_slide_compare.xml` - Layout mới với segmented control
- `AiSlideCompareView.kt` - Logic mới với gesture + animation
- `ic_check_tick.xml` - Icon tick đơn giản
- `bg_segmented_button_ripple.xml` - Ripple effect cho button

#### Key Methods:
- `selectOriginal(animated: Boolean)` - Chọn hiển thị bản gốc
- `selectSummary(animated: Boolean)` - Chọn hiển thị bản tóm tắt
- `toggle()` - Chuyển đổi giữa 2 bản
- `getCurrentText()` - Lấy text đang hiển thị
- `isShowingSummaryState()` - Kiểm tra trạng thái hiện tại
- `onSelectionChanged` - Callback khi user chọn bản nào
- `onApplyClicked` - Callback khi user ấn nút Áp dụng ⭐ MỚI

#### Animation Details:
- **Crossfade**: 220ms total (110ms fade out + 110ms fade in)
- **Translation**: ±12dp khi chuyển đổi
- **Segmented Control**: Scale 0.95-1.0, alpha 0-1
- **Tick Icon**: Scale 0.8-1.0, alpha 0-1

### 🎨 Color Palette

```kotlin
Primary Purple: #9787FF
Background Purple: #1A9787FF (10% opacity)
Text Active: #9787FF
Text Inactive: #666666
Text Content: #1C1B1F
Hint Text: #999999
Divider: #F0F0F0
Card Background: #FFFFFF
Segmented BG: #F5F5F7
```

### 📱 Responsive Design

- Min height: 140dp
- Max height: 400dp (với scroll)
- Padding: 20dp horizontal, 16-20dp vertical
- Line spacing: 6dp extra + 1.2x multiplier
- Card radius: 20dp
- Button radius: 20dp
- Segmented control height: 44dp

### ✅ Compatibility

- Tương thích 100% với code hiện tại
- Không cần thay đổi EditNoteActivity
- Không cần thay đổi AISummaryActivity
- Tất cả method cũ vẫn hoạt động

### 🚀 Usage Example

```kotlin
// Setup
slideView.setTexts(originalText, summaryText)
slideView.visibility = View.VISIBLE

// Listen to selection changes (optional)
slideView.onSelectionChanged = { isShowingSummary ->
    // User chuyển đổi giữa Gốc/Tóm tắt
    Log.d("Summary", "Đang xem: ${if (isShowingSummary) "Tóm tắt" else "Gốc"}")
}

// Handle apply button click ⭐ MỚI
slideView.onApplyClicked = { selectedText, isShowingSummary ->
    // Áp dụng text đã chọn
    binding.EnterBody.setText(selectedText)
    binding.EnterBody.visibility = View.VISIBLE
    
    // Ẩn card
    slideView.visibility = View.GONE
    
    // Hiển thị thông báo
    val message = if (isShowingSummary) {
        "Đã áp dụng bản tóm tắt"
    } else {
        "Đã giữ nguyên bản gốc"
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

// Show hint once
val prefs = getSharedPreferences("ai_hints", Context.MODE_PRIVATE)
slideView.showHintOnce(prefs)
```

---

## 🎉 Kết quả

Card mới:
- ✅ Hiện đại, không quê mùa
- ✅ Dễ hiểu, trạng thái rõ ràng
- ✅ Animation mượt mà
- ✅ Swipe tự nhiên
- ✅ Không đè chữ
- ✅ Có thể chọn bản nào làm chính

Hoàn toàn đáp ứng yêu cầu thiết kế Material You 2024-2025! 🎨✨
