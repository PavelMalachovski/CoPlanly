# 🚀 CoPlanly Roadmap: AI Интеграция

**Цель**: Интегрировать AI возможности для создания интеллектуального помощника по воспитанию

**Текущий статус**: Нет AI интеграции. Фокус на практических AI функциях для улучшения UX.

---

## 📅 День 1: Smart Scheduling Assistant

### 1.1. AI-powered conflict resolution

**Проблема**:
- Родители тратят время на согласование расписаний
- Пропускают конфликты в расписании

**Решение**:
```kotlin
// domain/ai/SmartSchedulingUseCase.kt
class SmartSchedulingUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val aiService: AIService,
    private val conflictDetector: ConflictDetector
) {
    suspend fun findOptimalTimeSlot(
        eventRequest: EventRequest,
        parentCalendars: List<CalendarData>
    ): List<TimeSlotSuggestion> {

        // Найти все конфликты
        val conflicts = conflictDetector.findConflicts(
            eventRequest.duration,
            eventRequest.preferredTimes,
            parentCalendars
        )

        if (conflicts.isEmpty()) {
            // Нет конфликтов - вернуть предпочтительные слоты
            return eventRequest.preferredTimes.map { timeSlot ->
                TimeSlotSuggestion(
                    startTime = timeSlot,
                    endTime = timeSlot.plus(eventRequest.duration),
                    score = 1.0,
                    reasoning = "Perfect time slot - no conflicts"
                )
            }
        }

        // Использовать AI для поиска оптимальных альтернатив
        val prompt = buildSchedulingPrompt(eventRequest, conflicts, parentCalendars)

        return aiService.generateTimeSlotSuggestions(prompt)
    }

    private fun buildSchedulingPrompt(
        request: EventRequest,
        conflicts: List<Conflict>,
        calendars: List<CalendarData>
    ): String {
        return """
            Analyze the following scheduling request and find optimal time slots:

            Event: ${request.title}
            Duration: ${request.duration}
            Preferred times: ${request.preferredTimes.joinToString { it.toString() }}
            Event type: ${request.eventType}

            Existing conflicts:
            ${conflicts.joinToString("\n") { "- ${it.description}" }}

            Parent schedules:
            ${calendars.joinToString("\n") { calendar ->
                "Parent: ${calendar.parentName}\n${calendar.events.joinToString("\n") { "  - ${it.title} at ${it.startTime}" }}"
            }}

            Suggest 3 optimal time slots that minimize conflicts and consider:
            1. Work schedules and typical availability
            2. Child's routine and energy levels
            3. Travel time between activities
            4. Family preferences and patterns

            Return in JSON format with score (0-1) and reasoning for each suggestion.
        """.trimIndent()
    }
}

data class EventRequest(
    val title: String,
    val duration: Duration,
    val preferredTimes: List<LocalDateTime>,
    val eventType: String,
    val childAge: Int? = null,
    val specialRequirements: List<String> = emptyList()
)

data class TimeSlotSuggestion(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val score: Double, // 0-1, how optimal
    val reasoning: String,
    val alternativeDates: List<LocalDateTime> = emptyList()
)

// domain/ai/ConflictDetector.kt
class ConflictDetector @Inject constructor() {

    fun findConflicts(
        duration: Duration,
        preferredTimes: List<LocalDateTime>,
        calendars: List<CalendarData>
    ): List<Conflict> {

        val conflicts = mutableListOf<Conflict>()

        preferredTimes.forEach { preferredTime ->
            val endTime = preferredTime.plus(duration)

            calendars.forEach { calendar ->
                val overlappingEvents = calendar.events.filter { event ->
                    event.startDateTime.isBefore(endTime) &&
                    event.endDateTime.isAfter(preferredTime)
                }

                overlappingEvents.forEach { event ->
                    conflicts.add(Conflict(
                        timeSlot = preferredTime to endTime,
                        conflictingEvent = event,
                        parentName = calendar.parentName,
                        severity = calculateConflictSeverity(event, duration),
                        description = "${calendar.parentName} has ${event.title}"
                    ))
                }
            }
        }

        return conflicts.distinctBy { it.conflictingEvent.id }
    }

    private fun calculateConflictSeverity(event: Event, requestedDuration: Duration): ConflictSeverity {
        return when {
            event.eventType in listOf("work", "meeting") -> ConflictSeverity.HIGH
            event.eventType in listOf("doctor", "school") -> ConflictSeverity.MEDIUM
            event.endDateTime.minusHours(2).isBefore(event.startDateTime) -> ConflictSeverity.LOW
            else -> ConflictSeverity.LOW
        }
    }
}

enum class ConflictSeverity { LOW, MEDIUM, HIGH }

data class Conflict(
    val timeSlot: Pair<LocalDateTime, LocalDateTime>,
    val conflictingEvent: Event,
    val parentName: String,
    val severity: ConflictSeverity,
    val description: String
)

// data/remote/ai/AIService.kt
interface AIService {
    suspend fun generateTimeSlotSuggestions(prompt: String): List<TimeSlotSuggestion>
    suspend fun analyzeCalendarPatterns(calendars: List<CalendarData>): CalendarInsights
    suspend fun suggestEventImprovements(event: Event, context: EventContext): List<EventSuggestion>
}

class OpenAIService @Inject constructor(
    private val apiKey: String,
    private val httpClient: OkHttpClient
) : AIService {

    override suspend fun generateTimeSlotSuggestions(prompt: String): List<TimeSlotSuggestion> {
        val request = ChatCompletionRequest(
            model = "gpt-4",
            messages = listOf(
                ChatMessage(role = "system", content = "You are a family scheduling assistant..."),
                ChatMessage(role = "user", content = prompt)
            ),
            temperature = 0.7,
            maxTokens = 1000
        )

        val response = makeApiCall(request)
        return parseSuggestions(response)
    }
}
```

