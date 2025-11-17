# Рекомендации по улучшениям CoParently

## 🎯 Приоритет 1: Критические улучшения

### 1. Миграция с deprecated GoogleSignIn API
**Проблема:** GoogleSignIn API помечен как deprecated в последних версиях Play Services.

**Решение:**
```kotlin
// Вместо GoogleSignIn использовать Credential Manager API
implementation("androidx.credentials:credentials:1.2.0")
implementation("androidx.credentials:credentials-play-services-auth:1.2.0")
implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
```

**Ссылки:**
- [Credential Manager Guide](https://developer.android.com/training/sign-in/credential-manager)
- [Migration Guide](https://developer.android.com/training/sign-in/credential-manager-siwg)

**Оценка трудозатрат:** 4-6 часов

---

### 2. Реализация Cloud Functions для Push Notifications
**Проблема:** Текущая реализация создает notification queue в Firestore, но не отправляет уведомления.

**Решение:**
```javascript
// Firebase Cloud Functions
exports.sendNotification = functions.firestore
  .document('notification_queue/{notificationId}')
  .onCreate(async (snap, context) => {
    const notificationData = snap.data();
    const targetUserId = notificationData.targetUserId;

    // Get user's FCM token
    const userDoc = await admin.firestore()
      .collection('users')
      .doc(targetUserId)
      .get();

    const fcmToken = userDoc.data().fcmToken;

    // Send notification
    await admin.messaging().send({
      token: fcmToken,
      notification: {
        title: notificationData.data.title,
        body: notificationData.data.body
      },
      data: notificationData.data
    });

    // Update status
    await snap.ref.update({ status: 'sent' });
  });
```

**Оценка трудозатрат:** 3-4 часа

---

### 3. Расширенная валидация форм
**Проблема:** Отсутствует валидация email в PairingScreen и других формах.

**Решение:**
```kotlin
// В SettingsViewModel или utility class
fun validateEmail(email: String): ValidationResult {
    return when {
        email.isBlank() -> ValidationResult.Error("Email cannot be empty")
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
            ValidationResult.Error("Invalid email format")
        else -> ValidationResult.Success
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
```

**Оценка трудозатрат:** 2-3 часа

---

## 🔧 Приоритет 2: Функциональные улучшения

### 4. Полноценная форма AddEditChildInfo
**Текущее состояние:** Упрощенная форма с только именем ребенка.

**Что добавить:**
- Date picker для даты рождения
- Динамический список medications
- Динамический список activities
- Динамический список allergies
- Emergency contacts editor
- School info form

**Пример компонента:**
```kotlin
@Composable
fun MedicationEditor(
    medications: List<Medication>,
    onAdd: (Medication) -> Unit,
    onRemove: (Int) -> Unit,
    onEdit: (Int, Medication) -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }

    Column {
        medications.forEachIndexed { index, medication ->
            MedicationCard(
                medication = medication,
                onEdit = { onEdit(index, it) },
                onDelete = { onRemove(index) }
            )
        }

        if (isAddingNew) {
            MedicationForm(
                onSave = {
                    onAdd(it)
                    isAddingNew = false
                },
                onCancel = { isAddingNew = false }
            )
        } else {
            Button(onClick = { isAddingNew = true }) {
                Text("Add Medication")
            }
        }
    }
}
```

**Оценка трудозатрат:** 8-10 часов

---

### 5. Offline-first подход
**Проблема:** Приложение требует постоянного интернет-соединения.

**Решение:**
- Room уже используется, но нужно улучшить sync логику
- Добавить индикаторы sync статуса
- Конфликт-резолюция для одновременных изменений

```kotlin
// Conflict resolution strategy
suspend fun resolveConflict(
    local: Event,
    remote: Event
): Event {
    return when {
        // Server wins for deleted items
        remote.isDeleted -> remote

        // Latest timestamp wins
        local.updatedAt > remote.updatedAt -> local

        // For tie, use createdBy priority
        else -> if (local.lastModifiedBy == currentUserId) local else remote
    }
}
```

**Оценка трудозатрат:** 6-8 часов

---

### 6. Улучшенная обработка ошибок
**Проблема:** Некоторые ошибки не обрабатываются должным образом.

**Решение:**
```kotlin
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val retry: (() -> Unit)? = null
    ) : UiState<Nothing>()
}

// В ViewModel
_uiState.value = try {
    UiState.Success(repository.getData())
} catch (e: IOException) {
    UiState.Error(
        message = "Network error. Please check your connection.",
        throwable = e,
        retry = { loadData() }
    )
} catch (e: Exception) {
    UiState.Error(
        message = "Something went wrong: ${e.message}",
        throwable = e
    )
}
```

**Оценка трудозатрат:** 4-5 часов

---

## 🎨 Приоритет 3: UX улучшения

### 7. Темная тема
**Решение:**
- Material3 уже поддерживает темную тему
- Нужно добавить переключатель в Settings
- Сохранять предпочтение пользователя

```kotlin
// В SettingsViewModel
fun toggleTheme(isDark: Boolean) {
    viewModelScope.launch {
        preferencesRepository.setDarkTheme(isDark)
    }
}

// В Theme.kt - уже реализовано, нужно только добавить в Settings
@Composable
fun CoParentlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    // ...
}
```

**Оценка трудозатрат:** 2-3 часа

---

### 8. Анимации и переходы
**Текущее состояние:** Базовые переходы Navigation Compose.

**Улучшения:**
```kotlin
// Shared element transitions
@Composable
fun CalendarToEventTransition() {
    AnimatedContent(
        targetState = selectedEvent,
        transitionSpec = {
            slideInVertically { height -> height } + fadeIn() with
            slideOutVertically { height -> -height } + fadeOut()
        }
    ) { event ->
        if (event != null) {
            EventDetailScreen(event)
        } else {
            CalendarScreen()
        }
    }
}

// Loading skeletons
@Composable
fun SkeletonLoading() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition()
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        )
    )

    // Shimmer effect
}
```

**Оценка трудозатрат:** 4-6 часов

---

### 9. Локализация
**Решение:**
- Добавить strings.xml для разных языков
- Использовать stringResource() везде

```xml
<!-- res/values/strings.xml -->
<resources>
    <string name="app_name">CoParently</string>
    <string name="settings_title">Settings</string>
    <string name="notifications_enable">Push Notifications</string>
</resources>

<!-- res/values-ru/strings.xml -->
<resources>
    <string name="app_name">CoParently</string>
    <string name="settings_title">Настройки</string>
    <string name="notifications_enable">Push-уведомления</string>
</resources>
```

**Оценка трудозатрат:** 6-8 часов (зависит от количества языков)

---

## 🧪 Приоритет 4: Качество кода

### 10. Unit тесты для ViewModel
**Пример:**
```kotlin
@ExperimentalCoroutinesTest
class SettingsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fcmService: FcmService
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fcmService = mockk()
        userRepository = mockk()
        viewModel = SettingsViewModel(fcmService, userRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleNotifications enables notifications successfully`() = runTest {
        // Given
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { fcmService.updateUserToken(any()) } returns Result.success(Unit)

        // When
        viewModel.toggleNotifications(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.notificationsEnabled)
        assertNull(state.errorMessage)
        assertEquals("Notifications enabled", state.successMessage)
    }
}
```

**Оценка трудозатрат:** 8-10 часов для всех ViewModels

---

### 11. UI тесты
**Пример:**
```kotlin
@HiltAndroidTest
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsScreen_displaysNotificationToggle() {
        composeTestRule.onNodeWithText("Push Notifications").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_toggleNotifications_updatesState() {
        composeTestRule.onNodeWithText("Push Notifications").performClick()

        // Verify switch is checked
        composeTestRule.onNode(
            hasTestTag("notification_switch") and hasToggleState(true)
        ).assertExists()
    }
}
```

**Оценка трудозатрат:** 10-12 часов

---

### 12. Detekt для статического анализа
**Настройка:**
```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

detekt {
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4")
}
```

**Оценка трудозатрат:** 2-3 часа на настройку + исправление найденных проблем

---

## 📊 Приоритет 5: Аналитика и мониторинг

### 13. Firebase Analytics события
**Решение:**
```kotlin
class AnalyticsManager @Inject constructor(
    private val analytics: FirebaseAnalytics
) {
    fun logScreenView(screenName: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
    }

    fun logInvitationSent() {
        analytics.logEvent("invitation_sent") {
            param("method", "email")
        }
    }

    fun logChildInfoAdded() {
        analytics.logEvent("child_info_added", null)
    }
}
```

**Оценка трудозатрат:** 3-4 часа

---

### 14. Crashlytics интеграция
**Решение:**
```kotlin
// build.gradle.kts
implementation("com.google.firebase:firebase-crashlytics-ktx")

// Application class
class CoParentlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }
}

