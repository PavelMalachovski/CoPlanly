# Анализ кодовой базы: CoPlanly

## 📁 Структура проекта

### Дерево директорий (до 3-го уровня)

```
CoPlanly/
├── app/
│   ├── build.gradle.kts              # Конфигурация модуля приложения
│   ├── proguard-rules.pro            # Правила ProGuard
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # Манифест приложения
│           ├── java/com/coparently/app/
│           │   ├── CoPlanlyApplication.kt  # Application класс с Hilt
│           │   ├── data/              # Слой данных (Clean Architecture)
│           │   │   ├── local/        # Локальное хранилище (Room)
│           │   │   │   ├── CoPlanlyDatabase.kt
│           │   │   │   ├── Converters.kt
│           │   │   │   ├── dao/       # Data Access Objects
│           │   │   │   ├── entity/   # Entity модели для БД
│           │   │   │   └── preferences/  # Шифрованные настройки
│           │   │   ├── remote/       # Удаленные источники данных
│           │   │   │   ├── firebase/  # Firebase сервисы
│           │   │   │   └── google/    # Google сервисы
│           │   │   ├── repository/   # Реализации репозиториев
│           │   │   └── sync/         # Синхронизация с календарями
│           │   ├── domain/            # Слой домена (Clean Architecture)
│           │   │   ├── model/        # Доменные модели
│           │   │   └── repository/    # Интерфейсы репозиториев
│           │   ├── presentation/     # Слой презентации
│           │   │   ├── auth/         # Авторизация
│           │   │   ├── calendar/    # Экран календаря
│           │   │   ├── event/       # Управление событиями
│           │   │   ├── pairing/     # Связывание родителей
│           │   │   ├── settings/    # Настройки
│           │   │   ├── sync/        # Синхронизация
│           │   │   ├── navigation/  # Навигация
│           │   │   ├── theme/       # Тема и стили
│           │   │   └── MainActivity.kt
│           │   └── di/               # Dependency Injection (Hilt)
│           │       ├── DatabaseModule.kt
│           │       ├── FirebaseModule.kt
│           │       ├── GoogleModule.kt
│           │       └── RepositoryModule.kt
│           └── res/                  # Ресурсы приложения
│               └── values/           # Строки, темы (многоязычность)
│                   ├── strings.xml
│                   ├── values-cs/    # Чешский
│                   ├── values-en/    # Английский
│                   └── values-ru/    # Русский
├── build.gradle.kts                  # Корневой build файл
├── settings.gradle.kts               # Настройки проекта
├── gradle.properties                 # Свойства Gradle
├── README.md                          # Документация проекта
└── docs/                              # Дополнительная документация
```

### Описание директорий

- **`app/data/local/`** — Локальное хранилище данных на базе Room Database. Содержит DAO интерфейсы, Entity классы для отображения таблиц БД, конвертеры типов и управление зашифрованными настройками.

- **`app/data/remote/`** — Интеграции с внешними сервисами: Firebase (Firestore, Authentication, Messaging) и Google Services (Calendar API, Sign-In).

- **`app/data/repository/`** — Реализации репозиториев, маппинг между доменными моделями и сущностями данных, синхронизация локального и удаленного хранилищ.

- **`app/domain/`** — Бизнес-логика и модели домена. Чистый слой без зависимостей от фреймворков, содержит интерфейсы репозиториев и domain-модели.

- **`app/presentation/`** — UI слой на Jetpack Compose. Содержит экраны (Composables), ViewModels для управления состоянием и логики представления, навигацию и темизацию.

- **`app/di/`** — Модули Hilt для dependency injection. Разделены по доменам (Database, Firebase, Google, Repository).

### Принципы организации кода

Проект следует **Clean Architecture** с четким разделением на три слоя:

1. **Presentation Layer** — UI компоненты, ViewModels
2. **Domain Layer** — Бизнес-логика, интерфейсы репозиториев
3. **Data Layer** — Реализации репозиториев, источники данных (Room, Firebase)

Зависимости направлены внутрь: Presentation → Domain ← Data. Domain не зависит от внешних библиотек, что обеспечивает тестируемость и гибкость.

---

## 🛠 Технологический стек

