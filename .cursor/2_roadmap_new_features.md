# 🚀 CoParently Roadmap: Новые функции и возможности

**Цель**: Расширить функциональность приложения, добавив востребованные возможности для улучшения управления совместным воспитанием

**Приоритет**: High - новые функции для увеличения вовлеченности и удержания пользователей

---

## 📅 День 1: Внутриприложенный чат и коммуникации

### 1.1. Система сообщений между родителями

**Проблема**:
- Отсутствует возможность быстрой коммуникации по вопросам воспитания
- Родители вынуждены использовать внешние мессенджеры

**Решение**:
```kotlin
// domain/model/Message.kt
data class Message(
    val id: String = "",
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val messageType: MessageType = MessageType.TEXT,
    val attachments: List<Attachment> = emptyList(),
    val isRead: Boolean = false,
    val replyToMessageId: String? = null
)

enum class MessageType {
    TEXT, IMAGE, VOICE, EVENT_LINK
}

data class Conversation(
    val id: String,
    val participants: List<String>,
    val title: String,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// data/repository/MessageRepository.kt
interface MessageRepository {
    fun getConversations(userId: String): Flow<List<Conversation>>
    fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(message: Message): Result<Unit>
    suspend fun markAsRead(conversationId: String, userId: String): Result<Unit>
}

// presentation/chat/ChatScreen.kt
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ChatTopBar(
                conversation = viewModel.conversation,
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages list
            MessagesList(
                messages = messages,
                modifier = Modifier.weight(1f)
            )

            // Message input
            MessageInput(
                onSendMessage = { content ->
                    viewModel.sendMessage(content)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

**Преимущества**:
- 💬 Прямая коммуникация между родителями
- 📱 Интеграция с событиями календаря
- 🔔 Push-уведомления о новых сообщениях
- 📎 Поддержка вложений (фото, документы)

---

### 1.2. Шаблоны сообщений для типичных ситуаций

**Проблема**:
- Родители тратят время на написание похожих сообщений

**Решение**:
```kotlin
// domain/model/MessageTemplate.kt
data class MessageTemplate(
    val id: String,
    val category: TemplateCategory,
    val title: String,
    val content: String,
    val placeholders: List<String> = emptyList()
)

enum class TemplateCategory {
    PICKUP_DROP, ILLNESS, SCHOOL_EVENTS, HOLIDAYS, CONFLICT_RESOLUTION
}

// presentation/chat/MessageTemplates.kt
@Composable
fun MessageTemplatesBottomSheet(
    onTemplateSelected: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val templates by remember {
        mutableStateOf(getDefaultTemplates())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Message Templates",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn {
                items(templates.groupBy { it.category }) { (category, categoryTemplates) ->
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    categoryTemplates.forEach { template ->
                        TemplateItem(
                            template = template,
                            onClick = { onTemplateSelected(template) }
                        )
                    }
                }
            }
        }
    }
}