// В ViewModels
try {
    // risky operation
} catch (e: Exception) {
    FirebaseCrashlytics.getInstance().recordException(e)
    _uiState.value = UiState.Error(e.message ?: "Unknown error")
}
```

**Оценка трудозатрат:** 2-3 часа

---

## 🔒 Приоритет 6: Безопасность

### 15. Firestore Security Rules
**Текущие правила нужно улучшить:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users collection
    match /users/{userId} {
      allow read: if request.auth != null &&
                     (request.auth.uid == userId ||
                      resource.data.partnerId == request.auth.uid);
      allow write: if request.auth != null && request.auth.uid == userId;
    }

    // Events collection
    match /events/{eventId} {
      allow read: if request.auth != null &&
                     (resource.data.createdByFirebaseUid == request.auth.uid ||
                      resource.data.sharedWith.hasAny([request.auth.uid]));
      allow create: if request.auth != null &&
                       request.resource.data.createdByFirebaseUid == request.auth.uid;
      allow update, delete: if request.auth != null &&
                               resource.data.createdByFirebaseUid == request.auth.uid;
    }

    // Child info collection
    match /child_info/{childInfoId} {
      allow read, write: if request.auth != null &&
                            (resource.data.createdByFirebaseUid == request.auth.uid ||
                             get(/databases/$(database)/documents/users/$(request.auth.uid)).data.partnerId == resource.data.createdByFirebaseUid);
    }

    // Invitations
    match /invitations/{invitationId} {
      allow read: if request.auth != null &&
                     (resource.data.fromUserId == request.auth.uid ||
                      resource.data.toEmail == request.auth.token.email);
      allow create: if request.auth != null &&
                       request.resource.data.fromUserId == request.auth.uid;
      allow update: if request.auth != null &&
                       resource.data.toEmail == request.auth.token.email;
      allow delete: if request.auth != null &&
                       resource.data.fromUserId == request.auth.uid;
    }
  }
}
```