**Преимущества**:
- 🤖 Автоматическое разрешение конфликтов
- 📅 Интеллектуальные предложения времени
- 👨‍👩‍👧‍👦 Учет расписаний обоих родителей
- 🎯 Персонализированные рекомендации

---

### 1.2. Proactive conflict alerts

**Проблема**:
- Родители узнают о конфликтах слишком поздно
- Проблемы с координацией

**Решение**:
```kotlin
// domain/ai/ProactiveConflictMonitor.kt
class ProactiveConflictMonitor @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val notificationManager: NotificationManager,
    private val aiService: AIService,
    private val workManager: WorkManager
) {

    fun scheduleConflictMonitoring() {
        val monitoringRequest = PeriodicWorkRequestBuilder<ConflictMonitoringWorker>(
            2, TimeUnit.HOURS // Check every 2 hours
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "conflict_monitoring",
            ExistingPeriodicWorkPolicy.REPLACE,
            monitoringRequest
        )
    }

    fun checkForUpcomingConflicts(): List<UpcomingConflict> {
        val now = LocalDateTime.now()
        val checkUntil = now.plusDays(7) // Look ahead 7 days

        val parentCalendars = calendarRepository.getBothParentsCalendars()
        val conflicts = findUpcomingConflicts(parentCalendars, now, checkUntil)

        return conflicts.filter { it.severity >= ConflictSeverity.MEDIUM }
    }

    private suspend fun findUpcomingConflicts(
        calendars: List<CalendarData>,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<UpcomingConflict> {

        val conflicts = mutableListOf<UpcomingConflict>()

        // Анализировать паттерны и предсказывать потенциальные конфликты
        val insights = aiService.analyzeCalendarPatterns(calendars)

        insights.predictedConflicts.forEach { predicted ->
            if (predicted.confidence > 0.7) { // High confidence
                conflicts.add(UpcomingConflict(
                    title = "Potential ${predicted.conflictType} conflict",
                    description = predicted.description,
                    suggestedSolutions = predicted.solutions,
                    timeUntil = Duration.between(from, predicted.expectedTime),
                    severity = ConflictSeverity.fromConfidence(predicted.confidence)
                ))
            }
        }

        return conflicts
    }
}

data class UpcomingConflict(
    val title: String,
    val description: String,
    val suggestedSolutions: List<String>,
    val timeUntil: Duration,
    val severity: ConflictSeverity
)

// domain/ai/CalendarInsights.kt
data class CalendarInsights(
    val busyPeriods: List<BusyPeriod>,
    val freeTimePatterns: List<FreeTimePattern>,
    val predictedConflicts: List<PredictedConflict>,
    val schedulingEfficiency: Double // 0-1 score
)

data class PredictedConflict(
    val conflictType: String, // "double_booking", "travel_conflict", etc.
    val expectedTime: LocalDateTime,
    val confidence: Double, // 0-1
    val description: String,
    val solutions: List<String>
)

// presentation/ai/ConflictAlertCard.kt
@Composable
fun ConflictAlertCard(
    conflict: UpcomingConflict,
    onViewSolutions: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (conflict.severity) {
                ConflictSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
                ConflictSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                ConflictSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            when (conflict.severity) {
                ConflictSeverity.HIGH -> MaterialTheme.colorScheme.error
                ConflictSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiary
                ConflictSeverity.LOW -> MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conflict.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = conflict.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = formatTimeUntil(conflict.timeUntil),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            if (conflict.suggestedSolutions.isNotEmpty()) {
                OutlinedButton(
                    onClick = onViewSolutions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("View Solutions (${conflict.suggestedSolutions.size})")
                }
            }
        }
    }
}
```

**Преимущества**:
- 🚨 Проактивные предупреждения
- 🧠 AI предсказания конфликтов
- 💡 Автоматические предложения решений
- 📱 Push-уведомления

---

## 📅 День 2: Natural Language Event Creation

### 2.1. Voice-to-event conversion

**Проблема**:
- Создание событий через формы занимает время
- Пользователи хотят быстрый ввод