private fun getDefaultTemplates() = listOf(
    MessageTemplate(
        id = "pickup_delay",
        category = TemplateCategory.PICKUP_DROP,
        title = "Задержка с передачей ребенка",
        content = "Привет! Я немного задержусь с передачей [ребенка]. Буду через [время] минут. Извини за неудобство!"
    ),
    MessageTemplate(
        id = "doctor_visit",
        category = TemplateCategory.ILLNESS,
        title = "Визит к врачу",
        content = "[Ребенок] сегодня был у врача по поводу [жалоба]. Диагноз: [диагноз]. Нужно [рекомендации врача]."
    )
)
```

**Преимущества**:
- ⚡ Быстрое создание сообщений
- 🎯 Шаблоны для типичных ситуаций
- 🔧 Настраиваемые плейсхолдеры

---

## 📅 День 2: Трекер расходов и бюджета

### 2.1. Система учета расходов на ребенка

**Проблема**:
- Отсутствует учет расходов на ребенка
- Трудно отслеживать кто и сколько потратил

**Решение**:
```kotlin
// domain/model/Expense.kt
data class Expense(
    val id: String = "",
    val childId: String? = null, // null для общих расходов
    val title: String,
    val amount: Double,
    val currency: String = "USD",
    val category: ExpenseCategory,
    val paidBy: String, // userId родителя
    val splitBetween: List<String> = emptyList(), // userIds
    val date: LocalDate = LocalDate.now(),
    val receiptUrl: String? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ExpenseCategory {
    EDUCATION, MEDICAL, CLOTHING, FOOD, ACTIVITIES,
    TRANSPORTATION, TOYS, HOUSEHOLD, OTHER
}

// data/repository/ExpenseRepository.kt
interface ExpenseRepository {
    fun getExpensesForChild(childId: String): Flow<List<Expense>>
    fun getExpensesForPeriod(start: LocalDate, end: LocalDate): Flow<List<Expense>>
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>>
    suspend fun addExpense(expense: Expense): Result<Unit>
    suspend fun updateExpense(expense: Expense): Result<Unit>
    suspend fun deleteExpense(expenseId: String): Result<Unit>
}

// presentation/expenses/ExpenseScreen.kt
@Composable
fun ExpenseScreen(
    childId: String? = null,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val expenses by viewModel.expenses.collectAsState()
    val summary by viewModel.expenseSummary.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    IconButton(onClick = { /* show filters */ }) {
                        Icon(Icons.Default.FilterList, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* navigate to add expense */ }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary cards
            ExpenseSummaryCards(summary = summary)

            // Expenses list
            ExpenseList(
                expenses = expenses,
                onExpenseClick = { /* navigate to detail */ },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

**Преимущества**:
- 💰 Отслеживание расходов на ребенка
- 📊 Автоматический расчет долей
- 📈 Аналитика расходов по категориям
- 📷 Фото чеков и квитанций

---

### 2.2. Бюджетное планирование и оповещения

**Проблема**:
- Нет контроля за бюджетом на ребенка
- Неожиданные крупные расходы

**Решение**:
```kotlin
// domain/model/Budget.kt
data class Budget(
    val id: String = "",
    val childId: String? = null,
    val category: ExpenseCategory,
    val monthlyLimit: Double,
    val currency: String = "USD",
    val alertThreshold: Double = 0.8, // 80% of limit
    val isActive: Boolean = true
)

data class BudgetAlert(
    val budgetId: String,
    val currentSpent: Double,
    val limit: Double,
    val percentage: Double,
    val category: ExpenseCategory
)

// presentation/expenses/BudgetScreen.kt
@Composable
fun BudgetScreen(
    childId: String? = null,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsState()
    val alerts by viewModel.activeAlerts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Active alerts
        if (alerts.isNotEmpty()) {
            AlertSection(alerts = alerts)
        }

        // Budget list
        LazyColumn {
            items(budgets) { budget ->
                BudgetItem(
                    budget = budget,
                    spentAmount = viewModel.getSpentForBudget(budget.id),
                    onEdit = { /* navigate to edit */ }
                )
            }
        }
    }
}

@Composable
fun BudgetItem(
    budget: Budget,
    spentAmount: Double,
    onEdit: () -> Unit
) {
    val progress = (spentAmount / budget.monthlyLimit).coerceIn(0.0, 1.0)
    val color = when {
        progress >= 1.0 -> Color.Red
        progress >= budget.alertThreshold -> Color.Yellow
        else -> Color.Green
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budget.category.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${spentAmount.formatCurrency()}/${budget.monthlyLimit.formatCurrency()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LinearProgressIndicator(
                progress = progress.toFloat(),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Text(
                text = "${(progress * 100).roundToInt()}% used",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}
```

**Преимущества**:
- 🎯 Контроль бюджета по категориям
- 🚨 Предупреждения о превышении лимитов
- 📊 Визуализация расходов
- 💡 Планирование крупных покупок

---

## 📅 День 3: Медицинские записи и здоровье

### 3.1. Электронный медицинский дневник

**Проблема**:
- Разрозненная информация о здоровье ребенка
- Трудно отслеживать прививки и осмотры

**Решение**:
```kotlin
// domain/model/MedicalRecord.kt
data class MedicalRecord(
    val id: String = "",
    val childId: String,
    val recordType: MedicalRecordType,
    val title: String,
    val description: String? = null,
    val date: LocalDate,
    val doctorName: String? = null,
    val clinicName: String? = null,
    val diagnosis: String? = null,
    val treatment: String? = null,
    val medications: List<Medication> = emptyList(),
    val attachments: List<String> = emptyList(), // URLs to photos/documents
    val followUpDate: LocalDate? = null,
    val notes: String? = null
)

enum class MedicalRecordType {
    VISIT, VACCINATION, ILLNESS, ALLERGY_UPDATE,
    MEDICATION_CHANGE, EMERGENCY, CHECKUP
}

data class Medication(
    val name: String,
    val dosage: String,
    val frequency: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val notes: String? = null
)

// presentation/medical/MedicalScreen.kt
@Composable
fun MedicalScreen(
    childId: String,
    viewModel: MedicalViewModel = hiltViewModel()
) {
    val records by viewModel.medicalRecords.collectAsState()
    val upcomingEvents by viewModel.upcomingMedicalEvents.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Medical Records") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* add record */ }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Upcoming events
            if (upcomingEvents.isNotEmpty()) {
                UpcomingMedicalEvents(events = upcomingEvents)
            }

            // Records timeline
            MedicalRecordsTimeline(
                records = records.sortedByDescending { it.date },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

**Преимущества**:
- 🏥 Полная история медицинских записей
- 💉 Отслеживание прививок и осмотров
- 📅 Напоминания о приемах
- 📎 Хранение медицинских документов

---

### 3.2. Система аллергий и ограничений

**Проблема**:
- Критично важная информация может быть забыта
- Нет быстрого доступа к аллергиям в экстренных ситуациях

**Решение**:
```kotlin
// domain/model/Allergy.kt
data class Allergy(
    val id: String = "",
    val childId: String,
    val allergen: String,
    val severity: AllergySeverity,
    val symptoms: String,
    val firstReactionDate: LocalDate? = null,
    val treatment: String? = null,
    val emergencyContacts: List<String> = emptyList(),
    val notes: String? = null
)

enum class AllergySeverity {
    MILD, MODERATE, SEVERE, LIFE_THREATENING
}

// presentation/medical/AllergyCard.kt
@Composable
fun AllergyCard(
    allergy: Allergy,
    modifier: Modifier = Modifier
) {
    val severityColor = when (allergy.severity) {
        AllergySeverity.MILD -> Color.Yellow
        AllergySeverity.MODERATE -> Color.Orange
        AllergySeverity.SEVERE -> Color.Red
        AllergySeverity.LIFE_THREATENING -> Color(0xFF8B0000) // Dark Red
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        ),
        border = BorderStroke(2.dp, severityColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = allergy.allergen,
                    style = MaterialTheme.typography.headlineSmall
                )

                Chip(
                    text = allergy.severity.name,
                    color = severityColor
                )
            }

            if (allergy.symptoms.isNotBlank()) {
                Text(
                    text = "Symptoms: ${allergy.symptoms}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (allergy.treatment?.isNotBlank() == true) {
                Text(
                    text = "Treatment: ${allergy.treatment}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Emergency access screen
@Composable
fun EmergencyMedicalInfo(
    childId: String,
    viewModel: MedicalViewModel = hiltViewModel()
) {
    val child by viewModel.child.collectAsState()
    val allergies by viewModel.allergies.collectAsState()
    val emergencyContacts by viewModel.emergencyContacts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Child info header
        EmergencyHeader(child = child)

        // Critical allergies
        if (allergies.any { it.severity == AllergySeverity.SEVERE || it.severity == AllergySeverity.LIFE_THREATENING }) {
            Text(
                text = "CRITICAL ALLERGIES",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            allergies.filter {
                it.severity == AllergySeverity.SEVERE || it.severity == AllergySeverity.LIFE_THREATENING
            }.forEach { allergy ->
                AllergyCard(allergy = allergy)
            }
        }

        // Emergency contacts
        EmergencyContactsList(contacts = emergencyContacts)
    }
}
```

**Преимущества**:
- 🚨 Быстрый доступ к критической информации
- 🏥 Цветовое кодирование по severity
- 📞 Экстренные контакты на одном экране
- 📱 Lock screen widget для быстрого доступа

---

## 📅 День 4: Образовательный трекер

### 4.1. Отслеживание школьной успеваемости

**Проблема**:
- Родители не всегда в курсе школьных достижений
- Трудно отслеживать прогресс по предметам

**Решение**:
```kotlin
// domain/model/Grade.kt
data class Grade(
    val id: String = "",
    val childId: String,
    val subject: String,
    val grade: String, // A, B, C, D, F or numeric
    val gradeScale: GradeScale, // LETTER, PERCENTAGE, GPA
    val date: LocalDate,
    val teacher: String? = null,
    val assignment: String? = null,
    val comments: String? = null,
    val weight: Double = 1.0 // for weighted averages
)

enum class GradeScale {
    LETTER, PERCENTAGE, GPA_4, GPA_5
}

// domain/model/AcademicPeriod.kt
data class AcademicPeriod(
    val id: String = "",
    val childId: String,
    val name: String, // "Fall 2024", "Q1 2024"
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isCurrent: Boolean = false
)

// presentation/education/GradesScreen.kt
@Composable
fun GradesScreen(
    childId: String,
    viewModel: EducationViewModel = hiltViewModel()
) {
    val currentPeriod by viewModel.currentPeriod.collectAsState()
    val grades by viewModel.grades.collectAsState()
    val gradeSummary by viewModel.gradeSummary.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Period selector
        AcademicPeriodSelector(
            currentPeriod = currentPeriod,
            periods = viewModel.periods.collectAsState().value,
            onPeriodSelected = { viewModel.selectPeriod(it) }
        )

        // Grade summary cards
        GradeSummaryCards(summary = gradeSummary)

        // Grades list
        GradesList(
            grades = grades.sortedByDescending { it.date },
            onGradeClick = { /* show details */ }
        )
    }
}

@Composable
fun GradeSummaryCards(summary: Map<String, GradeSummary>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(summary.entries.toList()) { (subject, gradeSummary) ->
            Card(
                modifier = Modifier.width(120.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = gradeSummary.averageGrade,
                        style = MaterialTheme.typography.headlineMedium,
                        color = gradeSummary.color
                    )

                    LinearProgressIndicator(
                        progress = gradeSummary.progress,
                        color = gradeSummary.color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
```

**Преимущества**:
- 📚 Отслеживание успеваемости по предметам
- 📊 Визуализация прогресса
- 👨‍🏫 Информация об учителях и заданиях
- 📈 Динамика успеваемости со временем

---

### 4.2. Школьное расписание и события

**Проблема**:
- Родители не знают о школьных мероприятиях
- Трудно координировать участие в школьных событиях

**Решение**:
```kotlin
// domain/model/SchoolEvent.kt
data class SchoolEvent(
    val id: String = "",
    val childId: String,
    val title: String,
    val description: String? = null,
    val eventType: SchoolEventType,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime? = null,
    val location: String? = null,
    val teacher: String? = null,
    val isRequired: Boolean = false,
    val rsvpRequired: Boolean = false,
    val rsvpStatus: RSVPStatus? = null,
    val notes: String? = null
)

enum class SchoolEventType {
    PARENT_TEACHER_CONFERENCE, SCHOOL_PLAY, FIELD_TRIP,
    SPORTS_EVENT, SCHOOL_BOARD_MEETING, HOLIDAY_PROGRAM,
    CLASS_PARTY, OTHER
}

enum class RSVPStatus {
    ATTENDING, NOT_ATTENDING, MAYBE, PENDING
}

// presentation/education/SchoolEventsScreen.kt
@Composable
fun SchoolEventsScreen(
    childId: String,
    viewModel: EducationViewModel = hiltViewModel()
) {
    val upcomingEvents by viewModel.upcomingSchoolEvents.collectAsState()
    val pastEvents by viewModel.pastSchoolEvents.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = 0) {
            Tab(
                selected = true,
                onClick = { },
                text = { Text("Upcoming") }
            )
            Tab(
                selected = false,
                onClick = { },
                text = { Text("Past") }
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(upcomingEvents) { event ->
                SchoolEventCard(
                    event = event,
                    onRSVP = { status -> viewModel.updateRSVP(event.id, status) },
                    onAddToCalendar = { viewModel.addToCalendar(event) }
                )
            }
        }
    }
}

@Composable
fun SchoolEventCard(
    event: SchoolEvent,
    onRSVP: (RSVPStatus) -> Unit,
    onAddToCalendar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (event.description?.isNotBlank() == true) {
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (event.isRequired) {
                    Chip(
                        text = "Required",
                        color = Color.Red.copy(alpha = 0.1f),
                        textColor = Color.Red
                    )
                }
            }

            // Date and time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatEventDateTime(event),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // RSVP section
            if (event.rsvpRequired) {
                RSVPSection(
                    currentStatus = event.rsvpStatus,
                    onRSVP = onRSVP,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAddToCalendar) {
                    Text("Add to Calendar")
                }
            }
        }
    }
}
```

**Преимущества**:
- 🏫 Синхронизация с школьным календарем
- 📝 RSVP система для мероприятий
- 👨‍👩‍👧‍👦 Координация участия родителей
- 📅 Интеграция с основным календарем

---

## 📅 День 5: Виджеты и быстрый доступ

### 5.1. Home screen widgets

**Проблема**:
- Пользователи забывают проверять календарь
- Нужно быстрое отображение важной информации

**Решение**:
```kotlin
// presentation/widgets/TodayEventsWidget.kt
class TodayEventsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TodayEventsWidgetContent()
        }
    }
}

@Composable
fun TodayEventsWidgetContent() {
    val context = LocalContext.current
    val viewModel: WidgetViewModel = hiltViewModel()
    val todayEvents by viewModel.todayEvents.collectAsState()
    val custodyToday by viewModel.custodyToday.collectAsState()

    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .padding(8.dp)
        ) {
            // Header with custody info
            Text(
                text = "Today",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            custodyToday?.let { custody ->
                Text(
                    text = "With ${custody.parentName}",
                    style = TextStyle(fontSize = 12.sp),
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }

            // Events list (limited to 3)
            todayEvents.take(3).forEach { event ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${event.startTime} ${event.title}",
                        style = TextStyle(fontSize = 12.sp),
                        modifier = GlanceModifier.padding(start = 8.dp)
                    )
                }
            }

            // Tap to open app
            ActionButton(
                text = "Open CoParently",
                onClick = actionStartActivity(context, MainActivity::class.java)
            )
        }
    }
}

// Emergency widget
class EmergencyWidget : GlanceAppWidget() {

    @Composable
    override fun Content() {
        val context = LocalContext.current

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.Red.copy(alpha = 0.1f)))
                    .padding(8.dp)
            ) {
                Text(
                    text = "🚨 EMERGENCY",
                    style = TextStyle(
                        color = ColorProvider(Color.Red),
                        fontWeight = FontWeight.Bold
                    )
                )

                // Critical allergies count
                Text(
                    text = "Critical allergies: 2",
                    style = TextStyle(fontSize = 12.sp)
                )

                // Emergency contact
                Text(
                    text = "Emergency: 911",
                    style = TextStyle(fontSize = 12.sp)
                )

                ActionButton(
                    text = "View Details",
                    onClick = actionStartActivity(context, EmergencyActivity::class.java)
                )
            }
        }
    }
}
```

**Преимущества**:
- 🏠 Виджеты на home screen
- ⚡ Быстрый доступ к информации
- 🚨 Экстренные виджеты
- 📱 Разные размеры виджетов

---

### 5.2. Quick Actions через App Shortcuts

**Проблема**:
- Много шагов для создания события
- Пользователи хотят быстрого доступа

**Решение**:
```kotlin
// utils/AppShortcuts.kt
object AppShortcuts {

