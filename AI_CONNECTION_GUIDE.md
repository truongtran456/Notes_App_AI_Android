# Hướng Dẫn Chi Tiết: Kết Nối Frontend (Android) và Backend (FastAPI)

## 📋 Tổng Quan Kiến Trúc

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Activity)                       │
│              AISummaryActivity.kt                            │
│  - Hiển thị UI, xử lý user interaction                      │
│  - Gọi Repository qua lifecycleScope.launch                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Repository Layer                                │
│              AIRepository.kt                                 │
│  - Chuyển đổi data từ UI format → API format               │
│  - Xử lý business logic                                     │
│  - Gọi Service interface                                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              API Service Layer                               │
│              NoteAIService.kt (Interface)                   │
│  - Định nghĩa API endpoints với Retrofit annotations       │
│  - Không có implementation, Retrofit tự generate           │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Network Layer                                   │
│              ApiClient.kt                                    │
│  - Cấu hình Retrofit, OkHttp                                │
│  - Quản lý base URL, timeout, interceptors                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Backend (FastAPI)                               │
│              http://10.0.2.2:8000/api/v1/                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 1. API CLIENT (ApiClient.kt) - Network Configuration Layer

### Mục đích:
- **Singleton object** quản lý Retrofit instance
- Cấu hình network (timeout, interceptors, base URL)
- Tạo và cache `NoteAIService` instance

### Code Chi Tiết:

```kotlin
object ApiClient {
    // Base URL mặc định cho emulator
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/api/v1/"
    
    // Timeout configuration (giảm để tránh ANR)
    private const val CONNECT_TIMEOUT = 10L  // 10 giây
    private const val READ_TIMEOUT = 30L     // 30 giây
    private const val WRITE_TIMEOUT = 30L    // 30 giây
    
    // Cache Retrofit và Service instance
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var noteAIService: NoteAIService? = null
}
```

### Giải Thích Từng Phần:

#### 1.1. Base URL Configuration
```kotlin
private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/api/v1/"
```
- **`10.0.2.2`**: Địa chỉ đặc biệt của Android Emulator để trỏ về `localhost` của máy host
- **`8000`**: Port mà FastAPI đang chạy
- **`/api/v1/`**: Base path của API

**Lưu ý:**
- Emulator: `http://10.0.2.2:8000`
- Device thật: `http://<IP_máy_tính>:8000` (ví dụ: `http://192.168.1.100:8000`)

#### 1.2. OkHttp Client Configuration
```kotlin
private fun createOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)  // Log requests/responses
        .addInterceptor { chain ->           // Custom interceptor
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
```

**Giải thích:**
- **`loggingInterceptor`**: Log tất cả HTTP requests/responses để debug
- **Custom interceptor**: Tự động thêm headers `Accept` và `Content-Type`
- **Timeouts**: Giới hạn thời gian để tránh app bị đơ (ANR)
- **`retryOnConnectionFailure(true)`**: Tự động retry nếu kết nối thất bại

#### 1.3. Retrofit Instance Creation
```kotlin
private fun createRetrofit(baseUrl: String): Retrofit {
    val gson = Gson()
    return Retrofit.Builder()
        .baseUrl(baseUrl)                              // Base URL
        .client(createOkHttpClient())                  // OkHttp client
        .addConverterFactory(GsonConverterFactory.create(gson))  // JSON converter
        .build()
}
```

**Giải thích:**
- **`baseUrl`**: URL gốc của API
- **`client`**: OkHttp client đã cấu hình
- **`GsonConverterFactory`**: Tự động convert JSON ↔ Kotlin objects

#### 1.4. Service Instance (Thread-Safe Singleton)
```kotlin
fun getService(): NoteAIService {
    return noteAIService
        ?: synchronized(this) { 
            noteAIService ?: createService().also { noteAIService = it } 
        }
}

private fun createService(): NoteAIService {
    val retrofitInstance = retrofit ?: createRetrofit(currentBaseUrl).also { retrofit = it }
    return retrofitInstance.create(NoteAIService::class.java)
}
```

**Giải thích:**
- **Double-checked locking**: Đảm bảo chỉ tạo 1 instance (thread-safe)
- **`retrofit.create()`**: Retrofit tự động generate implementation cho interface
- **Caching**: Lưu instance để tái sử dụng, không tạo mới mỗi lần