**Решение**:
```kotlin
// domain/ai/NaturalLanguageEventParser.kt
class NaturalLanguageEventParser @Inject constructor(
    private val aiService: AIService,
    private val calendarRepository: CalendarRepository,
    private val eventValidator: EventValidator
) {

    suspend fun parseEventFromText(text: String, context: ParsingContext): ParsedEventResult {
        val prompt = buildParsingPrompt(text, context)

        return try {
            val aiResponse = aiService.parseNaturalLanguage(prompt)
            val parsedEvent = parseAIResponse(aiResponse)

            // Валидация распознанного события
            val validation = eventValidator.validate(parsedEvent.toEvent())

            ParsedEventResult.Success(
                event = parsedEvent,
                confidence = aiResponse.confidence,
                validationIssues = validation.errors
            )
        } catch (e: Exception) {
            ParsedEventResult.Error("Failed to parse event: ${e.message}")
        }
    }

    private fun buildParsingPrompt(text: String, context: ParsingContext): String {
        return """
            Parse the following natural language text into a structured calendar event.

            Text: "$text"

            Context:
            - Current date: ${context.currentDate}
            - User timezone: ${context.timezone}
            - Recent events: ${context.recentEvents.joinToString { it.title }}
            - Common locations: ${context.commonLocations.joinToString()}

            Extract:
            1. Event title
            2. Date and time (if not specified, infer from context)
            3. Duration (if not specified, use defaults)
            4. Location (if mentioned)
            5. Event type (school, medical, sports, etc.)
            6. Parent assignment (mom/dad/both)
            7. Recurrence pattern (if mentioned)
            8. Additional notes

            Consider common patterns:
            - "Soccer practice tomorrow at 3pm" -> Sports event
            - "Doctor appointment next Tuesday" -> Medical event
            - "School pickup Friday" -> School event
            - "Dentist 2pm Wednesday" -> Medical event

            Return JSON with confidence score (0-1) and extracted data.
        """.trimIndent()
    }
}

data class ParsingContext(
    val currentDate: LocalDate = LocalDate.now(),
    val timezone: String = "UTC",
    val recentEvents: List<Event> = emptyList(),
    val commonLocations: List<String> = emptyList()
)

sealed class ParsedEventResult {
    data class Success(
        val event: ParsedEvent,
        val confidence: Double,
        val validationIssues: List<String> = emptyList(),
        val suggestions: List<String> = emptyList()
    ) : ParsedEventResult()

    data class Error(val message: String) : ParsedEventResult()
}

data class ParsedEvent(
    val title: String,
    val dateTime: LocalDateTime? = null,
    val duration: Duration? = null,
    val location: String? = null,
    val eventType: String? = null,
    val parentOwner: String? = null,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null,
    val notes: String? = null
) {
    fun toEvent(): Event {
        return Event(
            id = "", // Will be generated
            title = title,
            startDateTime = dateTime ?: LocalDateTime.now(),
            endDateTime = dateTime?.plus(duration ?: Duration.ofHours(1)),
            eventType = eventType ?: "general",
            parentOwner = parentOwner ?: "mom",
            isRecurring = isRecurring,
            recurrencePattern = recurrencePattern,
            description = notes,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}

// presentation/ai/VoiceEventCreation.kt
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceEventCreation(
    onEventParsed: (ParsedEvent) -> Unit,
    onCancel: () -> Unit
) {
    val speechRecognizer = rememberSpeechRecognizer()
    val parser = hiltViewModel<NaturalLanguageEventParser>()

    var isListening by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    var parsingResult by remember { mutableStateOf<ParsedEventResult?>(null) }

    val microphonePermission = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Voice recording button
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    if (isListening)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable {
                    if (microphonePermission.status.isGranted) {
                        if (isListening) {
                            speechRecognizer.stopListening()
                        } else {
                            speechRecognizer.startListening { text ->
                                transcribedText = text
                                isListening = false
                                // Auto-parse
                                scope.launch {
                                    parsingResult = parser.parseEventFromText(
                                        text,
                                        ParsingContext()
                                    )
                                }
                            }
                            isListening = true
                        }
                    } else {
                        microphonePermission.launchPermissionRequest()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop recording" else "Start recording",
                modifier = Modifier.size(48.dp),
                tint = if (isListening)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isListening) "Listening..." else "Tap to speak",
            style = MaterialTheme.typography.headlineSmall
        )

        // Transcribed text
        if (transcribedText.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = transcribedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Parsing result
        parsingResult?.let { result ->
            when (result) {
                is ParsedEventResult.Success -> {
                    ParsedEventConfirmation(
                        parsedEvent = result.event,
                        confidence = result.confidence,
                        issues = result.validationIssues,
                        onConfirm = { onEventParsed(result.event) },
                        onEdit = { /* Navigate to manual edit */ }
                    )
                }
                is ParsedEventResult.Error -> {
                    ErrorMessage(
                        message = result.message,
                        onRetry = {
                            scope.launch {
                                parsingResult = parser.parseEventFromText(
                                    transcribedText,
                                    ParsingContext()
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
```

**Преимущества**:
- 🎤 Голосовой ввод событий
- 🧠 AI понимание естественного языка
- ⚡ Быстрое создание событий
- 🔄 Автокоррекция и предложения

---

### 2.2. Smart event suggestions