| Категория | Технология | Версия | Назначение |
|-----------|-----------|--------|------------|
| **Язык** | Kotlin | 1.9.22 | Основной язык разработки |
| **UI Framework** | Jetpack Compose | BOM 2024.02.00 | Современный декларативный UI |
| **Material Design** | Material 3 | BOM 2024.02.00 | Дизайн-система Material 3 |
| **Архитектура** | MVVM + Clean Architecture | - | Паттерн организации кода |
| **DI** | Hilt (Dagger) | 2.48 | Dependency Injection |
| **Локальная БД** | Room | 2.6.1 | SQLite обертка |
| **Асинхронность** | Coroutines + Flow | 1.7.3 | Реактивные потоки данных |
| **Навигация** | Navigation Compose | 2.7.6 | Навигация между экранами |
| **Lifecycle** | Lifecycle Runtime | 2.7.0 | Управление жизненным циклом |
| **Calendar UI** | Kizitonwose Calendar | 2.4.0 | Календарный компонент |
| **Firebase** | Firebase BOM | 32.7.0 | Backend-as-a-Service |
| **Firebase Auth** | Firebase Auth | - | Аутентификация |
| **Firebase Firestore** | Firestore | - | NoSQL база данных |
| **Firebase Messaging** | FCM | - | Push-уведомления |
| **Google Services** | Play Services Auth | 20.7.0 | Google Sign-In |
| **Google Calendar** | Calendar API | v3-rev20231120 | Синхронизация календаря |
| **Безопасность** | Security Crypto | 1.1.0-alpha06 | Шифрованные SharedPreferences |
| **JSON** | Gson | 2.10.1 | Парсинг JSON |
| **Сборка** | Gradle | 8.2.2 | Система сборки |
| **Минимальный SDK** | Android 8.0 (API 26) | 26 | Минимальная версия Android |
| **Target SDK** | Android 14 (API 34) | 34 | Целевая версия Android |

### Ключевые зависимости

**Core Android:**
- `androidx.core:core-ktx:1.12.0` — Kotlin расширения для Android
- `androidx.activity:activity-compose:1.8.2` — Compose Activity интеграция

**Compose:**
- Используется BOM (Bill of Materials) для синхронизации версий всех Compose библиотек
- Material 3 для современного дизайна
- Material Icons Extended для иконок

**Build Tools:**
- Android Gradle Plugin 8.2.2
- Kotlin 1.9.22 с Kotlin Compiler Extension 1.5.8 для Compose

---

## 🏗 Архитектура

### Архитектурные паттерны

#### 1. Clean Architecture (3 слоя)

**Presentation Layer** (`app/presentation/`)
- **Экраны** (Composable функции) — декларативный UI
- **ViewModels** — управление состоянием, бизнес-логика для UI
- **Navigation** — определение маршрутов

Пример структуры экрана:

```kotlin
@Composable
fun CalendarScreen(
    onEventClick: (String) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel()
) {
    val events by eventViewModel.events.collectAsState()
    // UI код
}
```

**Domain Layer** (`app/domain/`)
- **Модели** — чистые data классы без зависимостей
- **Интерфейсы репозиториев** — контракты для работы с данными

Пример доменной модели:

```kotlin
data class Event(
    val id: String,
    val title: String,
    val startDateTime: LocalDateTime,
    val parentOwner: String,
    // ...
)
```

**Data Layer** (`app/data/`)
- **Реализации репозиториев** — маппинг между domain и data моделями
- **Local DAO** — доступ к Room Database
- **Remote DataSources** — Firebase, Google API клиенты

#### 2. MVVM (Model-View-ViewModel)

ViewModels инкапсулируют состояние и логику представления:

```kotlin
@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    fun loadEvents() {
        viewModelScope.launch {
            eventRepository.getAllEvents().collect { eventList ->
                _events.value = eventList
            }
        }
    }
}
```

#### 3. Repository Pattern

Репозитории абстрагируют источники данных и обеспечивают единый интерфейс:

```kotlin
interface EventRepository {
    fun getAllEvents(): Flow<List<Event>>
    suspend fun insertEvent(event: Event)
    suspend fun syncWithFirestore()
}
```

Реализация объединяет локальное (Room) и удаленное (Firestore) хранилища:

```kotlin
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val firestoreEventDataSource: FirestoreEventDataSource
) : EventRepository {
    override suspend fun insertEvent(event: Event) {
        eventDao.insertEvent(event.toEntity())
        // Синхронизация с Firestore при необходимости
        firestoreEventDataSource.insertEvent(event.id, eventData)
    }
}
```

### Управление состоянием

**StateFlow** — основной механизм для реактивного состояния:

- ViewModels используют `StateFlow` для публикации состояния
- UI компоненты подписываются через `collectAsState()`
- Поддержка состояний Loading/Success/Error через sealed классы

Пример UI State:

```kotlin
sealed class EventUiState {
    data object Loading : EventUiState()
    data class Success(val events: List<Event>) : EventUiState()
    data class Error(val message: String) : EventUiState()
}
```

### Организация API-слоя

**Многоуровневая архитектура данных:**

1. **DataSources** (remote/firebase, remote/google) — прямые вызовы API
2. **Repository implementations** — координация между local и remote
3. **Repository interfaces** (domain) — контракты для использования в domain слое

**Пример работы с Firestore:**

```kotlin
class FirestoreEventDataSource {
    fun insertEvent(eventId: String, eventData: Map<String, Any>) {
        firestore.collection("events")
            .document(eventId)
            .set(eventData)
    }
}
```

### Паттерны роутинга

Используется **Navigation Compose** с type-safe маршрутами:

```kotlin
sealed class Screen(val route: String) {
    data object Calendar : Screen("calendar")
    data object AddEvent : Screen("add_event")
    data object EditEvent : Screen("edit_event/{eventId}") {
        const val ARG_EVENT_ID = "eventId"
        fun createRoute(eventId: String): String = "edit_event/$eventId"
    }
}
```

Навигация через NavGraph:

```kotlin
NavHost(navController = navController, startDestination = Screen.Calendar.route) {
    composable(Screen.Calendar.route) { CalendarScreen(...) }
    composable(Screen.AddEvent.route) { AddEditEventScreen(...) }
}
```

### Обработка ошибок

- **Try-catch блоки** в ViewModels для обработки исключений
- **Error состояния** в UI State для отображения ошибок пользователю
- **Graceful degradation** — работа без интернета с локальной БД

---

## 🎨 UI/UX и стилизация

### Подходы к стилизации

**Material 3 Design System** — основа дизайна приложения.

**Кастомная тема** с брендовыми цветами:

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = CoPlanlyColors.BrandPrimary,
    secondary = CoPlanlyColors.MomPink,
    tertiary = CoPlanlyColors.BrandAccent,
    // ...
)
```

**Цветовая палитра** для родителей:
- Mom — розовый (#FF4081)
- Dad — синий (#2196F3)
- Поддержка светлой и темной темы

### Дизайн-система

Используется встроенная Material 3 тема с кастомизацией:

- **Typography** — определенные стили текста
- **Colors** — палитра брендовых цветов
- **Shapes** — скругления и формы компонентов
- **Spacing** — единая система отступов

**Пример использования:**

```kotlin
@Composable
fun CoPlanlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Адаптивность

- **Responsive layouts** через Compose layout компоненты
- **Modifier.fillMaxSize()** для адаптации под разные экраны
- Календарь использует горизонтальный скролл (HorizontalCalendar)

### Темизация

Поддержка **светлой и темной темы**:
- Автоматическое определение системной темы
- Опциональная поддержка Dynamic Color (Android 12+)
- Полная палитра цветов для обеих тем

### Доступность (a11y)

- **ContentDescription** для иконок и кнопок
- **Semantic roles** через Material компоненты
- **String resources** для локализации текста

---

## ✅ Качество кода

### Конфигурации линтеров

**Gradle настройки:**
- Kotlin code style: `official`
- AndroidX включен (`android.useAndroidX=true`)
- Kotlin incremental compilation

**Примечание:** Отдельные конфигурации ESLint/Prettier не найдены, что нормально для Kotlin проектов (используется встроенный форматтер Kotlin).

### Соглашения по именованию

**Пакеты:** `com.coparently.app` с разделением по слоям (data, domain, presentation)