    fun setupShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)

        val shortcuts = listOf(
            ShortcutInfo.Builder(context, "new_event")
                .setShortLabel("New Event")
                .setLongLabel("Create new calendar event")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_add))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("coparently://new-event")
                    }
                )
                .build(),

            ShortcutInfo.Builder(context, "today_events")
                .setShortLabel("Today's Events")
                .setLongLabel("View today's schedule")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_today))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("coparently://today")
                    }
                )
                .build(),

            ShortcutInfo.Builder(context, "emergency")
                .setShortLabel("Emergency Info")
                .setLongLabel("Access critical medical information")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_emergency))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("coparently://emergency")
                    }
                )
                .build()
        )

        shortcutManager.dynamicShortcuts = shortcuts
    }
}

// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Handle shortcut intents
    when (intent?.data?.host) {
        "new-event" -> navigateToAddEvent()
        "today" -> navigateToTodayView()
        "emergency" -> navigateToEmergencyScreen()
    }

    // Setup shortcuts
    AppShortcuts.setupShortcuts(this)
}
```

**Преимущества**:
- ⚡ Быстрый доступ без открытия приложения
- 📱 Android shortcuts integration
- 🎯 Часто используемые действия
- 🔄 Динамические shortcuts

---

## 📊 Ожидаемые результаты после новых функций

### Метрики улучшения:
- **Вовлеченность пользователей**: +60% (новые возможности использования)
- **Среднее время сессии**: +40% (больше функций для изучения)
- **Удержание пользователей**: +25% (больше ценности)
- **Количество событий**: +50% (новые типы контента)
- **Монетизация**: +100% (премиум функции)

### Бизнес-ценность:
- 💰 **Расширенная аудитория**: Привлечение родителей с более сложными нуждами
- 🎯 **Дифференциация**: Уникальные функции недоступные в других приложениях
- 📈 **Монетизация**: Премиум подписки для расширенных функций
- 🤝 **Лояльность**: Более глубокая интеграция в жизнь семьи

---

## 🎯 Приоритеты реализации

### High Priority (высокий приоритет):
1. ✅ **День 1**: Чат и коммуникации (высокий retention impact)
2. ✅ **День 3**: Медицинские записи (критично для безопасности)

### Medium Priority (средний приоритет):
3. ✅ **День 2**: Трекер расходов (финансовая прозрачность)
4. ✅ **День 4**: Образовательный трекер (школьные родители)

### Low Priority (низкий приоритет):
5. ✅ **День 5**: Виджеты и shortcuts (удобство использования)

---

## 🔧 Технические требования

- **Database**: Дополнительные таблицы для новых сущностей
- **API**: Firebase для хранения новых данных
- **Permissions**: Camera (фото), Storage (документы)
- **Offline**: Синхронизация всех новых данных
- **Security**: Шифрование медицинских и финансовых данных

---

💡 **Стратегия монетизации**: Новые функции как премиум возможности с постепенным переходом к freemium модели.</content>
</xai:function_call">﻿

Assistant: Теперь создам третий файл - 3_roadmap_technical_improvements.md с техническими улучшениями. Сначала обновлю TODO.  Сначала обновлю TODO.

<xai:function_call name="todo_write">
<parameter name="merge">true