**Проблема**:
- Пользователи забывают о регулярных событиях
- Трудно поддерживать последовательность

**Решение**:
```kotlin
// domain/ai/EventSuggestionEngine.kt
class EventSuggestionEngine @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val aiService: AIService,
    private val analyticsManager: AnalyticsManager
) {

    suspend fun generateEventSuggestions(
        context: SuggestionContext
    ): List<EventSuggestion> {

        val userHistory = calendarRepository.getUserEventHistory(context.userId, 90) // Last 90 days
        val currentSchedule = calendarRepository.getCurrentSchedule()

        val prompt = buildSuggestionPrompt(userHistory, currentSchedule, context)

        return aiService.generateEventSuggestions(prompt)
    }

    private fun buildSuggestionPrompt(
        history: List<Event>,
        currentSchedule: List<Event>,
        context: SuggestionContext
    ): String {
        return """
            Analyze user's event history and suggest new events they might want to add.

            User history (last 90 days):
            ${history.joinToString("\n") { "- ${it.title} (${it.eventType}) on ${it.startDateTime.dayOfWeek}" }}

            Current upcoming events:
            ${currentSchedule.joinToString("\n") { "- ${it.title} on ${it.startDateTime}" }}

            Context:
            - Current date: ${context.currentDate}
            - Child age: ${context.childAge}
            - Family location: ${context.location}
            - Weather: ${context.weather}

            Suggest events based on:
            1. Recurring patterns (weekly soccer practice, monthly doctor visits)
            2. Seasonal activities (back-to-school events, holiday activities)
            3. Age-appropriate activities for ${context.childAge} year old
            4. Location-based suggestions (${context.location})
            5. Weather-appropriate activities (${context.weather})

            Return 3-5 relevant suggestions with reasoning.
        """.trimIndent()
    }

    suspend fun learnFromUserActions(suggestion: EventSuggestion, action: UserAction) {
        // Машинное обучение на основе действий пользователя
        analyticsManager.logEventSuggestionAction(
            suggestionId = suggestion.id,
            action = action,
            context = suggestion.context
        )

        // Обновить модель предпочтений пользователя
        updateUserPreferences(suggestion, action)
    }
}

data class SuggestionContext(
    val userId: String,
    val currentDate: LocalDate = LocalDate.now(),
    val childAge: Int? = null,
    val location: String? = null,
    val weather: String? = null,
    val familySize: Int = 2
)

data class EventSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val suggestedDateTime: LocalDateTime,
    val eventType: String,
    val confidence: Double, // 0-1
    val reasoning: String,
    val category: SuggestionCategory
)

enum class SuggestionCategory {
    RECURRING, SEASONAL, AGE_APPROPRIATE, LOCATION_BASED, WEATHER_BASED
}

enum class UserAction {
    ACCEPTED, REJECTED, MODIFIED, IGNORED
}

// presentation/ai/EventSuggestions.kt
@Composable
fun EventSuggestions(
    suggestions: List<EventSuggestion>,
    onSuggestionAccepted: (EventSuggestion) -> Unit,
    onSuggestionRejected: (EventSuggestion) -> Unit,
    onSuggestionModified: (EventSuggestion) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                onAccept = { onSuggestionAccepted(suggestion) },
                onReject = { onSuggestionRejected(suggestion) },
                onModify = { onSuggestionModified(suggestion) }
            )
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: EventSuggestion,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onModify: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Confidence indicator
                    LinearProgressIndicator(
                        progress = suggestion.confidence.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(4.dp)
                    )

                    Text(
                        text = "${(suggestion.confidence * 100).roundToInt()}% match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Category chip
                SuggestionCategoryChip(suggestion.category)
            }

            // Reasoning
            Text(
                text = suggestion.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Not interested")
                }

                OutlinedButton(
                    onClick = onModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Modify")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Event")
                }
            }
        }
    }
}
```

**Преимущества**:
- 🧠 Персонализированные предложения
- 📈 Обучение на паттернах пользователя
- 🎯 Контекстные рекомендации
- 🔄 Постоянное улучшение

---

## 📅 День 3: Communication Intelligence

### 3.1. Tone analysis для сообщений

**Проблема**:
- Родители пишут эмоциональные сообщения
- Конфликты из-за недопонимания