**Классы:**
- ViewModels: `*ViewModel` (EventViewModel, CalendarViewModel)
- Screens: `*Screen` (CalendarScreen, AddEditEventScreen)
- Repositories: `*Repository` (интерфейс), `*RepositoryImpl` (реализация)
- DAO: `*Dao` (EventDao, UserDao)
- Entities: `*Entity` (EventEntity, UserEntity)

**Функции и переменные:** camelCase в соответствии с Kotlin conventions

### Качество TypeScript типизации

⚠️ **Проект использует Kotlin, не TypeScript.**

Kotlin обеспечивает строгую типизацию:
- Все функции и свойства типизированы
- Использование data class для моделей
- Null-safety через nullable типы (`String?`)

### Наличие и качество тестов

**Статус:** Тесты не найдены в структуре проекта.

**Настроено для тестирования:**
- JUnit 4.13.2
- Hilt testing зависимости
- AndroidX Test dependencies

**Рекомендация:** Добавить unit-тесты для ViewModels и Repository, instrumented тесты для UI компонентов.

### Документация в коде

**KDoc комментарии** используются активно:

```kotlin
/**
 * Repository interface for managing events.
 * Part of the domain layer in Clean Architecture.
 */
interface EventRepository {
    /**
     * Gets all events as a Flow.
     */
    fun getAllEvents(): Flow<List<Event>>
}
```

Все публичные классы, функции и свойства документированы согласно KDoc стандарту.

---

## 🔧 Ключевые компоненты

### 1. CalendarScreen

**Назначение:** Главный экран приложения, отображающий календарь с событиями и расписанием опеки.

**Ключевые особенности:**
- Использует Kizitonwose Calendar для календарного виджета
- Анимации переходов между месяцами
- Индикаторы событий для каждого дня
- Индикаторы опеки (mom/dad) по цветам
- Интеграция с EventViewModel и CalendarViewModel

**Пример кода:**

```12:24:app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt
@Composable
fun CalendarScreen(
    onEventClick: (String) -> Unit,
    onAddEventClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    eventViewModel: EventViewModel = hiltViewModel(),
    calendarViewModel: CalendarViewModel = hiltViewModel()
) {
    val events by eventViewModel.events.collectAsState()
    val custodySchedules by calendarViewModel.custodySchedules.collectAsState()
```

**Зависимости:**
- `EventViewModel` — получение событий
- `CalendarViewModel` — расписание опеки
- `HorizontalCalendar` — календарный компонент

---

### 2. EventViewModel

**Назначение:** Управление состоянием и логикой работы с событиями.

**Основные функции:**
- Загрузка событий (всех, по дате, по диапазону)
- Создание, обновление, удаление событий
- Управление UI состояниями (Loading/Success/Error)

**Пример кода:**

```20:45:app/src/main/java/com/coparently/app/presentation/event/EventViewModel.kt
@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventUiState>(EventUiState.Loading)
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    init {
        loadEvents()
    }

    /**
     * Loads all events.
     */
    fun loadEvents() {
        viewModelScope.launch {
            eventRepository.getAllEvents().collect { eventList ->
                _events.value = eventList
                _uiState.value = EventUiState.Success(eventList)
            }
        }
    }
```

**API:**
- `loadEvents()` — загрузить все события
- `createEvent(event)` — создать новое событие
- `updateEvent(event)` — обновить событие
- `deleteEvent(event)` — удалить событие

---

### 3. EventRepositoryImpl

**Назначение:** Реализация репозитория, объединяющая локальное хранилище (Room) и удаленную синхронизацию (Firestore).

**Особенности:**
- Двунаправленная синхронизация данных
- Автоматическая синхронизация с Firestore при изменении локальных данных
- Маппинг между domain моделями и entity

**Пример кода:**

```23:86:app/src/main/java/com/coparently/app/data/repository/EventRepositoryImpl.kt
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val firestoreEventDataSource: FirestoreEventDataSource
) : EventRepository {

    override suspend fun insertEvent(event: Event) {
        val entity = event.toEntity()
        eventDao.insertEvent(entity)

        // Sync to Firestore if authenticated
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null && !event.syncedToFirestore) {
            val eventData = mapOf(
                "id" to event.id,
                "title" to event.title,
                // ... остальные поля
            )
            firestoreEventDataSource.insertEvent(event.id, eventData)

            // Mark as synced
            val syncedEvent = event.copy(syncedToFirestore = true)
            eventDao.updateEvent(syncedEvent.toEntity())
        }
    }
```