**Оценка трудозатрат:** 2-3 часа

---

## 📈 Оценка общих трудозатрат

| Приоритет | Задачи | Часы |
|-----------|--------|------|
| 1. Критические | 3 задачи | 9-13 часов |
| 2. Функциональные | 3 задачи | 18-23 часа |
| 3. UX | 3 задачи | 12-17 часов |
| 4. Качество | 3 задачи | 20-25 часов |
| 5. Аналитика | 2 задачи | 5-7 часов |
| 6. Безопасность | 1 задача | 2-3 часа |
| **ИТОГО** | **15 задач** | **66-88 часов** |

## 🎯 Рекомендуемый план реализации

### Спринт 1 (1-2 недели): Критические
1. Миграция GoogleSignIn API
2. Cloud Functions для notifications
3. Валидация форм

### Спринт 2 (1-2 недели): Функциональные
4. Полная форма AddEditChildInfo
5. Offline-first improvements
6. Улучшенная обработка ошибок

### Спринт 3 (1 неделя): UX
7. Темная тема
8. Анимации
9. Локализация (базовая)

### Спринт 4 (2 недели): Качество
10. Unit тесты
11. UI тесты
12. Detekt

### Спринт 5 (1 неделя): Мониторинг и безопасность
13. Analytics
14. Crashlytics
15. Firestore Security Rules

---

*Документ создан: 16 ноября 2025*
*Последнее обновление: 16 ноября 2025*