**Решение**:
```kotlin
// domain/ai/CommunicationAnalyzer.kt
class CommunicationAnalyzer @Inject constructor(
    private val aiService: AIService,
    private val sentimentAnalyzer: SentimentAnalyzer
) {

    suspend fun analyzeMessageTone(message: String, context: MessageContext): ToneAnalysis {
        val sentiment = sentimentAnalyzer.analyzeSentiment(message)

        // Детальный анализ с AI
        val prompt = """
            Analyze the tone and emotional content of this co-parenting message:

            Message: "$message"

            Context:
            - Sender: ${context.senderName}
            - Recipient: ${context.recipientName}
            - Topic: ${context.topic ?: "General"}
            - Relationship history: ${context.relationshipHistory}

            Assess:
            1. Overall tone (positive/neutral/negative/aggressive)
            2. Emotional intensity (1-10 scale)
            3. Potential for misunderstanding
            4. Communication effectiveness
            5. Suggested improvements

            Consider co-parenting best practices:
            - Focus on child's needs
            - Use "I" statements
            - Avoid blame language
            - Be specific about requests

            Return analysis with improvement suggestions.
        """.trimIndent()

        val aiAnalysis = aiService.analyzeCommunication(prompt)

        return ToneAnalysis(
            sentiment = sentiment,
            tone = aiAnalysis.tone,
            intensity = aiAnalysis.intensity,
            issues = aiAnalysis.potentialIssues,
            suggestions = aiAnalysis.improvements,
            effectiveness = aiAnalysis.effectiveness
        )
    }

    suspend fun suggestImprovedMessage(originalMessage: String, analysis: ToneAnalysis): ImprovedMessage {
        if (analysis.effectiveness > 0.7) {
            return ImprovedMessage(originalMessage, emptyList())
        }

        val prompt = """
            Rewrite this co-parenting message to be more effective and appropriate:

            Original: "$originalMessage"

            Issues identified:
            ${analysis.issues.joinToString("\n")}

            Current tone: ${analysis.tone}
            Intensity: ${analysis.intensity}/10

            Rewrite focusing on:
            1. Child-centered language
            2. Clear, specific requests
            3. Neutral, professional tone
            4. Positive intent

            Provide 2-3 alternative versions.
        """.trimIndent()

        val suggestions = aiService.generateMessageRewrites(prompt)

        return ImprovedMessage(
            originalMessage = originalMessage,
            suggestions = suggestions
        )
    }
}

data class ToneAnalysis(
    val sentiment: SentimentResult,
    val tone: MessageTone,
    val intensity: Int, // 1-10
    val issues: List<String>,
    val suggestions: List<String>,
    val effectiveness: Double // 0-1
)

data class ImprovedMessage(
    val originalMessage: String,
    val suggestions: List<String>
)

enum class MessageTone {
    POSITIVE, NEUTRAL, CONCERNED, FRUSTRATED, AGGRESSIVE
}

data class MessageContext(
    val senderName: String,
    val recipientName: String,
    val topic: String? = null,
    val relationshipHistory: String = "neutral" // "positive", "neutral", "strained"
)

// presentation/ai/ToneAnalyzer.kt
@Composable
fun MessageToneAnalyzer(
    message: String,
    context: MessageContext,
    onMessageApproved: (String) -> Unit,
    onMessageRevised: (String) -> Unit
) {
    val analyzer = hiltViewModel<CommunicationAnalyzer>()
    var analysis by remember { mutableStateOf<ToneAnalysis?>(null) }
    var improvedSuggestions by remember { mutableStateOf<ImprovedMessage?>(null) }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            analysis = analyzer.analyzeMessageTone(message, context)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Message preview
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Analysis results
        analysis?.let { toneAnalysis ->
            ToneAnalysisCard(
                analysis = toneAnalysis,
                onImprove = {
                    scope.launch {
                        improvedSuggestions = analyzer.suggestImprovedMessage(message, toneAnalysis)
                    }
                }
            )
        }

        // Improvement suggestions
        improvedSuggestions?.let { improvements ->
            ImprovedMessageSuggestions(
                improvements = improvements,
                onSelectSuggestion = { selectedMessage ->
                    onMessageRevised(selectedMessage)
                }
            )
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { /* Go back to edit */ },
                modifier = Modifier.weight(1f)
            ) {
                Text("Edit")
            }

            Button(
                onClick = { onMessageApproved(message) },
                modifier = Modifier.weight(1f),
                enabled = analysis?.effectiveness ?: 0.0 > 0.6
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ToneAnalysisCard(
    analysis: ToneAnalysis,
    onImprove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                analysis.effectiveness > 0.7 -> MaterialTheme.colorScheme.primaryContainer
                analysis.effectiveness > 0.4 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Message Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Effectiveness indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        analysis.effectiveness > 0.7 -> Color.Green.copy(alpha = 0.2f)
                        analysis.effectiveness > 0.4 -> Color.Yellow.copy(alpha = 0.2f)
                        else -> Color.Red.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = "${(analysis.effectiveness * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Tone: ${analysis.tone.name.lowercase().capitalize()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Intensity: ${analysis.intensity}/10",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (analysis.issues.isNotEmpty()) {
                Text(
                    text = "Potential issues:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                analysis.issues.forEach { issue ->
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Button(
                    onClick = onImprove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Get Suggestions")
                }
            }
        }
    }
}
```

**Преимущества**:
- 💬 Анализ эмоционального тона
- 🛡️ Предотвращение конфликтов
- 💡 Предложения по улучшению коммуникации
- 📈 Лучшее взаимопонимание

---

### 3.2. Smart conversation summaries

**Проблема**:
- Родители забывают о предыдущих договоренностях
- Трудно отслеживать изменения в планах