---

## 📡 2. API SERVICE INTERFACE (NoteAIService.kt) - API Definition Layer

### Mục đích:
- Định nghĩa các API endpoints bằng Retrofit annotations
- Retrofit sẽ tự động generate implementation

### Code Chi Tiết:

#### 2.1. Synchronous Endpoint - Summarize Text
```kotlin
@FormUrlEncoded
@POST("summarize")
suspend fun summarizeText(
    @Field("note") note: String,
    @Field("user_id") userId: String? = null,
    @Field("note_id") noteId: String? = null,
): Response<SummaryResponse>
```

**Giải thích:**
- **`@POST("summarize")`**: HTTP POST request đến `/api/v1/summarize`
- **`@FormUrlEncoded`**: Gửi data dạng form-urlencoded (key=value&key2=value2)
- **`@Field("note")`**: Field name trong form data
- **`suspend fun`**: Coroutine function, có thể gọi từ coroutine scope
- **`Response<SummaryResponse>`**: Wrapper chứa status code, headers, body

**Request sẽ là:**
```
POST /api/v1/summarize
Content-Type: application/x-www-form-urlencoded

note=Hello world&user_id=123&note_id=456
```

#### 2.2. File Upload Endpoint
```kotlin
@Multipart
@POST("process")
suspend fun processFile(
    @Part file: MultipartBody.Part,
    @Part("user_id") userId: RequestBody? = null,
    @Part("note_id") noteId: RequestBody? = null,
): Response<SummaryResponse>
```

**Giải thích:**
- **`@Multipart`**: Gửi multipart/form-data (dùng cho file upload)
- **`@Part file`**: File được đóng gói trong `MultipartBody.Part`
- **`@Part("user_id")`**: Text field trong multipart form

**Request sẽ là:**
```
POST /api/v1/process
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="image.jpg"
Content-Type: image/jpeg

[Binary file data]
------WebKitFormBoundary
Content-Disposition: form-data; name="user_id"

123
------WebKitFormBoundary--
```

#### 2.3. Async Job Endpoints
```kotlin
@Multipart
@POST("process/async")
suspend fun processFileAsync(...): Response<AsyncJobResponse>

@GET("jobs/{job_id}/status")
suspend fun getJobStatus(@Path("job_id") jobId: String): Response<JobStatusResponse>

@GET("jobs/{job_id}/result")
suspend fun getJobResult(@Path("job_id") jobId: String): Response<SummaryResponse>
```

**Giải thích:**
- **`@Path("job_id")`**: Thay thế `{job_id}` trong URL bằng giá trị thực
- **Flow**: Upload file → Nhận `job_id` → Poll status → Lấy result

---

## 🏗️ 3. REPOSITORY (AIRepository.kt) - Business Logic Layer

### Mục đích:
- Chuyển đổi data từ UI format → API format
- Xử lý errors, retry logic
- Wrap API calls trong `withContext(Dispatchers.IO)` để chạy trên background thread

### Code Chi Tiết:

#### 3.1. Summarize Note Function
```kotlin
suspend fun summarizeNote(
    noteText: String,
    userId: String? = null,
    noteId: String? = null,
): AIResult<SummaryResponse> =
    withContext(Dispatchers.IO) {  // Chạy trên IO thread
        try {
            // Gọi API
            val response = service.summarizeText(
                note = noteText, 
                userId = userId, 
                noteId = noteId
            )

            // Xử lý response
            if (response.isSuccessful && response.body() != null) {
                AIResult.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                AIResult.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            AIResult.Error(e.message ?: "Network error")
        }
    }
```

**Giải thích:**
- **`withContext(Dispatchers.IO)`**: Đảm bảo chạy trên background thread (không block UI)
- **`response.isSuccessful`**: Kiểm tra HTTP status code (200-299)
- **`response.body()`**: Parse JSON → `SummaryResponse` object
- **`response.errorBody()`**: Lấy error message nếu có
- **`AIResult`**: Sealed class để wrap kết quả (Success/Error/Loading)

