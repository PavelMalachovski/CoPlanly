# 🤝 Contributing to CoPlanly

Спасибо за интерес к проекту CoPlanly! Мы ценим любой вклад — от исправления опечаток до новых функций.

---

## 📋 Содержание

- [Кодекс поведения](#-кодекс-поведения)
- [С чего начать](#-с-чего-начать)
- [Процесс разработки](#-процесс-разработки)
- [Правила кодирования](#-правила-кодирования)
- [Commit Convention](#-commit-convention)
- [Pull Request Process](#-pull-request-process)
- [Тестирование](#-тестирование)
- [Документация](#-документация)

---

## 📜 Кодекс поведения

Участвуя в этом проекте, вы соглашаетесь:

- Быть уважительным к другим участникам
- Использовать конструктивную критику
- Фокусироваться на том, что лучше для сообщества
- Проявлять эмпатию к другим участникам

---

## 🚀 С чего начать

### 1. Найдите задачу

Ищите issues с метками:
- `good first issue` — для новичков
- `help wanted` — помощь приветствуется
- `bug` — исправление багов
- `enhancement` — новые функции

### 2. Сообщите о намерении

Оставьте комментарий в issue, чтобы другие знали, что вы работаете над задачей.

### 3. Fork и Clone

```bash
# Fork через GitHub UI
# Затем клонируйте свой fork
git clone https://github.com/YOUR-USERNAME/CoPlanly.git
cd CoPlanly

# Добавьте upstream remote
git remote add upstream https://github.com/original-owner/CoPlanly.git
```

### 4. Настройте окружение

```bash
# Откройте в Android Studio
studio .

# Синхронизируйте Gradle
# File > Sync Project with Gradle Files

# Настройте google-services.json (см. docs/google-oauth-setup.md)
```

---

## 💻 Процесс разработки

### 1. Создайте branch

```bash
# Всегда создавайте новый branch от main
git checkout main
git pull upstream main
git checkout -b feature/your-feature-name

# Или для багфиксов
git checkout -b fix/bug-description
```

### Naming Convention для branches:

- `feature/` — новые функции
- `fix/` — исправление багов
- `refactor/` — рефакторинг
- `docs/` — изменения в документации
- `test/` — добавление тестов
- `chore/` — технические изменения

**Примеры:**
```
feature/add-expense-tracker
fix/calendar-date-overflow
refactor/repository-pattern
docs/update-readme
test/add-calendar-tests
chore/update-dependencies
```

### 2. Разработка

- Следуйте [правилам кодирования](#-правила-кодирования)
- Пишите тесты для новой функциональности
- Обновляйте документацию при необходимости
- Регулярно делайте commits

### 3. Тестирование

```bash
# Unit тесты
./gradlew test

# Lint проверка
./gradlew lint

# Сборка проекта
./gradlew assembleDebug
```

### 4. Push и PR

```bash
git push origin feature/your-feature-name
```

Затем откройте Pull Request через GitHub UI.

---

## 📏 Правила кодирования

### Clean Architecture

Проект следует принципам Clean Architecture:

```
├── data/       # Слой данных (Repository implementations, API clients, DB)
├── domain/     # Слой домена (Use Cases, Repository interfaces, Models)
└── presentation/ # Слой UI (Compose, ViewModels)
```

**Правила:**
- Domain layer не должен зависеть от других слоёв
- Data layer реализует интерфейсы из Domain
- Presentation зависит только от Domain

### Jetpack Compose

#### ✅ DO:

```kotlin
@Composable
fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stateless composable
    // State hoisted to parent/ViewModel
}
```

#### ❌ DON'T:

```kotlin
@Composable
fun CalendarDay(date: LocalDate) {
    // State inside composable
    var isSelected by remember { mutableStateOf(false) }
}
```

### Material 3

Всегда используйте Material 3 компоненты и темы:

```kotlin
// ✅ Правильно
Text(
    text = "Hello",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.primary
)

// ❌ Неправильно
Text(
    text = "Hello",
    fontSize = 16.sp,
    color = Color(0xFF0000FF)
)
```

### KDoc комментарии

Все публичные функции должны иметь KDoc:

```kotlin
/**
 * Generates weeks for the month view.
 *
 * @param yearMonth The month to display
 * @param firstDayOfWeek First day of the week (Monday or Sunday)
 * @param weeksToShow Number of weeks to display (default 7)
 * @return List of weeks, each week contains 7 days
 */
private fun generateWeeksForMonth(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    weeksToShow: Int = 7
): List<List<LocalDate>> {
    // ...
}
```

### Naming Conventions

```kotlin
// Classes: PascalCase
class CalendarViewModel

// Functions: camelCase
fun loadEvents()

// Variables: camelCase
val selectedDate: LocalDate

// Constants: SCREAMING_SNAKE_CASE
const val MAX_WEEKS_TO_SHOW = 7

// Composables: PascalCase
@Composable
fun CalendarScreen()

// Private composables: PascalCase with "private"
@Composable
private fun WeekRow()
```

### Обязательные правила

Из [.cursorrules](.cursorrules):

1. ✅ Используйте **только Jetpack Compose** (никакого XML)
2. ✅ Минимальный SDK = **26**
3. ✅ Все UI-компоненты должны быть **stateless**
4. ✅ Используйте **ViewModel** для логики
5. ✅ **Dependency Injection** через Hilt
6. ✅ **Тесты обязательны** для каждого публичного метода repository
7. ✅ Не используйте **deprecated API**
8. ✅ Придерживайтесь **Material3**
9. ✅ Комментарии по стандарту **KDoc**

---

## 📝 Commit Convention

Мы используем [Conventional Commits](https://www.conventionalcommits.org/).

### Формат

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat:` — новая функция
- `fix:` — исправление бага
- `docs:` — обновление документации
- `style:` — форматирование (без изменения логики)
- `refactor:` — рефакторинг кода
- `perf:` — улучшение производительности
- `test:` — добавление/изменение тестов
- `chore:` — технические изменения
- `ci:` — изменения в CI/CD
- `build:` — изменения в сборке

### Scope (опционально)

- `calendar` — календарь
- `event` — события
- `auth` — авторизация
- `sync` — синхронизация
- `ui` — пользовательский интерфейс

### Примеры

```bash
# Простой commit
git commit -m "feat: add swipe gestures to month view"

# С scope
git commit -m "fix(calendar): resolve date overflow in top bar"

# С body
git commit -m "feat(calendar): add week numbers to month view

- Display week numbers on the left side
- Use WeekFields for locale-aware calculation
- Add 7 weeks instead of variable count"

# Breaking change
git commit -m "feat!: change MonthView API to support swipe gestures

BREAKING CHANGE: MonthView now requires onMonthChange callback"
```

---

## 🔄 Pull Request Process

### 1. Создание PR

- Заполните шаблон PR
- Дайте описательное название
- Опишите изменения подробно
- Добавьте скриншоты/видео для UI изменений
- Свяжите с related issues (fixes #123)

### 2. Checklist

Убедитесь, что вы выполнили:

- [ ] Код следует правилам проекта
- [ ] Добавлены/обновлены тесты
- [ ] Все тесты проходят (`./gradlew test`)
- [ ] Нет lint ошибок (`./gradlew lint`)
- [ ] Проект собирается (`./gradlew assembleDebug`)
- [ ] Обновлена документация (если нужно)
- [ ] Добавлены KDoc комментарии
- [ ] Проверена работа на разных размерах экранов
- [ ] Проверена работа в светлой/темной темах

### 3. Review Process

- Maintainers рассмотрят PR в течение 3-7 дней
- Ответьте на комментарии и внесите изменения
- После approval, PR будет смержен

### 4. После merge

```bash
# Обновите свой fork
git checkout main
git pull upstream main
git push origin main

# Удалите feature branch (опционально)
git branch -d feature/your-feature-name
git push origin --delete feature/your-feature-name
```

---

## 🧪 Тестирование

### Unit Tests

```kotlin
@Test
fun `generateWeeksForMonth returns exactly 7 weeks`() {
    // Given
    val yearMonth = YearMonth.of(2025, 11)
    val firstDayOfWeek = DayOfWeek.MONDAY

    // When
    val weeks = generateWeeksForMonth(yearMonth, firstDayOfWeek)

    // Then
    assertEquals(7, weeks.size)
    weeks.forEach { week ->
        assertEquals(7, week.size)
    }
}
```

### UI Tests

```kotlin
@Test
fun calendarDisplaysCurrentMonth() {
    composeTestRule.setContent {
        CoPlanlyTheme {
            CalendarScreen(
                onEventClick = {},
                onAddEventClick = {}
            )
        }
    }

    // Verify current month is displayed
    val currentMonth = YearMonth.now()
        .format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    composeTestRule
        .onNodeWithText(currentMonth)
        .assertIsDisplayed()
}
```

### Coverage

Стремитесь к:
- **80%+ coverage** для domain layer
- **60%+ coverage** для data layer
- **40%+ coverage** для presentation layer

```bash
# Генерация отчета
./gradlew jacocoTestReport

# Отчет доступен в
open app/build/reports/jacoco/test/html/index.html
```

---

## 📚 Документация

### Когда обновлять

Обновляйте документацию при:
- Добавлении новых функций
- Изменении API
- Исправлении багов (если это недокументированное поведение)
- Изменении процесса разработки

### Что обновлять

- `README.md` — для крупных изменений
- `CHANGELOG.md` — для всех изменений
- `docs/` — для специфической документации
- Inline комментарии — для сложной логики
- KDoc — для публичных API

### Стиль документации

- Используйте Markdown
- Добавляйте примеры кода
- Включайте скриншоты для UI
- Пишите ясно и кратко
- Используйте эмодзи для визуальной привлекательности 🎨

---

## 🐛 Reporting Bugs

### Перед созданием issue

1. Проверьте existing issues
2. Убедитесь, что используете последнюю версию
3. Попробуйте воспроизвести на чистой установке

### Template для bug report

```markdown
**Описание**
Краткое описание проблемы

**Шаги воспроизведения**
1. Открыть '...'
2. Нажать на '...'
3. Прокрутить вниз до '...'
4. Наблюдать ошибку

**Ожидаемое поведение**
Что должно произойти

**Скриншоты**
Если применимо

**Окружение:**
 - Устройство: [e.g. Pixel 6]
 - OS: [e.g. Android 14]
 - Версия приложения: [e.g. 2.0.0]

**Дополнительная информация**
Логи, stacktrace и т.д.
```

---

## 💡 Feature Requests

Хотите предложить новую функцию?

1. Проверьте [roadmap](.cursor/roadmap.md)
2. Найдите existing feature requests
3. Создайте новый issue с меткой `enhancement`

### Template

```markdown
**Описание функции**
Четкое описание того, что вы хотите

**Проблема, которую это решает**
Зачем нужна эта функция

**Предложенное решение**
Как это должно работать

**Альтернативы**
Другие варианты решения

**Дополнительный контекст**
Mockups, примеры, ссылки
```

---

## ❓ Вопросы

Есть вопросы? Мы здесь, чтобы помочь!

- 📧 Email: support@coparently.app
- 💬 Discord: [CoPlanly Community](https://discord.gg/coparently)
- 🐦 Twitter: [@CoPlanlyApp](https://twitter.com/CoPlanlyApp)

---

## 🙏 Спасибо!

Ваш вклад делает CoPlanly лучше для тысяч родителей по всему миру! ❤️

---

<div align="center">

**Happy Coding! 🚀**

[⬆ Наверх](#-contributing-to-coparently)

</div>