**Решение**:
```kotlin
// domain/ai/ConversationSummarizer.kt
class ConversationSummarizer @Inject constructor(
    private val aiService: AIService,
    private val messageRepository: MessageRepository
) {

    suspend fun summarizeConversation(
        conversationId: String,
        since: LocalDateTime? = null
    ): ConversationSummary {

        val messages = messageRepository.getMessagesSince(conversationId, since ?: LocalDateTime.now().minusDays(7))

        if (messages.isEmpty()) {
            return ConversationSummary.Empty
        }

        val prompt = buildSummaryPrompt(messages)

        val aiSummary = aiService.generateConversationSummary(prompt)

        return ConversationSummary.Content(
            summary = aiSummary.summary,
            keyPoints = aiSummary.keyPoints,
            actionItems = aiSummary.actionItems,
            agreements = aiSummary.agreements,
            conflicts = aiSummary.conflicts,
            sentiment = aiSummary.overallSentiment,
            timeRange = messages.first().timestamp to messages.last().timestamp
        )
    }

    private fun buildSummaryPrompt(messages: List<Message>): String {
        val messageText = messages.joinToString("\n") { msg ->
            "${msg.senderName}: ${msg.content}"
        }

        return """
            Summarize this co-parenting conversation:

            Messages:
            $messageText

            Provide:
            1. Brief summary of discussion
            2. Key decisions/agreements made
            3. Action items (who needs to do what)
            4. Any conflicts or disagreements
            5. Overall tone/sentiment
            6. Important dates mentioned

            Focus on co-parenting topics like:
            - Child custody and visitation
            - School and education
            - Medical care and appointments
            - Expenses and financial matters
            - Communication preferences
        """.trimIndent()
    }

    suspend fun extractActionItems(conversationId: String): List<ActionItem> {
        val summary = summarizeConversation(conversationId)

        return when (summary) {
            is ConversationSummary.Content -> summary.actionItems.map { actionText ->
                ActionItem(
                    id = UUID.randomUUID().toString(),
                    description = actionText,
                    conversationId = conversationId,
                    status = ActionItemStatus.PENDING,
                    createdAt = LocalDateTime.now()
                )
            }
            else -> emptyList()
        }
    }
}

sealed class ConversationSummary {
    data object Empty : ConversationSummary()

    data class Content(
        val summary: String,
        val keyPoints: List<String>,
        val actionItems: List<String>,
        val agreements: List<String>,
        val conflicts: List<String>,
        val sentiment: MessageTone,
        val timeRange: Pair<LocalDateTime, LocalDateTime>
    ) : ConversationSummary()
}

data class ActionItem(
    val id: String,
    val description: String,
    val conversationId: String,
    val assignedTo: String? = null,
    val dueDate: LocalDateTime? = null,
    val status: ActionItemStatus = ActionItemStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ActionItemStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}

// presentation/ai/ConversationSummary.kt
@Composable
fun ConversationSummaryView(
    conversationId: String,
    onActionItemCompleted: (String) -> Unit
) {
    val summarizer = hiltViewModel<ConversationSummarizer>()
    var summary by remember { mutableStateOf<ConversationSummary?>(null) }
    var actionItems by remember { mutableStateOf<List<ActionItem>>(emptyList()) }

    LaunchedEffect(conversationId) {
        summary = summarizer.summarizeConversation(conversationId)
        actionItems = summarizer.extractActionItems(conversationId)
    }

    when (val currentSummary = summary) {
        null -> {
            LoadingState()
        }
        is ConversationSummary.Empty -> {
            EmptySummaryState()
        }
        is ConversationSummary.Content -> {
            SummaryContent(
                summary = currentSummary,
                actionItems = actionItems,
                onActionItemCompleted = onActionItemCompleted
            )
        }
    }
}

@Composable
fun SummaryContent(
    summary: ConversationSummary.Content,
    actionItems: List<ActionItem>,
    onActionItemCompleted: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Summary
        item {
            SummaryCard(summary = summary.summary)
        }

        // Key points
        if (summary.keyPoints.isNotEmpty()) {
            item {
                SectionHeader("Key Points")
            }
            items(summary.keyPoints) { point ->
                KeyPointItem(point = point)
            }
        }

        // Agreements
        if (summary.agreements.isNotEmpty()) {
            item {
                SectionHeader("Agreements")
            }
            items(summary.agreements) { agreement ->
                AgreementItem(agreement = agreement)
            }
        }

        // Action items
        if (actionItems.isNotEmpty()) {
            item {
                SectionHeader("Action Items")
            }
            items(actionItems) { actionItem ->
                ActionItemCard(
                    actionItem = actionItem,
                    onCompleted = { onActionItemCompleted(actionItem.id) }
                )
            }
        }

        // Conflicts (if any)
        if (summary.conflicts.isNotEmpty()) {
            item {
                SectionHeader("Areas of Concern")
            }
            items(summary.conflicts) { conflict ->
                ConflictItem(conflict = conflict)
            }
        }
    }
}
```

**Преимущества**:
- 📝 Автоматические резюме разговоров
- ✅ Отслеживание задач и договоренностей
- 📊 Анализ эффективности коммуникации
- 🔄 Улучшение со временем

---

## 📅 День 4: Family Insights & Analytics

### 4.1. AI-powered parenting insights