#### 3.2. Process File Function
```kotlin
suspend fun processFile(
    fileUri: Uri,
    userId: String? = null,
    noteId: String? = null,
): AIResult<SummaryResponse> =
    withContext(Dispatchers.IO) {
        try {
            // 1. Convert URI → File
            val file = uriToTempFile(fileUri)
            val mimeType = context.contentResolver.getType(fileUri) 
                ?: "application/octet-stream"

            // 2. Tạo MultipartBody.Part
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // 3. Tạo RequestBody cho các field khác
            val userIdPart = userId?.toRequestBody("text/plain".toMediaTypeOrNull())
            val noteIdPart = noteId?.toRequestBody("text/plain".toMediaTypeOrNull())

            // 4. Gọi API
            val response = service.processFile(filePart, userIdPart, noteIdPart)

            // 5. Cleanup temp file
            file.delete()

            // 6. Xử lý response
            if (response.isSuccessful && response.body() != null) {
                AIResult.Success(response.body()!!)
            } else {
                AIResult.Error("Failed: ${response.code()}")
            }
        } catch (e: Exception) {
            AIResult.Error(e.message ?: "Network error")
        }
    }
```

**Giải thích:**
- **`uriToTempFile()`**: Copy file từ URI vào temp directory
- **`asRequestBody()`**: Convert File → RequestBody với MIME type
- **`MultipartBody.Part.createFormData()`**: Tạo multipart part cho file
- **`toRequestBody()`**: Convert String → RequestBody cho text fields
- **Cleanup**: Xóa temp file sau khi upload

#### 3.3. Async Processing với Progress
```kotlin
fun processFileWithProgress(
    fileUri: Uri,
    userId: String? = null,
    noteId: String? = null,
): Flow<AIResult<Any>> =
    flow {
        try {
            emit(AIResult.Loading)  // Bắt đầu loading

            // 1. Upload file và nhận job_id
            val asyncResponse = service.processFileAsync(filePart, userIdPart, noteIdPart)
            val jobId = asyncResponse.body()!!.jobId

            // 2. Polling status
            var attempts = 0
            while (attempts < MAX_POLLING_ATTEMPTS) {
                delay(POLLING_INTERVAL_MS)  // Đợi 1.5 giây
                
                val statusResponse = service.getJobStatus(jobId)
                val status = statusResponse.body()!!

                // Emit progress update
                emit(AIResult.Success(ProcessingProgress(
                    progress = status.progress ?: 0,
                    stage = status.stage,
                    status = JobStatus.fromString(status.status)
                )))

                // Kiểm tra status
                when (JobStatus.fromString(status.status)) {
                    JobStatus.COMPLETED -> {
                        val resultResponse = service.getJobResult(jobId)
                        emit(AIResult.Success(resultResponse.body()!!))
                        return@flow
                    }
                    JobStatus.FAILED -> {
                        emit(AIResult.Error(status.error ?: "Processing failed"))
                        return@flow
                    }
                    else -> {
                        // Continue polling
                    }
                }
            }
        } catch (e: Exception) {
            emit(AIResult.Error(e.message ?: "Network error"))
        }
    }.flowOn(Dispatchers.IO)  // Chạy trên IO thread
```

**Giải thích:**
- **`Flow`**: Reactive stream để emit progress updates
- **Polling**: Gọi `getJobStatus()` mỗi 1.5 giây để check progress
- **`emit()`**: Gửi update về UI layer
- **`flowOn(Dispatchers.IO)`**: Đảm bảo chạy trên background thread

---

## 🎨 4. UI LAYER (AISummaryActivity.kt) - Presentation Layer

### Mục đích:
- Hiển thị UI, xử lý user interaction
- Gọi Repository và cập nhật UI với kết quả

### Code Chi Tiết:

#### 4.1. Initialize Repository
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityAiSummaryBinding.inflate(layoutInflater)
    setContentView(binding.root)

    aiRepository = AIRepository(this)  // Tạo repository instance
    // ...
}
```

#### 4.2. Call API và Update UI
```kotlin
private fun summarizeNote() {
    showLoading()  // Hiển thị loading indicator

    lifecycleScope.launch {  // Launch coroutine
        val result = aiRepository.summarizeNote(
            noteText = noteContent,
            userId = null,
            noteId = if (noteId != -1L) noteId.toString() else null,
        )

        when (result) {
            is AIResult.Success -> {
                summaryResponse = result.data
                displayResults(result.data)  // Hiển thị kết quả
            }
            is AIResult.Error -> {
                showError(result.message)  // Hiển thị lỗi
            }
            is AIResult.Loading -> {
                showLoading()
            }
        }
    }
}
```

**Giải thích:**
- **`lifecycleScope.launch`**: Coroutine scope tự động cancel khi Activity bị destroy
- **`when (result)`**: Pattern matching với sealed class `AIResult`
- **UI updates**: Gọi trên main thread (coroutine tự động switch về main thread)

#### 4.3. Display Results
```kotlin
private fun displayResults(response: SummaryResponse) {
    showContent()

    // Hiển thị raw_text nếu có
    response.rawText?.let { rawText ->
        if (rawText.isNotBlank()) {
            binding.RawTextCard.isVisible = true
            binding.RawTextContent.text = rawText
        }
    }

    // Hiển thị summaries
    response.summaries?.let { summaries ->
        summaries.oneSentence?.let {
            binding.OneSentenceCard.isVisible = true
            binding.OneSentenceText.text = it
        }
        // ...
    }

    // Hiển thị questions
    response.questions?.let { questions ->
        // ...
    }

    // Hiển thị MCQs
    response.mcqs?.let { mcqs ->
        // ...
    }
}
```

---

## 📦 5. DATA MODELS (AIModels.kt) - Data Transfer Objects

### Mục đích:
- Định nghĩa cấu trúc data từ backend
- Gson tự động parse JSON → Kotlin objects

### Code Chi Tiết:

```kotlin
data class SummaryResponse(
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("summaries") val summaries: Summaries? = null,
    @SerializedName("questions") val questions: List<Question>? = null,
    @SerializedName("mcqs") val mcqs: MCQs? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("processed_text") val processedText: String? = null,
    @SerializedName("error") val error: String? = null,
)
```

**Giải thích:**
- **`@SerializedName("raw_text")`**: Map JSON field `raw_text` → Kotlin property `rawText`
- **`val rawText: String?`**: Nullable vì có thể không có trong response
- **Gson**: Tự động parse JSON response → `SummaryResponse` object

**JSON Response từ Backend:**
```json
{
  "raw_text": "Text đã được AI xử lý...",
  "summaries": {
    "one_sentence": "Tóm tắt 1 câu",
    "short_paragraph": "Tóm tắt chi tiết...",
    "bullet_points": ["Điểm 1", "Điểm 2"]
  },
  "questions": [
    {"question": "Câu hỏi 1?", "answer": "Đáp án 1"}
  ],
  "mcqs": {
    "easy": [...],
    "medium": [...],
    "hard": [...]
  }
}
```

---

## 🔄 6. FLOW HOÀN CHỈNH: Từ UI → Backend → UI

### Scenario: User chọn "Quick Summary"

```
1. USER ACTION
   └─> User click "Quick Summary" trong bottom sheet
       └─> EditNoteActivity.openAIActionsMenu()
           └─> AISummaryActivity.start(this, noteText, noteId, AISection.SUMMARY)