**Зависимости:**
- `EventDao` — локальная БД
- `FirebaseAuthService` — проверка аутентификации
- `FirestoreEventDataSource` — синхронизация с Firestore

---

### 4. CoPlanlyDatabase

**Назначение:** Room Database для локального хранения данных.

**Структура:**
- Версия БД: 2
- Entity: EventEntity, UserEntity, CustodyScheduleEntity
- TypeConverters для LocalDateTime

**Пример:**

```19:44:app/src/main/java/com/coparently/app/data/local/CoPlanlyDatabase.kt
@Database(
    entities = [
        EventEntity::class,
        UserEntity::class,
        CustodyScheduleEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CoPlanlyDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao
    abstract fun custodyScheduleDao(): CustodyScheduleDao
}
```

---

### 5. NavGraph

**Назначение:** Определение навигационной структуры приложения.

**Маршруты:**
- Calendar — главный экран
- EventList — список событий
- AddEvent — создание события
- EditEvent — редактирование события
- Settings — настройки

**Пример:**

```18:36:app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Calendar.route
    ) {
        composable(Screen.Calendar.route) {
            CalendarScreen(
                onEventClick = { eventId ->
                    navController.navigate(Screen.EditEvent.createRoute(eventId))
                },
                onAddEventClick = {
                    navController.navigate(Screen.AddEvent.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
```

---

## 📋 Паттерны и best practices

### Переиспользуемые паттерны

1. **Dependency Injection через Hilt**
   - Все ViewModels и Repositories инжектируются
   - Модули разделены по доменам

2. **StateFlow для реактивного состояния**
   - Единый подход к управлению состоянием
   - Автоматическая рекомпозиция UI при изменении состояния

3. **Sealed классы для UI состояний**
   ```kotlin
   sealed class EventUiState {
       data object Loading : EventUiState()
       data class Success(val events: List<Event>) : EventUiState()
       data class Error(val message: String) : EventUiState()
   }
   ```

4. **Extension функции для маппинга**
   - `EventEntity.toDomain()` — конвертация в domain модель
   - `Event.toEntity()` — конвертация в entity

### Оптимизация производительности

- **Lazy initialization** ViewModels через `hiltViewModel()`
- **Flow collection** в ViewModelScope для автоматической отмены
- **Type converters** в Room для эффективного хранения дат
- **Compose recomposition** оптимизирована через StateFlow

### Обработка асинхронных операций

**Coroutines + Flow:**
- Все асинхронные операции в `viewModelScope.launch`
- Flow для реактивных потоков данных
- `suspend` функции для одноразовых операций

**Пример:**
```kotlin
fun loadEvents() {
    viewModelScope.launch {
        eventRepository.getAllEvents().collect { eventList ->
            _events.value = eventList
        }
    }
}
```

### Валидация данных

⚠️ **Примечание:** Явная валидация не обнаружена в коде. Рекомендуется добавить:
- Валидацию полей форм (AddEditEventScreen)
- Проверку дат (начало < конец)
- Валидацию email при регистрации

### Локализация

**Поддержка многоязычности:**
- `values/strings.xml` — русский (по умолчанию)
- `values-en/strings.xml` — английский
- `values-cs/strings.xml` — чешский
- `values-ru/strings.xml` — русский

Все строки вынесены в ресурсы, использование через `stringResource(R.string.*)`.

---

## 🔧 Инфраструктура разработки

### Скрипты в build.gradle.kts

**Основные команды:**
- `./gradlew assembleDebug` — сборка debug APK
- `./gradlew assembleRelease` — сборка release APK
- `./gradlew test` — запуск unit-тестов
- `./gradlew connectedAndroidTest` — запуск instrumented тестов
- `./gradlew clean` — очистка проекта

### Настройки среды разработки

**Gradle:**
- JVM args: `-Xmx2048m` (2GB памяти для Gradle)
- AndroidX включен
- Kotlin code style: official
- Kotlin incremental compilation

