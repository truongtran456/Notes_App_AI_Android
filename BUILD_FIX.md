# 🔧 Build Fix Guide

## ❌ Lỗi gặp phải

```
e: [ksp] java.lang.NullPointerException
e: file:///D:/ANDROID/NotallyX/app/src/main/java/com/philkes/notallyx/presentation/activity/note/EditNoteActivity.kt:289:13 Expecting member declaration
```

## ✅ Nguyên nhân

Code bị **duplicate** trong file `EditNoteActivity.kt` do merge conflict hoặc copy/paste lỗi.

## 🔨 Đã fix

Xóa đoạn code duplicate từ dòng 289-302 trong `EditNoteActivity.kt`.

## 🚀 Cách build lại

### Option 1: Clean build trong Android Studio
```
Build > Clean Project
Build > Rebuild Project
```

### Option 2: Gradle command (nếu JAVA_HOME đã setup đúng)
```bash
./gradlew clean
./gradlew assembleDebug
```

### Option 3: Invalidate caches
```
File > Invalidate Caches / Restart...
Chọn "Invalidate and Restart"
```

## 📝 Kiểm tra

Sau khi build thành công, kiểm tra:

1. ✅ File `EditNoteActivity.kt` không có syntax error
2. ✅ Các function sau hoạt động đúng:
   - `restoreOriginalFromInlineSummary()`
   - `applyInlineSummaryReplace()`
   - `restoreAISummaryState()`
   - `setupApplyButtonCallback()`

## 🐛 Nếu vẫn lỗi

### Lỗi JAVA_HOME
```
ERROR: JAVA_HOME is set to an invalid directory
```

**Fix:**
1. Mở Android Studio
2. File > Settings > Build, Execution, Deployment > Build Tools > Gradle
3. Chọn "Gradle JDK" đúng version
4. Hoặc set JAVA_HOME trong environment variables

### Lỗi KSP cache
```
e: [ksp] java.lang.NullPointerException
```

**Fix:**
1. Xóa folder `.gradle` trong project
2. Xóa folder `build` trong project và module `app`
3. Sync Gradle lại
4. Rebuild

### Lỗi syntax còn sót
Kiểm tra các dòng sau trong `EditNoteActivity.kt`:
- Dòng 260-340: Functions `restoreOriginalFromInlineSummary()` và `applyInlineSummaryReplace()`
- Dòng 405-460: Function `restoreAISummaryState()`
- Dòng 180-230: Function `setupApplyButtonCallback()`

## ✨ Kết quả mong đợi

Sau khi build thành công:
- ✅ App chạy được
- ✅ Nút "Áp dụng" hoạt động
- ✅ Trạng thái summary được lưu
- ✅ Ra vào lại note vẫn giữ nguyên card

---

**Nếu vẫn gặp vấn đề, hãy:**
1. Check lại file `EditNoteActivity.kt` có đúng syntax không
2. Xóa cache và rebuild
3. Restart Android Studio