2. UI LAYER (AISummaryActivity)
   └─> onCreate()
       └─> summarizeNote()
           └─> showLoading()  // Hiển thị loading
           └─> lifecycleScope.launch {
               └─> aiRepository.summarizeNote(noteText, userId, noteId)

3. REPOSITORY LAYER (AIRepository)
   └─> summarizeNote()
       └─> withContext(Dispatchers.IO) {  // Switch to background thread
           └─> service.summarizeText(note, userId, noteId)

4. API SERVICE (NoteAIService - Retrofit generated)
   └─> @POST("summarize")
       └─> Retrofit tạo HTTP request:
           POST http://10.0.2.2:8000/api/v1/summarize
           Content-Type: application/x-www-form-urlencoded
           Body: note=Hello world&user_id=123&note_id=456

5. NETWORK LAYER (OkHttp)
   └─> OkHttp gửi request qua network
       └─> Interceptors thêm headers
       └─> Logging interceptor log request/response

6. BACKEND (FastAPI)
   └─> Nhận request tại /api/v1/summarize
       └─> Xử lý và trả về JSON response:
           {
             "raw_text": "...",
             "summaries": {...},
             "questions": [...],
             "mcqs": {...}
           }

7. NETWORK LAYER (OkHttp)
   └─> Nhận HTTP response
       └─> Logging interceptor log response

8. API SERVICE (Retrofit)
   └─> GsonConverterFactory parse JSON → SummaryResponse object
       └─> Return Response<SummaryResponse>

9. REPOSITORY LAYER
   └─> Kiểm tra response.isSuccessful
       └─> Return AIResult.Success(response.body()!!)
           hoặc AIResult.Error(...)

10. UI LAYER
    └─> when (result) {
        is AIResult.Success -> {
            displayResults(result.data)  // Hiển thị kết quả
        }
        is AIResult.Error -> {
            showError(result.message)  // Hiển thị lỗi
        }
    }
```

---

## 🎯 7. CÁC ĐIỂM QUAN TRỌNG

### 7.1. Threading
- **UI Thread**: Chỉ để update UI
- **Background Thread (IO)**: Tất cả network calls
- **`withContext(Dispatchers.IO)`**: Đảm bảo chạy trên background thread
- **Coroutine**: Tự động switch về main thread khi update UI

### 7.2. Error Handling
- **Try-catch**: Bắt exceptions
- **Response checking**: Kiểm tra `isSuccessful`
- **Error messages**: Parse từ `errorBody()`
- **User-friendly errors**: Hiển thị message dễ hiểu

### 7.3. Network Configuration
- **Base URL**: Cấu hình theo môi trường (emulator/device/production)
- **Timeouts**: Giảm để tránh ANR
- **Interceptors**: Logging, custom headers
- **Retry**: Tự động retry khi connection failed

### 7.4. Data Flow
- **Request**: UI → Repository → Service → Network → Backend
- **Response**: Backend → Network → Service → Repository → UI
- **Parsing**: Gson tự động parse JSON ↔ Kotlin objects

---

## 📝 8. VÍ DỤ CỤ THỂ: Summarize Text

### Backend Endpoint:
```python
@router.post("/summarize")
async def summarize(note: str, user_id: Optional[str] = None, note_id: Optional[str] = None):
    # Xử lý...
    return {
        "raw_text": "Text đã xử lý",
        "summaries": {...},
        "questions": [...],
        "mcqs": {...}
    }
```

### Frontend Call:
```kotlin
// 1. UI Layer
lifecycleScope.launch {
    val result = aiRepository.summarizeNote("Hello world", null, null)
    // ...
}

// 2. Repository
suspend fun summarizeNote(...): AIResult<SummaryResponse> =
    withContext(Dispatchers.IO) {
        val response = service.summarizeText(note, userId, noteId)
        // ...
    }

// 3. Service Interface
@POST("summarize")
suspend fun summarizeText(...): Response<SummaryResponse>

// 4. Retrofit tự động tạo HTTP request
POST http://10.0.2.2:8000/api/v1/summarize
Content-Type: application/x-www-form-urlencoded
Body: note=Hello world

// 5. Backend xử lý và trả về JSON

// 6. Gson parse JSON → SummaryResponse

// 7. Repository return AIResult.Success(data)

// 8. UI hiển thị kết quả
```

---

## ✅ 9. CHECKLIST Khi Tạo API Mới

1. ✅ Thêm endpoint vào `NoteAIService.kt` với annotations
2. ✅ Tạo/update data models trong `AIModels.kt`
3. ✅ Thêm function vào `AIRepository.kt` với error handling
4. ✅ Gọi từ UI layer với `lifecycleScope.launch`
5. ✅ Update UI với kết quả
6. ✅ Test với backend đang chạy

---

## 🔍 10. DEBUGGING TIPS

### Xem HTTP Requests/Responses:
- Check Logcat với tag `ApiClient`
- HttpLoggingInterceptor sẽ log tất cả requests/responses

### Kiểm tra Base URL:
```kotlin
Log.d("ApiClient", "Current URL: ${ApiClient.getCurrentBaseUrl()}")
```

### Test Connection:
```kotlin
lifecycleScope.launch {
    val connected = ApiClient.checkConnection()
    Log.d("Test", "Connected: $connected")
}
```

---

## 📚 Tài Liệu Tham Khảo

- **Retrofit**: https://square.github.io/retrofit/
- **OkHttp**: https://square.github.io/okhttp/
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **Gson**: https://github.com/google/gson

---

**Chúc bạn code vui vẻ! 🚀**