**Сборка:**
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Compile SDK: 34
- Java compatibility: 17

### Pre-commit hooks

⚠️ **Не обнаружены.** Рекомендуется настроить:
- Kotlin code formatting check
- Lint checks перед коммитом

### CI/CD

⚠️ **Конфигурация не найдена.** Рекомендуется настроить:
- GitHub Actions / GitLab CI / Jenkins
- Автоматические сборки при push
- Запуск тестов в CI
- Автоматическое деплой в Play Store (для release)

### Docker/контейнеризация

❌ **Не используется** (типично для Android проектов, используют эмуляторы/устройства)

---

## 📋 Выводы и рекомендации

### Сильные стороны проекта

✅ **Чистая архитектура** — четкое разделение на слои, соблюдение принципов Clean Architecture

✅ **Современный стек** — Kotlin, Jetpack Compose, Material 3, последние версии библиотек

✅ **Качественный код** — хорошая структура, KDoc документация, использование best practices

✅ **Реактивное программирование** — StateFlow и Flow для управления состоянием

✅ **Интеграции** — Firebase для синхронизации, Google Calendar API для интеграции с календарем

✅ **Локализация** — поддержка нескольких языков

✅ **Безопасность** — использование EncryptedSharedPreferences для чувствительных данных

### Области для улучшения

⚠️ **Тестирование** — отсутствуют unit и instrumented тесты. Рекомендуется:
- Добавить unit-тесты для ViewModels
- Добавить unit-тесты для Repository implementations
- Добавить instrumented тесты для критичных UI экранов
- Настроить покрытие кода (aim: >70%)

⚠️ **Валидация данных** — отсутствует явная валидация форм. Рекомендуется:
- Добавить валидацию полей в AddEditEventScreen
- Использовать библиотеку для валидации (например, custom validators)

⚠️ **Обработка ошибок** — базовая обработка через try-catch. Рекомендуется:
- Создать sealed класс для типов ошибок
- Добавить retry логику для network операций
- Улучшить пользовательские сообщения об ошибках

⚠️ **CI/CD** — отсутствует автоматизация. Рекомендуется:
- Настроить GitHub Actions для автоматических сборок
- Добавить автоматический запуск тестов
- Настроить автоматический деплой в Play Store (staging/production)

⚠️ **Документация** — хорошая KDoc, но можно улучшить:
- Добавить архитектурные диаграммы
- Создать CONTRIBUTING.md с руководством для разработчиков
- Добавить примеры использования API

### Уровень сложности проекта

**Оценка: Middle-Senior level**

**Обоснование:**
- Использование Clean Architecture требует понимания архитектурных принципов
- Работа с несколькими источниками данных (Room + Firestore)
- Интеграция с внешними API (Google Calendar)
- Реактивное программирование с Flow
- Jetpack Compose требует знания декларативного подхода к UI

**Junior-friendly аспекты:**
- Хорошая структура проекта облегчает навигацию
- Использование стандартных Android библиотек
- Подробная KDoc документация

**Senior-level аспекты:**
- Сложная архитектура с несколькими слоями
- Синхронизация данных между локальным и удаленным хранилищами
- Оптимизация производительности Compose

### Интересные решения

1. **Двунаправленная синхронизация** — автоматическая синхронизация локальных и удаленных данных с отслеживанием статуса синхронизации

2. **Индикаторы опеки** — визуальное отображение расписания опеки прямо в календаре через цветовые индикаторы

3. **Кастомизированная тема** — собственная палитра цветов для родителей (mom/dad) с поддержкой темной темы

4. **Анимации в календаре** — плавные переходы между месяцами и состояниями с использованием AnimatedContent

---

## Заключение

CoPlanly — хорошо структурированное Android приложение, следующее современным практикам разработки. Проект демонстрирует понимание Clean Architecture, использование актуального технологического стека и внимание к деталям UX.

Основные рекомендации для улучшения:
1. Добавить тестовое покрытие
2. Улучшить валидацию данных
3. Настроить CI/CD
4. Расширить обработку ошибок

Проект готов к дальнейшему развитию и может служить хорошим примером архитектуры для других Android проектов.