**Проблема**:
- Родители не видят паттернов в воспитании
- Трудно принимать data-driven решения

**Решение**:
```kotlin
// domain/ai/FamilyInsightsEngine.kt
class FamilyInsightsEngine @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val expenseRepository: ExpenseRepository,
    private val aiService: AIService,
    private val analyticsManager: AnalyticsManager
) {

    suspend fun generateFamilyInsights(
        childId: String,
        timeRange: DateRange = DateRange.LAST_30_DAYS
    ): FamilyInsights {

        val calendarData = calendarRepository.getChildEvents(childId, timeRange)
        val expenseData = expenseRepository.getChildExpenses(childId, timeRange)

        val prompt = buildInsightsPrompt(calendarData, expenseData, timeRange)

        val aiInsights = aiService.generateFamilyInsights(prompt)

        return FamilyInsights(
            timeDistribution = analyzeTimeDistribution(calendarData),
            activityPatterns = analyzeActivityPatterns(calendarData),
            expenseAnalysis = analyzeExpensePatterns(expenseData),
            recommendations = aiInsights.recommendations,
            concerns = aiInsights.concerns,
            positiveTrends = aiInsights.positiveTrends,
            generatedAt = LocalDateTime.now()
        )
    }

    private fun analyzeTimeDistribution(events: List<Event>): TimeDistribution {
        val totalTime = events.sumOf { Duration.between(it.startDateTime, it.endDateTime).toMinutes() }

        val byParent = events.groupBy { it.parentOwner }
            .mapValues { (_, parentEvents) ->
                parentEvents.sumOf { Duration.between(it.startDateTime, it.endDateTime).toMinutes() } / totalTime.toDouble()
            }

        val byActivity = events.groupBy { it.eventType }
            .mapValues { (_, typeEvents) ->
                typeEvents.sumOf { Duration.between(it.startDateTime, it.endDateTime).toMinutes() } / totalTime.toDouble()
            }

        return TimeDistribution(byParent, byActivity)
    }

    private fun analyzeActivityPatterns(events: List<Event>): ActivityPatterns {
        val byDayOfWeek = events.groupBy { it.startDateTime.dayOfWeek }
            .mapValues { (_, dayEvents) -> dayEvents.size }

        val byHourOfDay = events.groupBy { it.startDateTime.hour }
            .mapValues { (_, hourEvents) -> hourEvents.size }

        return ActivityPatterns(byDayOfWeek, byHourOfDay)
    }
}

data class FamilyInsights(
    val timeDistribution: TimeDistribution,
    val activityPatterns: ActivityPatterns,
    val expenseAnalysis: ExpenseAnalysis,
    val recommendations: List<String>,
    val concerns: List<String>,
    val positiveTrends: List<String>,
    val generatedAt: LocalDateTime
)

data class TimeDistribution(
    val byParent: Map<String, Double>, // Percentage of time with each parent
    val byActivity: Map<String, Double>  // Percentage by activity type
)

data class ActivityPatterns(
    val byDayOfWeek: Map<DayOfWeek, Int>,
    val byHourOfDay: Map<Int, Int>
)

// presentation/ai/FamilyInsightsScreen.kt
@Composable
fun FamilyInsightsScreen(childId: String) {
    val insightsEngine = hiltViewModel<FamilyInsightsEngine>()
    var insights by remember { mutableStateOf<FamilyInsights?>(null) }

    LaunchedEffect(childId) {
        insights = insightsEngine.generateFamilyInsights(childId)
    }

    insights?.let { currentInsights ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Time distribution
            item {
                TimeDistributionChart(
                    distribution = currentInsights.timeDistribution,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            // Activity patterns
            item {
                ActivityPatternsChart(
                    patterns = currentInsights.activityPatterns,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            // Recommendations
            if (currentInsights.recommendations.isNotEmpty()) {
                item {
                    SectionHeader("AI Recommendations")
                }
                items(currentInsights.recommendations) { recommendation ->
                    RecommendationCard(recommendation = recommendation)
                }
            }

            // Concerns
            if (currentInsights.concerns.isNotEmpty()) {
                item {
                    SectionHeader("Areas of Attention")
                }
                items(currentInsights.concerns) { concern ->
                    ConcernCard(concern = concern)
                }
            }

            // Positive trends
            if (currentInsights.positiveTrends.isNotEmpty()) {
                item {
                    SectionHeader("Positive Trends")
                }
                items(currentInsights.positiveTrends) { trend ->
                    PositiveTrendCard(trend = trend)
                }
            }
        }
    } ?: LoadingState()
}
```

**Преимущества**:
- 📊 Data-driven insights о воспитании
- 📈 Визуализация распределения времени
- 💡 Персонализированные рекомендации
- 🎯 Выявление проблемных зон

---

### 4.2. Predictive analytics для семейного планирования

**Проблема**:
- Трудно планировать долгосрочные изменения
- Непредсказуемые паттерны поведения

