# UiState Pattern - Руководство по использованию

## Обзор

В приложении CoPlanly реализован унифицированный `UiState` pattern для обработки ошибок с автоматическим retry механизмом и информативными сообщениями об ошибках.

## Основные компоненты

### 1. UiState<T>

Запечатанный класс, представляющий состояние UI операции:

```kotlin
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data class Loading(val message: String? = null, val progress: Float? = null) : UiState<Nothing>()
    data class Success<T>(val data: T, val message: String? = null) : UiState<T>()
    data class Error(val error: UiError, val previousData: Any? = null) : UiState<Nothing>()
}
```

### 2. UiError

Детализированная информация об ошибке с возможностью retry:

```kotlin
data class UiError(
    val message: String,
    val type: ErrorType,
    val throwable: Throwable? = null,
    val retry: (() -> Unit)? = null
)
```

### 3. ErrorType

Типы ошибок для правильной классификации:

```kotlin
enum class ErrorType {
    NETWORK,
    AUTHENTICATION,
    PERMISSION,
    VALIDATION,
    NOT_FOUND,
    SERVER,
    UNKNOWN
}
```

## Использование в ViewModel

### Базовый пример

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Item>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<Item>>> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Loading data...")

            try {
                val data = repository.getData()
                _uiState.value = UiState.Success(data)
            } catch (e: IOException) {
                _uiState.value = UiState.Error(
                    UiError.network(
                        message = "Network error. Please check your connection.",
                        retry = { loadData() }
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    UiError.fromException(e, retry = { loadData() })
                )
            }
        }
    }
}
```

### Пример с различными типами ошибок

```kotlin
fun saveItem(item: Item) {
    viewModelScope.launch {
        _uiState.value = UiState.Loading("Saving...")

        try {
            // Validate input
            if (item.name.isBlank()) {
                throw ValidationException("Name cannot be empty")
            }

            repository.save(item)
            _uiState.value = UiState.Success(
                data = item,
                message = "Item saved successfully"
            )
        } catch (e: IOException) {
            _uiState.value = UiState.Error(
                UiError.network(retry = { saveItem(item) })
            )
        } catch (e: ValidationException) {
            _uiState.value = UiState.Error(
                UiError.validation(
                    message = e.message ?: "Invalid input",
                    retry = null // Валидация не требует retry
                )
            )
        } catch (e: SecurityException) {
            _uiState.value = UiState.Error(
                UiError(
                    message = "You don't have permission to perform this action",
                    type = ErrorType.PERMISSION
                )
            )
        } catch (e: Exception) {
            _uiState.value = UiState.Error(
                UiError.fromException(e, retry = { saveItem(item) })
            )
        }
    }
}
```

## Использование в UI (Compose)

### 1. ErrorDisplay - Карточка с ошибкой

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        when (uiState) {
            is UiState.Error -> {
                ErrorDisplay(
                    error = (uiState as UiState.Error).error,
                    onDismiss = { viewModel.clearError() }
                )
            }
            is UiState.Success -> {
                // Show success content
            }
            else -> { /* Handle other states */ }
        }
    }
}
```

### 2. ErrorSnackbar - Компактное уведомление

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as UiState.Error).error.message
            )
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                if (uiState is UiState.Error) {
                    ErrorSnackbar(error = (uiState as UiState.Error).error)
                } else {
                    Snackbar(snackbarData = data)
                }
            }
        }
    ) { /* Content */ }
}
```

### 3. ErrorScreen - Полноэкранная ошибка

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        is UiState.Error -> {
            ErrorScreen(error = (uiState as UiState.Error).error)
        }
        is UiState.Success -> {
            // Show content
        }
        is UiState.Loading -> {
            LoadingScreen(message = (uiState as UiState.Loading).message)
        }
        else -> { /* Idle state */ }
    }
}
```

### 4. LoadingOverlay - Оверлей с обработкой ошибок

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LoadingOverlay(uiState = uiState) {
        // Your content here
        MyContent()
    }
}
```

## Вспомогательные функции

### toUiState()

Конвертация Result в UiState:

```kotlin
val result = repository.getData()
_uiState.value = result.toUiState(retry = { loadData() })
```

### map()

Трансформация данных внутри UiState:

```kotlin
val itemsUiState: StateFlow<UiState<List<Item>>> = ...
val namesUiState: StateFlow<UiState<List<String>>> =
    itemsUiState.map { items -> items.map { it.name } }