**Решение**:
```kotlin
// domain/ai/PredictiveAnalytics.kt
class PredictiveAnalytics @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val aiService: AIService,
    private val machineLearningEngine: MachineLearningEngine
) {

    suspend fun predictScheduleChanges(
        childId: String,
        predictionHorizon: Duration = Duration.ofDays(30)
    ): List<SchedulePrediction> {

        val historicalData = calendarRepository.getChildEvents(
            childId = childId,
            timeRange = DateRange.LAST_6_MONTHS
        )

        // Train ML model on historical patterns
        val model = machineLearningEngine.trainSchedulePredictionModel(historicalData)

        val predictions = mutableListOf<SchedulePrediction>()

        // Predict for next 30 days
        val startDate = LocalDate.now()
        val endDate = startDate.plus(predictionHorizon)

        var currentDate = startDate
        while (currentDate.isBefore(endDate)) {
            val dayPrediction = model.predictForDate(currentDate)

            if (dayPrediction.confidence > 0.7) {
                predictions.add(SchedulePrediction(
                    date = currentDate,
                    predictedEvents = dayPrediction.events,
                    confidence = dayPrediction.confidence,
                    reasoning = dayPrediction.reasoning
                ))
            }

            currentDate = currentDate.plusDays(1)
        }

        return predictions
    }

    suspend fun predictExpenseTrends(
        childId: String,
        category: ExpenseCategory? = null
    ): ExpenseTrendPrediction {

        val historicalExpenses = expenseRepository.getChildExpenses(
            childId = childId,
            timeRange = DateRange.LAST_12_MONTHS
        ).filter { category == null || it.category == category }

        val prompt = """
            Analyze expense trends for child activities and predict future costs:

            Historical data:
            ${historicalExpenses.joinToString("\n") {
                "${it.date}: ${it.category} - $${it.amount}"
            }}

            Predict:
            1. Monthly spending trends
            2. Seasonal variations
            3. Expected cost increases
            4. Budget recommendations

            Consider:
            - Child's age and developmental stage
            - Typical cost increases for activities
            - Seasonal activity patterns
        """.trimIndent()

        return aiService.predictExpenseTrends(prompt)
    }
}

data class SchedulePrediction(
    val date: LocalDate,
    val predictedEvents: List<PredictedEvent>,
    val confidence: Double,
    val reasoning: String
)

data class PredictedEvent(
    val title: String,
    val eventType: String,
    val startTime: LocalTime,
    val duration: Duration,
    val parentOwner: String
)

// presentation/ai/PredictiveDashboard.kt
@Composable
fun PredictiveDashboard(childId: String) {
    val analytics = hiltViewModel<PredictiveAnalytics>()

    var schedulePredictions by remember { mutableStateOf<List<SchedulePrediction>>(emptyList()) }
    var expensePredictions by remember { mutableStateOf<ExpenseTrendPrediction?>(null) }

    LaunchedEffect(childId) {
        schedulePredictions = analytics.predictScheduleChanges(childId)
        expensePredictions = analytics.predictExpenseTrends(childId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Schedule predictions
        if (schedulePredictions.isNotEmpty()) {
            SchedulePredictionsSection(
                predictions = schedulePredictions.take(7), // Next 7 days
                modifier = Modifier.weight(1f)
            )
        }

        // Expense predictions
        expensePredictions?.let { predictions ->
            ExpensePredictionsSection(
                predictions = predictions,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

**Преимущества**:
- 🔮 Предсказание будущих событий
- 📈 Анализ трендов расходов
- 🎯 Проактивное планирование
- 🤖 ML-powered insights

---

## 📊 Ожидаемые результаты AI интеграции

### Метрики улучшения:
- **Эффективность коммуникации**: +60% (tone analysis, summaries)
- **Предотвращение конфликтов**: +50% (smart scheduling, proactive alerts)
- **Вовлеченность пользователей**: +70% (personalized suggestions, insights)
- **Скорость создания событий**: +80% (voice input, smart suggestions)
- **Удовлетворенность**: +65% (AI assistance, predictive features)

### Технические достижения:
- OpenAI GPT-4 integration
- On-device ML для pattern recognition
- Real-time sentiment analysis
- Predictive modeling для расписаний
- Natural language processing для голосового ввода

---

## 🎯 Приоритеты реализации

### High Priority (максимальный impact):
1. ✅ **День 1**: Smart Scheduling (конфликты, productivity)
2. ✅ **День 3**: Communication Intelligence (relationships, conflicts)
3. ✅ **День 2**: Natural Language Processing (UX, speed)

### Medium Priority (хороший impact):
4. ✅ **День 4**: Family Insights (engagement, retention)

### Technical Requirements:
- **API**: OpenAI GPT-4, Anthropic Claude
- **ML**: TensorFlow Lite для on-device predictions
- **Storage**: Firebase для AI-generated content
- **Privacy**: End-to-end encryption для чувствительных данных

---

💡 **Монетизация**: AI функции как премиум возможности с постепенным переходом к subscription model.</content>
</xai:function_call">﻿

Assistant: Теперь создам последний файл - 5_roadmap_monetization.md с стратегиями монетизации. Сначала обновлю TODO.

<xai:function_call name="todo_write">
<parameter name="merge">true