```

## Лучшие практики

### 1. Всегда предоставляйте retry для сетевых операций

```kotlin
// ✅ Правильно
_uiState.value = UiState.Error(
    UiError.network(retry = { loadData() })
)

// ❌ Неправильно
_uiState.value = UiState.Error(
    UiError.network()
)
```

### 2. Используйте правильный тип ошибки

```kotlin
// ✅ Правильно
catch (e: IOException) {
    UiError.network(retry = { ... })
}
catch (e: ValidationException) {
    UiError.validation(message = e.message)
}

// ❌ Неправильно
catch (e: Exception) {
    UiError(message = e.message ?: "Error", type = ErrorType.UNKNOWN)
}
```

### 3. Предоставляйте контекстные сообщения

```kotlin
// ✅ Правильно
UiState.Loading("Loading your events...")
UiState.Success(data, message = "Events loaded successfully")

// ❌ Неправильно
UiState.Loading()
UiState.Success(data)
```

### 4. Сбрасывайте состояние после обработки

```kotlin
fun clearError() {
    _uiState.value = UiState.Idle
}
```

## Примеры из проекта

### SettingsViewModel

```kotlin
fun toggleNotifications(enabled: Boolean) {
    viewModelScope.launch {
        _operationState.value = UiState.Loading(
            message = if (enabled) "Enabling notifications..." else "Disabling notifications..."
        )

        try {
            if (enabled) {
                val token = fcmService.getCurrentToken()
                    ?: throw IOException("Failed to get FCM token")
                fcmService.updateUserToken(token).getOrThrow()
            }

            _settingsState.value = _settingsState.value.copy(
                notificationsEnabled = enabled
            )
            _operationState.value = UiState.Success(
                data = Unit,
                message = "Notifications ${if (enabled) "enabled" else "disabled"} successfully"
            )
        } catch (e: IOException) {
            _operationState.value = UiState.Error(
                UiError.network(
                    message = "Network error. Please check your connection and try again.",
                    retry = { toggleNotifications(enabled) }
                )
            )
        } catch (e: Exception) {
            _operationState.value = UiState.Error(
                UiError.fromException(e, retry = { toggleNotifications(enabled) })
            )
        }
    }
}
```

## Тестирование

### Unit тесты для ViewModel

```kotlin
@Test
fun `toggleNotifications handles network error with retry`() = runTest {
    // Given
    coEvery { fcmService.getCurrentToken() } throws IOException("Network error")

    // When
    viewModel.toggleNotifications(true)
    advanceUntilIdle()

    // Then
    val state = viewModel.operationState.value
    assertTrue(state is UiState.Error)
    val error = (state as UiState.Error).error
    assertEquals(ErrorType.NETWORK, error.type)
    assertNotNull(error.retry)
}
```

## Миграция существующего кода

### До

```kotlin
sealed class MyUiState {
    object Loading : MyUiState()
    data class Success(val data: List<Item>) : MyUiState()
    data class Error(val message: String) : MyUiState()
}

fun loadData() {
    _uiState.value = MyUiState.Loading
    try {
        val data = repository.getData()
        _uiState.value = MyUiState.Success(data)
    } catch (e: Exception) {
        _uiState.value = MyUiState.Error(e.message ?: "Error")
    }
}
```

### После

```kotlin
private val _uiState = MutableStateFlow<UiState<List<Item>>>(UiState.Idle)

fun loadData() {
    viewModelScope.launch {
        _uiState.value = UiState.Loading("Loading data...")
        try {
            val data = repository.getData()
            _uiState.value = UiState.Success(data)
        } catch (e: Exception) {
            _uiState.value = UiState.Error(
                UiError.fromException(e, retry = { loadData() })
            )
        }
    }
}
```

## Заключение

UiState pattern обеспечивает:
- ✅ Унифицированную обработку ошибок
- ✅ Автоматический retry механизм
- ✅ Информативные сообщения об ошибках
- ✅ Типобезопасность
- ✅ Простоту тестирования
- ✅ Лучший UX

---

*Документ создан: 17 ноября 2025*

