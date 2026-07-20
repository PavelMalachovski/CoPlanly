# CoPlanly - Comprehensive Roadmap & Improvements Plan

**Generated:** January 2025
**Project:** CoPlanly - Co-Parenting Calendar App
**Technology:** Kotlin, Jetpack Compose, Material 3, Clean Architecture

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [UI/UX Evaluation](#uiux-evaluation)
3. [Technical Analysis & Refactoring Suggestions](#technical-analysis--refactoring-suggestions)
4. [New Feature Suggestions](#new-feature-suggestions)
5. [AI Integration Opportunities](#ai-integration-opportunities)
6. [Monetization Strategies](#monetization-strategies)
7. [2-Week Priority Roadmap](#2-week-priority-roadmap)
8. [Architecture Rules Summary](#architecture-rules-summary)
9. [PR Rules Summary](#pr-rules-summary)
10. [Final Recommendations](#final-recommendations)

---

## Executive Summary

CoPlanly is a well-architected Android application built with modern Kotlin and Jetpack Compose following Clean Architecture principles. The application provides a shared calendar solution for co-parenting, with Google Calendar sync, Firebase integration, and real-time collaboration features.

**Current State:**
- ✅ Solid Clean Architecture implementation (Data/Domain/Presentation layers)
- ✅ Modern UI with Jetpack Compose and Material 3
- ✅ Firebase integration (Auth, Firestore, FCM, Analytics, Crashlytics)
- ✅ Google Calendar API integration
- ✅ Offline-first approach with Room database
- ✅ Conflict resolution system in place
- ⚠️ Limited test coverage
- ⚠️ Some UI/UX consistency issues
- ⚠️ Missing advanced features for engagement

**Key Opportunities:**
- AI-powered scheduling assistance and conflict resolution
- Enhanced engagement features (messaging, expense tracking)
- B2B monetization through therapist/mediator partnerships
- Premium subscription tiers with AI features
- iOS expansion via Kotlin Multiplatform Mobile (KMM)

---

## UI/UX Evaluation

### 1. Usability Issues

#### Dashboard (CalendarScreen)
**Issues:**
1. **Information Overload:** All view modes (Day, 3 Days, Week, Month) shown simultaneously in selector - can overwhelm new users
2. **Missing Quick Actions:** No quick-add buttons for common event types (school, doctor, activity)
3. **Limited Contextual Information:** Today's custody indicator only shown in Month view
4. **Event Details:** No preview on tap - requires full navigation to edit screen
5. **Empty States:** No helpful empty states when no events exist

**Recommendations:**
- Add contextual quick-action menu based on view mode
- Implement event preview cards on long-press
- Show custody indicator in all view modes
- Add onboarding tooltips for first-time users
- Create engaging empty states with CTA buttons

#### Calendar Navigation
**Issues:**
1. **Inconsistent Navigation:** Three methods (swipe, arrows, date picker) can confuse users
2. **Date Selection:** Date picker dialog is hidden - users may not discover it
3. **Today Button:** Not prominently displayed in header

**Recommendations:**
- Consolidate navigation to primary method (swipe) with secondary (arrows)
- Add "Jump to Date" button in header
- Prominent "Today" button with badge when not on current date
- Haptic feedback for navigation actions

#### Event Creation (AddEditEventScreen)
**Issues:**
1. **Form Complexity:** Multiple pickers and fields can be overwhelming
2. **Validation Feedback:** Errors shown inline but not always clear
3. **Parent Selection:** Radio buttons for "mom/dad" may not fit all family structures
4. **Event Types:** Limited predefined types - no custom categories

**Recommendations:**
- Implement wizard-style multi-step form for new events
- Add visual validation indicators (green checkmarks)
- Support custom parent labels ("Parent 1/Parent 2" or names)
- Allow custom event type categories
- Add smart suggestions based on history

### 2. Flow Problems

#### Onboarding Flow
**Missing:**
- No onboarding flow for new users
- Pairing process is technical (email-based) without guidance
- No tutorial or guided tour

**Recommendations:**
1. **Welcome Screen:** Brand introduction with value proposition
2. **Step-by-Step Setup:**
   - Account creation/login
   - Child information setup
   - Co-parent invitation
   - Calendar permissions
   - First event creation tutorial
3. **Progress Indicator:** Show completion percentage
4. **Skip Option:** Allow skipping with reminders to complete later

#### Event Management Flow
**Issues:**
1. **Delete Confirmation:** No confirmation dialog for event deletion
2. **Bulk Operations:** Cannot select multiple events for batch actions
3. **Undo/Redo:** No undo functionality for accidental deletions

**Recommendations:**
- Add confirmation dialogs for destructive actions
- Implement bulk selection mode
- Add undo snackbar with 5-second timeout
- Show recycle bin for recently deleted items (30 days)

#### Pairing Flow
**Issues:**
1. **Email Validation:** Basic validation but no helpful error messages
2. **Invitation Status:** Unclear when invitation is sent/received
3. **No Alternative Methods:** Only email-based pairing

**Recommendations:**
- Add QR code pairing option
- Show invitation timeline (sent → delivered → opened → accepted)
- Support phone number-based pairing
- Allow pairing via shared link

### 3. Inconsistent UI Patterns

#### Color Usage
**Issues:**
- Custody colors (pink/blue) are hardcoded - no customization
- Event type colors not visually distinct
- No consistent color coding for recurring vs one-time events

**Recommendations:**
- Allow parent color customization in settings
- Implement color picker for event types
- Use visual indicators (icons + colors) for event categories
- Support accessibility color blind modes

#### Typography
**Issues:**
- Inconsistent font sizes for similar elements
- No clear hierarchy in event lists
- Date/time formatting varies across screens

**Recommendations:**
- Create typography scale following Material 3 guidelines
- Standardize date/time formatting with locale support
- Use consistent text styles (headline, body, caption)

#### Spacing & Layout
**Issues:**
- Inconsistent padding values across screens
- Event cards have varying sizes
- Alignment issues in Month view

**Recommendations:**
- Use `dimensions()` utility consistently (already implemented)
- Standardize card heights and spacing
- Improve grid alignment in calendar views

### 4. Accessibility Issues

#### Screen Readers
**Missing:**
- Some icons lack content descriptions
- Event cards not properly labeled for TalkBack
- Navigation buttons need better semantic labels

**Fixes:**
- Add `contentDescription` to all icon buttons
- Use semantic roles (`Button`, `Checkbox`, etc.)
- Test with TalkBack enabled

#### Touch Targets
**Issues:**
- Some clickable areas below 48dp minimum
- Event dots in month view too small for easy tapping
- Date picker buttons too close together

**Fixes:**
- Ensure all interactive elements ≥ 48dp
- Increase event indicator tap targets
- Add spacing between date picker buttons

#### Color Contrast
**Issues:**
- Light pink/blue may not meet WCAG AA standards in light theme
- Error text may not have sufficient contrast

**Fixes:**
- Audit all color combinations with contrast checker
- Adjust custody colors to meet WCAG AA (4.5:1)
- Ensure error states use sufficient contrast

### 5. 2025 Design Trends Recommendations

#### Glassmorphism & Depth
- Add subtle blur effects to floating action buttons
- Implement layered navigation with depth cues
- Use elevation properly for cards (already using Material 3)

#### Micro-interactions
- Add loading skeleton animations (partially implemented)
- Implement success animations for event creation
- Add subtle bounce effects for calendar navigation

#### Adaptive Design
- Better tablet/landscape layouts (WindowSizeClass partially implemented)
- Optimize for foldable devices
- Support Android Auto for voice commands

#### Gesture-First Design
- Swipe actions on event cards (swipe left to delete, right to edit)
- Pull-to-refresh (already implemented)
- Pinch to zoom in calendar views

#### Personalization
- Allow theme customization beyond dark/light
- Customizable home screen widgets
- User preferences for default view mode

---

## Technical Analysis & Refactoring Suggestions

### 1. Architecture

#### Current State
**Strengths:**
- ✅ Clean Architecture with clear separation (Data/Domain/Presentation)
- ✅ Repository pattern properly implemented
- ✅ Dependency Injection with Hilt
- ✅ ViewModel pattern for state management
- ✅ Use cases could be extracted from ViewModels

**Issues:**
1. **Missing Use Case Layer:** Business logic is in ViewModels instead of Use Cases
2. **Data Layer Complexity:** Repository implementations handle too much (sync + mapping + validation)
3. **Tight Coupling:** Some ViewModels directly depend on multiple repositories

**Refactoring Plan:**

```kotlin
// Current (EventViewModel)
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val analyticsManager: AnalyticsManager
) {
    fun createEvent(event: Event) {
        // Business logic mixed with UI logic
        val eventWithId = if (event.id.isEmpty()) {
            event.copy(id = UUID.randomUUID().toString())
        } else event
        eventRepository.insertEvent(eventWithId)
    }
}

// Proposed (with Use Cases)
// domain/usecase/CreateEventUseCase.kt
class CreateEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val validator: EventValidator
) {
    suspend operator fun invoke(event: Event): Result<Event> {
        val validation = validator.validate(event)
        if (validation.isFailure) return validation

        val eventWithId = event.copyIfNeeded(id = generateId())
        return eventRepository.insertEvent(eventWithId)
    }
}

// Updated ViewModel
class EventViewModel @Inject constructor(
    private val createEventUseCase: CreateEventUseCase,
    private val analyticsManager: AnalyticsManager
) {
    fun createEvent(event: Event) {
        viewModelScope.launch {
            when (val result = createEventUseCase(event)) {
                is Result.Success -> {
                    analyticsManager.logEventCreated(result.data.eventType)
                    _uiState.value = EventUiState.OperationSuccess("Event created")
                }
                is Result.Failure -> {
                    _uiState.value = EventUiState.Error(result.message)
                }
            }
        }
    }
}
```

**Priority:** Medium (refactor incrementally)

### 2. File Structure

#### Current Structure
```
app/src/main/java/com/coparently/app/
├── data/
│   ├── local/
│   ├── remote/
│   ├── repository/
│   └── sync/
├── domain/
│   ├── model/
│   └── repository/
├── presentation/
│   ├── auth/
│   ├── calendar/
│   ├── event/
│   └── ...
└── di/
```

#### Recommended Improvements

**Add Use Case Layer:**
```
domain/
├── model/
├── repository/
└── usecase/          # NEW
    ├── event/
    │   ├── CreateEventUseCase.kt
    │   ├── UpdateEventUseCase.kt
    │   ├── DeleteEventUseCase.kt
    │   └── GetEventsByDateRangeUseCase.kt
    ├── sync/
    │   └── SyncWithFirestoreUseCase.kt
    └── pairing/
        └── SendInvitationUseCase.kt
```

**Split Large Files:**
- `CalendarScreen.kt` (608 lines) → Split into:
  - `CalendarScreen.kt` (main composable)
  - `CalendarContent.kt` (calendar logic)
  - `CalendarAnimations.kt` (animation helpers)

**Package by Feature (Future Consideration):**
```
feature/
├── calendar/
│   ├── data/
│   ├── domain/
│   └── presentation/
├── events/
│   ├── data/
│   ├── domain/
│   └── presentation/
```

**Priority:** Low (current structure is acceptable)

### 3. Component Quality

#### Stateless Components ✅
All components follow stateless pattern correctly.

#### Reusability Issues
**Problems:**
1. Duplicated date picker logic across screens
2. Event card rendering logic scattered
3. Validation UI repeated in forms

**Refractions:**
```kotlin
// presentation/common/components/DatePickerButton.kt
@Composable
fun DatePickerButton(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    // Reusable date picker button
}

// presentation/common/components/EventCard.kt
@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Standardized event card
}

// presentation/common/components/ValidatedTextField.kt
@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    validator: (String) -> ValidationResult,
    // ...
) {
    // Reusable validated input
}
```

**Priority:** High (improves consistency and maintainability)

### 4. State Management

#### Current Approach
- ✅ `StateFlow` for reactive state (correct)
- ✅ `collectAsState()` in Composables (correct)
- ⚠️ Some ViewModels manage too much state

#### Issues
1. **UI State Complexity:** `EventUiState` has multiple subtypes but some overlap
2. **Loading States:** Multiple loading states in same ViewModel
3. **Error Handling:** Errors stored in UI state but not always recoverable

#### Recommendations
```kotlin
// Improved UI State pattern
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(
        val message: String,
        val retry: (() -> Unit)? = null,
        val dismissible: Boolean = true
    ) : UiState<Nothing>()
}

// Usage
class EventViewModel {
    private val _uiState = MutableStateFlow<UiState<List<Event>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<Event>>> = _uiState.asStateFlow()

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                eventRepository.getAllEvents().collect { events ->
                    _uiState.value = UiState.Success(events)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    message = "Failed to load events",
                    retry = { loadEvents() }
                )
            }
        }
    }
}
```

**Priority:** Medium (incremental improvement)

### 5. Performance

#### Current Optimizations ✅
- Lazy initialization of ViewModels
- Flow collection in ViewModelScope
- Room with type converters
- Compose recomposition optimizations

#### Issues & Optimizations

**1. Calendar Rendering:**
```kotlin
// Current: Re-renders entire month on date change
MonthView(selectedMonth = month, events = events, ...)

// Optimized: Use derivedStateOf for filtered events
@Composable
fun MonthView(...) {
    val visibleEvents by remember(month, events) {
        derivedStateOf {
            events.filter { event ->
                YearMonth.from(event.startDateTime) == month
            }
        }
    }
}
```

**2. Database Queries:**
- Add indexes for date range queries
- Use `distinctUntilChanged()` for Flow emissions
- Paginate event loading for large datasets

**3. Image Loading (Future):**
- Implement Coil for any future image loading
- Add placeholder images
- Cache images locally

**Priority:** Medium (good performance, optimize as needed)

### 6. Data Fetching

#### Current Implementation ✅
- Flow-based reactive data streams
- Local-first with Firestore sync
- Offline support with Room

#### Improvements

**1. Caching Strategy:**
```kotlin
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val firestoreEventDataSource: FirestoreEventDataSource
) {
    // Add cache TTL
    private val cacheExpiry = 5.minutes

    override fun getAllEvents(): Flow<List<Event>> {
        return eventDao.getAllEvents()
            .distinctUntilChanged()
            .onEach { localEvents ->
                // Background sync if cache expired
                if (shouldRefresh(localEvents)) {
                    syncWithFirestore()
                }
            }
            .map { entities -> entities.map { it.toDomain() } }
    }
}
```

**2. Retry Logic:**
```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(minOf(currentDelay, maxDelay))
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    throw IllegalStateException("Should not reach here")
}
```

**Priority:** Low (current implementation works)

### 7. Error Handling

#### Current State
- Basic try-catch in ViewModels
- Error states in UI State sealed classes
- Crashlytics integration for exceptions

#### Issues
1. **Generic Error Messages:** "Failed to load events" not helpful
2. **No Error Recovery:** Retry logic not consistently implemented
3. **Network Errors:** Not distinguished from other errors

#### Improved Pattern
```kotlin
sealed class AppError : Exception() {
    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : AppError()

    data class ValidationError(
        val field: String,
        val message: String
    ) : AppError()

    data class PermissionError(
        val permission: String
    ) : AppError()

    data class UnknownError(
        val message: String,
        val cause: Throwable? = null
    ) : AppError()
}

// Error Handler
class ErrorHandler @Inject constructor(
    private val crashlytics: CrashlyticsManager
) {
    fun handleError(error: Throwable): String {
        return when (error) {
            is AppError.NetworkError -> "Network error. Check your connection."
            is AppError.ValidationError -> error.message
            is AppError.PermissionError -> "Permission denied: ${error.permission}"
            else -> {
                crashlytics.recordException(error)
                "Something went wrong. Please try again."
            }
        }
    }
}
```

**Priority:** High (improves user experience)

### 8. TypeScript Strictness (Kotlin Null Safety)

#### Current State ✅
- Kotlin null safety enforced
- Nullable types used appropriately
- Safe calls and Elvis operators

#### Minor Improvements
- Consider using `requireNotNull()` for critical path assertions
- Add `@Throws` annotations for Java interop clarity
- Document nullable return types in KDoc

**Priority:** Low (already good)

### 9. Dependency Management

#### Current State ✅
- Using BOMs (Compose, Firebase) for version alignment
- Latest stable versions
- Hilt for DI

#### Recommendations
1. **Version Catalog (Gradle 7+):**
```kotlin
// gradle/libs.versions.toml
[versions]
compose = "2024.11.00"
hilt = "2.52"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
```

2. **Dependency Updates:**
- Set up Dependabot or Renovate for automated updates
- Review dependency updates monthly
- Test thoroughly before upgrading major versions

**Priority:** Low (current setup is fine)

### 10. Scalability Risks

#### Current Limitations

**1. Firestore Queries:**
- No pagination for events
- Could hit Firestore query limits with many events

**Solution:**
```kotlin
fun getEventsByDateRange(
    start: LocalDateTime,
    end: LocalDateTime,
    pageSize: Int = 50
): Flow<PagedData<Event>> {
    // Implement pagination
}
```

**2. Local Database:**
- Room database will grow indefinitely
- No data cleanup strategy

**Solution:**
```kotlin
// Add cleanup job
suspend fun cleanupOldEvents() {
    val cutoffDate = LocalDateTime.now().minusYears(2)
    eventDao.deleteEventsBefore(cutoffDate)
}
```

**3. Sync Performance:**
- Full sync on every change is inefficient
- Could be slow with many events

**Solution:**
- Implement incremental sync with timestamps
- Use Firestore snapshots for real-time updates
- Batch sync operations

**Priority:** Medium (plan for scale before issues arise)

---

## New Feature Suggestions

### 1. In-App Messaging 💬
**Impact:** High retention, transparency
**Effort:** High (2-3 weeks)

**Features:**
- Direct messaging between co-parents
- Event-based conversations (thread per event)
- Read receipts and typing indicators
- Media sharing (photos, documents)
- Message templates for common scenarios
- Notification preferences per conversation

**Technical Implementation:**
- Firestore `conversations` and `messages` collections
- Real-time listeners for new messages
- Image compression and Firebase Storage
- Encryption for sensitive messages (optional)

### 2. Expense Tracker 💰
**Impact:** High engagement, transparency
**Effort:** Medium (1-2 weeks)

**Features:**
- Track child-related expenses
- Categories (education, healthcare, clothing, activities)
- Photo receipts
- Split expenses (50/50 or custom ratios)
- Monthly/yearly reports
- Export to CSV/PDF

**Technical Implementation:**
- New `expense` entity in Room
- Firestore sync for expense sharing
- Receipt OCR (ML Kit) for automatic data extraction
- Charts library for reports

### 3. Document Storage 📎
**Impact:** Medium retention, convenience
**Effort:** Medium (1-2 weeks)

**Features:**
- Store important documents (medical records, school reports, legal)
- Organized folders by category
- OCR text search
- Share with co-parent
- Version history
- Secure cloud storage (Firebase Storage)

### 4. Recurring Event Templates 📋
**Impact:** Medium engagement, ease of use
**Effort:** Low (3-5 days)

**Features:**
- Save common events as templates
- Quick create from template
- Templates shared between parents
- Custom recurrence patterns beyond standard (e.g., "Every other week")
- School calendar integration (ICS import)

### 5. Activity Suggestions 🎨
**Impact:** Medium engagement
**Effort:** Medium (1 week)

**Features:**
- Suggest age-appropriate activities based on child info
- Weather-based suggestions
- Location-based suggestions (nearby parks, museums)
- Save favorite activities
- Integration with local event calendars

### 6. Medical Records Tracker 🏥
**Impact:** High value, retention
**Effort:** Medium (1-2 weeks)

**Features:**
- Track doctor appointments
- Vaccination records with reminders
- Medication schedules with alerts
- Allergy tracking with visual warnings
- Growth charts (height, weight)
- Medical history timeline

### 7. Calendar Widgets 📱
**Impact:** High daily usage
**Effort:** Medium (1 week)

**Features:**
- Home screen widget showing today's events
- Custody indicator widget
- Upcoming events widget (next 7 days)
- Customizable widget sizes (1x1, 2x2, 4x2)
- Tap to open app

### 8. Voice Notes & Reminders 🎤
**Impact:** Medium engagement, convenience
**Effort:** Medium (1-2 weeks)

**Features:**
- Record voice notes attached to events
- Speech-to-text transcription
- Voice reminders (set via voice)
- Voice commands for common actions
- Share voice notes with co-parent

**Technical Implementation:**
- Android Speech Recognizer API
- Firebase ML Kit for transcription
- Audio storage in Firebase Storage

### 9. Conflict Resolution Assistant 🤝
**Impact:** High value, differentiation
**Effort:** High (2-3 weeks) - See AI Integration

**Features:**
- AI-powered scheduling suggestions when conflicts arise
- Alternative time slot recommendations
- Communication templates for difficult conversations
- Neutral tone suggestions for messages

### 10. Co-Parenting Tips & Resources 📚
**Impact:** Medium engagement, community
**Effort:** Low (3-5 days)

**Features:**
- Daily tips for effective co-parenting
- Articles library (communication, legal, psychological)
- Video resources
- Community forum (future)
- Professional resources directory (therapists, mediators)

### 11. Backup & Export 📤
**Impact:** Medium trust, data portability
**Effort:** Low (3-5 days)

**Features:**
- Export all data to JSON/CSV
- Google Drive backup integration
- Scheduled automatic backups
- Import from other calendar apps (ICS files)
- Data portability compliance

### 12. Multi-Child Support 👨‍👩‍👧‍👦
**Impact:** High engagement (broader use case)
**Effort:** Medium (1-2 weeks)

**Features:**
- Support multiple children per account
- Per-child calendars and information
- Filter events by child
- Child-specific custody schedules
- Shared expenses per child

**Technical Changes:**
- Add `childId` foreign key to events
- Update all queries to filter by child
- UI for child selector/switcher

### 13. Integration with School Systems 🏫
**Impact:** High convenience
**Effort:** High (2-3 weeks) - Requires partnerships

**Features:**
- Import school calendars (ICS feeds)
- Parent portal integration (if APIs available)
- Homework tracking
- School event notifications
- Teacher communication log

### 14. Analytics Dashboard 📊
**Impact:** Medium engagement, insights
**Effort:** Medium (1 week)

**Features:**
- Time spent with each parent (visualization)
- Event frequency by type
- Expense trends over time
- Communication frequency
- Exportable reports

### 15. Emergency Contacts Quick Access 🚨
**Impact:** High safety value
**Effort:** Low (2-3 days)

**Features:**
- Quick access button/widget for emergency contacts
- One-tap calling
- Share location in emergency
- Medical information card (lock screen widget)
- ICE (In Case of Emergency) information

---

## AI Integration Opportunities

### 1. Smart Scheduling Assistant 🤖
**Value:** Conflict-free scheduling, reduced negotiation time
**Models:** GPT-4 or Claude for reasoning, custom fine-tuned model for patterns

**Features:**
- Analyzes both parents' calendars
- Suggests optimal time slots for new events
- Identifies potential conflicts before they occur
- Recommends alternative dates when conflicts exist
- Learns parent preferences over time

**Implementation:**
```kotlin
class SmartSchedulingUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val aiService: AIService
) {
    suspend fun suggestOptimalTime(
        eventType: String,
        duration: Duration,
        preferredDates: List<LocalDate>
    ): List<TimeSlotSuggestion> {
        val calendars = calendarRepository.getBothParentsCalendars()
        val preferences = calendarRepository.getParentPreferences()

        val prompt = buildPrompt(eventType, duration, preferredDates, calendars, preferences)
        val suggestions = aiService.generateSuggestions(prompt)

        return parseSuggestions(suggestions)
    }
}
```

**Cost:** Medium (API calls per request)
**Priority:** High

### 2. Natural Language Event Creation 📝
**Value:** Faster event creation, better UX
**Models:** GPT-4 or local on-device model (Gemini Nano)

**Features:**
- Voice or text input: "Doctor appointment for Emma on March 15th at 2pm"
- Parses natural language into structured event
- Asks clarifying questions if ambiguous
- Learns user patterns ("Emma's weekly piano lesson")

**Implementation:**
```kotlin
class NaturalLanguageEventParser @Inject constructor(
    private val nlpService: NLPService
) {
    suspend fun parseEventFromText(text: String): ParsedEvent? {
        val prompt = """
            Parse the following into an event:
            Event title, date, time, duration, parent owner, event type.

            Text: "$text"

            Return JSON format.
        """
        return nlpService.parse(prompt)
    }
}
```

**Cost:** Low-Medium (per parse)
**Priority:** High

### 3. Schedule Change Prediction 🔮
**Value:** Proactive conflict prevention
**Models:** Time-series forecasting model (TensorFlow Lite)

**Features:**
- Predicts when events might be rescheduled based on patterns
- Alerts parents of likely conflicts in advance
- Suggests backup plans
- Learns from historical changes

**Implementation:**
```kotlin
class ScheduleChangePredictor @Inject constructor(
    private val eventRepository: EventRepository
) {
    suspend fun predictRescheduleProbability(
        event: Event
    ): ReschedulePrediction {
        val history = eventRepository.getEventHistory(event.id)
        val similarEvents = eventRepository.getSimilarEvents(event)

        // Train lightweight model on historical patterns
        val model = loadPredictionModel()
        val features = extractFeatures(event, history, similarEvents)

        return model.predict(features)
    }
}
```

**Cost:** Low (on-device inference)
**Priority:** Medium

### 4. Communication Tone Analyzer & Suggester 💬
**Value:** Improved communication, reduced conflict
**Models:** Sentiment analysis (BERT-based), GPT-4 for rewrites

**Features:**
- Analyzes message tone before sending
- Suggests more neutral/positive alternatives
- Flags potentially inflammatory language
- Provides constructive communication templates

**Implementation:**
```kotlin
class ToneAnalyzer @Inject constructor(
    private val aiService: AIService
) {
    suspend fun analyzeAndSuggest(message: String): ToneAnalysis {
        val sentiment = analyzeSentiment(message)
        val suggestions = if (sentiment.isNegative) {
            aiService.rewriteMessage(message, tone = "neutral", context = "co-parenting")
        } else null

        return ToneAnalysis(sentiment, suggestions)
    }
}
```

**Cost:** Medium (per message analysis)
**Priority:** High

### 5. Automated Agreement Generation 📄
**Value:** Legal clarity, time-saving
**Models:** GPT-4 with legal fine-tuning (or legal API integration)

**Features:**
- Generates custody agreements from calendar data
- Creates expense sharing agreements
- Produces holiday schedule proposals
- Customizable templates based on jurisdiction

**Implementation:**
```kotlin
class AgreementGenerator @Inject constructor(
    private val aiService: AIService,
    private val templateRepository: TemplateRepository
) {
    suspend fun generateCustodyAgreement(
        schedule: CustodySchedule,
        preferences: AgreementPreferences
    ): Agreement {
        val template = templateRepository.getTemplate(preferences.jurisdiction)
        val data = extractScheduleData(schedule)

        return aiService.fillTemplate(template, data, preferences)
    }
}
```

**Cost:** High (requires legal review)
**Priority:** Medium (premium feature)

### 6. Voice-to-Structured Notes 🎤
**Value:** Quick documentation, accessibility
**Models:** Speech-to-text (Google ML Kit), GPT-4 for structuring

**Features:**
- Voice input for event notes
- Transcribes and structures automatically
- Extracts key information (dates, names, actions)
- Creates follow-up reminders from voice notes

**Implementation:**
```kotlin
class VoiceNoteProcessor @Inject constructor(
    private val speechRecognizer: SpeechRecognizer,
    private val nlpService: NLPService
) {
    suspend fun processVoiceNote(audio: ByteArray): StructuredNote {
        val transcript = speechRecognizer.transcribe(audio)
        val structured = nlpService.extractStructure(transcript)
        return StructuredNote(transcript, structured)
    }
}
```

**Cost:** Low (ML Kit is free)
**Priority:** Medium

### 7. AI Legal Co-pilot for Documents ⚖️
**Value:** Legal assistance, premium feature
**Models:** Legal AI APIs (Harvey, Casetext) or GPT-4 with legal training

**Features:**
- Answers legal questions about co-parenting
- Explains custody rights by jurisdiction
- Suggests when to consult a lawyer
- Helps prepare for court (if applicable)

**Implementation:**
- Integration with legal AI APIs
- Local knowledge base for common questions
- Escalation to human lawyers when needed

**Cost:** High (API costs or subscription)
**Priority:** Low (niche feature)

### 8. Medical Appointment Co-pilot 🏥
**Value:** Health management, peace of mind
**Models:** GPT-4 for question generation, medical knowledge base

**Features:**
- Prepares questions for doctor visits
- Summarizes medical records
- Medication interaction checks
- Vaccination schedule reminders

**Implementation:**
```kotlin
class MedicalCoPilot @Inject constructor(
    private val aiService: AIService,
    private val medicalRepository: MedicalRepository
) {
    suspend fun prepareForAppointment(
        appointment: MedicalAppointment,
        childInfo: ChildInfo
    ): AppointmentPreparation {
        val questions = aiService.generateQuestions(
            context = "child medical appointment",
            age = childInfo.age,
            symptoms = appointment.reason
        )
        val history = medicalRepository.getHistory(childInfo.id)
        return AppointmentPreparation(questions, history, reminders)
    }
}
```

**Cost:** Medium
**Priority:** Medium

### 9. School Task Assistant 🎓
**Value:** Education support, engagement
**Models:** GPT-4 for homework help, calendar integration

**Features:**
- Homework tracking and reminders
- Study schedule suggestions
- Parent-teacher conference prep
- Educational activity recommendations

**Cost:** Low-Medium
**Priority:** Medium

### 10. Activity Recommendation Engine 🎨
**Value:** Child development, engagement
**Models:** Recommendation system (collaborative filtering + content-based)

**Features:**
- Recommends age-appropriate activities
- Considers weather, location, time
- Learns from parent feedback
- Integrates with local event calendars

**Implementation:**
```kotlin
class ActivityRecommender @Inject constructor(
    private val eventRepository: EventRepository,
    private val weatherService: WeatherService,
    private val locationService: LocationService
) {
    suspend fun recommendActivities(
        childInfo: ChildInfo,
        date: LocalDate,
        preferences: ActivityPreferences
    ): List<ActivitySuggestion> {
        val weather = weatherService.getForecast(date)
        val location = locationService.getCurrentLocation()
        val history = eventRepository.getPastActivities(childInfo.id)

        // Use recommendation algorithm
        return recommendationEngine.recommend(
            age = childInfo.age,
            weather = weather,
            location = location,
            history = history,
            preferences = preferences
        )
    }
}
```

**Cost:** Low
**Priority:** Medium

### 11. Pattern Analysis & Insights 📊
**Value:** Self-awareness, improvement
**Models:** Data analysis, simple ML for patterns

**Features:**
- Identifies communication patterns (frequency, tone)
- Highlights scheduling conflicts trends
- Suggests improvements based on successful patterns
- Generates insights reports

**Implementation:**
- Local analytics with TensorFlow Lite
- Pattern recognition on user data
- Privacy-preserving (on-device analysis)

**Cost:** Low
**Priority:** Low

---

## Monetization Strategies

### 1. Freemium → Premium Model

#### Free Tier (Forever Free)
**Features:**
- Basic calendar (day/week/month views)
- Up to 2 children
- Event creation and management
- Basic custody schedule
- Google Calendar sync (1-way)
- Co-parent pairing
- Push notifications
- 30-day event history
- Basic child information

**Limitations:**
- No AI features
- No document storage
- No expense tracking
- Limited customization
- Basic support

#### Premium Tier ($9.99/month or $79.99/year)
**Additional Features:**
- Unlimited children
- AI scheduling assistant
- Natural language event creation
- Voice notes and reminders
- Document storage (10GB)
- Expense tracker
- Recurring event templates
- Advanced analytics dashboard
- Priority support
- Custom themes and branding
- Export/backup features
- Multi-device sync

#### Family Premium ($14.99/month or $119.99/year)
**Everything in Premium +:**
- Up to 4 co-parent pairs
- Shared family calendar
- Advanced AI features
- Legal document assistance
- Medical records management
- 50GB document storage
- White-label option (remove CoPlanly branding)

**Target Conversion:** 5-10% of free users

### 2. AI-Powered Subscription Tiers

#### Basic AI ($4.99/month)
- Natural language event creation
- Smart scheduling suggestions
- Tone analyzer for messages

#### Advanced AI ($14.99/month)
- Everything in Basic AI
- Predictive schedule changes
- Automated agreement generation
- Medical appointment co-pilot
- Voice-to-structured notes

#### Enterprise AI ($49.99/month per organization)
- Custom AI model training
- API access
- Dedicated support
- White-label solution

### 3. Feature-Based Upsells

#### One-Time Purchases
- **Document Storage Pack:** $2.99 for 50GB additional storage
- **Theme Pack:** $0.99 for premium themes
- **Export Tool:** $4.99 one-time for advanced export features
- **Backup Service:** $1.99/month for automated cloud backups

#### In-App Purchases
- Remove ads (if added): $2.99/month
- Unlock premium widgets: $0.99
- Extended event history: $1.99/month

### 4. B2B Revenue Streams

#### Therapist/Mediator Partnerships
**Model:** SaaS licensing

**Features:**
- Multi-client dashboard
- Client progress tracking
- Communication logs
- Appointment scheduling
- Billing integration
- HIPAA compliance (if applicable)

**Pricing:**
- Professional Plan: $29.99/month (up to 10 clients)
- Practice Plan: $99.99/month (up to 50 clients)
- Enterprise: Custom pricing (unlimited)

**Partnership Strategy:**
- Offer free trials to therapists
- Provide referral commissions (20% recurring)
- White-label option for practices
- Training and onboarding support

#### School Partnerships
**Model:** Per-school licensing

**Features:**
- School calendar integration
- Parent portal access
- Attendance tracking
- Homework assignments
- Teacher-parent messaging

**Pricing:**
- School License: $499/year per school
- District License: $4,999/year (up to 10 schools)
- State License: Custom pricing

### 5. Marketplace/Affiliate Options

#### Legal Services Marketplace
- Partner with family law attorneys
- Commission: 10-15% per referral
- Featured listings for premium placement

#### Childcare Services
- Partner with babysitters, daycares, activity centers
- Commission: 5-10% per booking
- Integration with scheduling

#### Educational Resources
- Affiliate links for educational apps, books, toys
- Commission: 5-15% per purchase
- Curated recommendations based on child age

### 6. Cross-Selling Partnerships

#### Calendar App Partnerships
- Integration with other calendar apps (Outlook, Apple Calendar)
- Revenue sharing for premium integrations

#### Legal Tech Partnerships
- Integration with legal document services (LegalZoom, Rocket Lawyer)
- Revenue sharing per document generated

#### Healthcare Partnerships
- Integration with telehealth platforms
- Commission for appointment bookings
- Health record management services

### 7. Cost-Benefit Evaluation

#### Development Costs

**High Priority Features:**
- In-app messaging: $15,000-25,000 (2-3 weeks dev)
- Expense tracker: $10,000-15,000 (1-2 weeks)
- AI scheduling assistant: $20,000-35,000 (2-4 weeks + API costs)

**Medium Priority:**
- Document storage: $8,000-12,000
- Voice notes: $10,000-15,000
- Analytics dashboard: $8,000-12,000

**Monthly Operating Costs:**
- Firebase: $100-500/month (scales with users)
- AI API costs: $500-2000/month (GPT-4 usage)
- Cloud storage: $50-200/month
- Support: $2,000-5,000/month (if outsourced)

#### Revenue Projections (Conservative)

**Year 1:**
- 10,000 free users
- 500 premium subscribers (5% conversion): $4,995/month
- 100 family premium: $1,499/month
- B2B: 20 therapists × $29.99 = $600/month
- **Total Monthly Recurring Revenue (MRR):** ~$7,094
- **Annual Recurring Revenue (ARR):** ~$85,128

**Year 2:**
- 50,000 free users
- 2,500 premium: $24,975/month
- 500 family: $7,495/month
- B2B: 100 therapists + 10 schools = $3,499/month
- **MRR:** ~$35,969
- **ARR:** ~$431,628

**Year 3:**
- 200,000 free users
- 10,000 premium: $99,900/month
- 2,000 family: $29,980/month
- B2B: 500 therapists + 50 schools = $19,995/month
- **MRR:** ~$149,875
- **ARR:** ~$1,798,500

#### Break-Even Analysis

**Monthly Costs Year 1:** ~$3,000-5,000
**Monthly Revenue Year 1:** ~$7,094
**Break-even:** Month 6-8

**ROI Timeline:**
- Month 12: Positive ROI
- Year 2: 7-10x ROI
- Year 3: 15-20x ROI

---

## 2-Week Priority Roadmap

### Week 1: Critical Fixes & Foundation

#### Day 1-2: High Priority
**Task 1.1: Fix Accessibility Issues**
- **Effort:** 4 hours
- **Impact:** High (legal compliance, user base expansion)
- **Acceptance Criteria:**
  - All icons have content descriptions
  - Touch targets ≥ 48dp
  - Color contrast meets WCAG AA
  - TalkBack tested on all screens
- **Technical Notes:**
  - Audit all Composable screens
  - Use `semantics {}` modifier for proper roles
  - Test with Accessibility Scanner

**Task 1.2: Improve Error Handling**
- **Effort:** 6 hours
- **Impact:** High (better UX, reduced frustration)
- **Acceptance Criteria:**
  - Consistent error messages across app
  - Retry buttons for recoverable errors
  - User-friendly error descriptions
  - Crashlytics integration for all exceptions
- **Technical Notes:**
  - Create `AppError` sealed class hierarchy
  - Implement `ErrorHandler` utility
  - Update all ViewModels to use new error handling

**Task 1.3: Add Event Deletion Confirmation**
- **Effort:** 2 hours
- **Impact:** Medium (prevent accidental deletions)
- **Acceptance Criteria:**
  - Confirmation dialog before deletion
  - Undo snackbar after deletion (5-second timeout)
  - Recycle bin view (optional for future)
- **Technical Notes:**
  - Add `AlertDialog` with confirmation
  - Implement undo mechanism in `EventViewModel`
  - Store deleted events temporarily for undo

#### Day 3-4: Medium Priority
**Task 1.4: Create Reusable Components**
- **Effort:** 8 hours
- **Impact:** High (code quality, maintainability)
- **Acceptance Criteria:**
  - `DatePickerButton` component extracted
  - `EventCard` component standardized
  - `ValidatedTextField` component created
  - All screens updated to use new components
- **Technical Notes:**
  - Create `presentation/common/components/` directory
  - Ensure components are stateless and reusable
  - Add KDoc documentation

**Task 1.5: Improve Onboarding Flow**
- **Effort:** 12 hours
- **Impact:** High (user retention, reduced support)
- **Acceptance Criteria:**
  - Welcome screen with value proposition
  - Step-by-step setup wizard
  - Progress indicator
  - Skip option with reminders
- **Technical Notes:**
  - Create `OnboardingScreen` composable
  - Use `rememberSaveable` for onboarding state
  - Integrate with existing auth flow

#### Day 5: High Priority
**Task 1.6: Fix Calendar Navigation Inconsistencies**
- **Effort:** 4 hours
- **Impact:** Medium (better UX)
- **Acceptance Criteria:**
  - Prominent "Today" button
  - "Jump to Date" button in header
  - Consistent swipe navigation
  - Haptic feedback on navigation
- **Technical Notes:**
  - Update `CalendarHeader` component
  - Add haptic feedback to swipe handlers
  - Test on multiple devices

### Week 2: Features & Polish

#### Day 6-7: High Impact Features
**Task 2.1: Implement Quick Actions for Events**
- **Effort:** 8 hours
- **Impact:** High (faster event creation)
- **Acceptance Criteria:**
  - Floating action button menu with common event types
  - Quick-create dialog for simple events
  - Preset templates (school, doctor, activity)
- **Technical Notes:**
  - Use `ExtendedFloatingActionButton` with menu
  - Create `QuickEventDialog` component
  - Store templates in Room database

**Task 2.2: Add Event Preview on Long-Press**
- **Effort:** 6 hours
- **Impact:** Medium (better information discovery)
- **Acceptance Criteria:**
  - Long-press on event shows preview card
  - Preview shows title, date, time, description
  - Tap preview to edit, swipe to dismiss
- **Technical Notes:**
  - Use `ModalBottomSheet` for preview
  - Implement long-press detection on event cards
  - Add animations for smooth transitions

#### Day 8-9: Medium Priority
**Task 2.3: Implement Empty States**
- **Effort:** 4 hours
- **Impact:** Medium (better first-time experience)
- **Acceptance Criteria:**
  - Engaging empty states for calendar, events, child info
  - CTA buttons in empty states
  - Illustrations or Lottie animations
- **Technical Notes:**
  - Create `EmptyState` composable component
  - Use existing `LottieAnimations` utility
  - Add to all list screens

**Task 2.4: Add Custody Indicator to All Views**
- **Effort:** 4 hours
- **Impact:** Medium (consistency)
- **Acceptance Criteria:**
  - Today's custody shown in Day, 3-Day, Week views
  - Consistent styling across views
  - Smooth animations when custody changes
- **Technical Notes:**
  - Extract `CustodyIndicator` to shared component
  - Add to `DayWeekView` composable
  - Ensure proper data flow from ViewModel

#### Day 10: Testing & Documentation
**Task 2.5: Add Unit Tests for Critical ViewModels**
- **Effort:** 6 hours
- **Impact:** High (code quality, regression prevention)
- **Acceptance Criteria:**
  - `EventViewModel` tests (create, update, delete)
  - `CalendarViewModel` tests (view mode, date selection)
  - `PairingViewModel` tests (invitation flow)
  - >70% coverage for ViewModels
- **Technical Notes:**
  - Use MockK for mocking dependencies
  - Use Turbine for Flow testing
  - Follow existing test patterns

**Task 2.6: Update Documentation**
- **Effort:** 4 hours
- **Impact:** Medium (developer onboarding)
- **Acceptance Criteria:**
  - Update README with new features
  - Document new components in KDoc
  - Create CONTRIBUTING.md if missing
- **Technical Notes:**
  - Use KDoc format
  - Include code examples
  - Update architecture diagrams if needed

---

## Architecture Rules Summary

### 1. Clean Architecture Principles

**Layer Separation:**
- **Presentation:** UI components, ViewModels, Navigation
- **Domain:** Business logic, Use Cases, Repository interfaces, Models
- **Data:** Repository implementations, DataSources, Database, API clients

**Dependency Direction:**
```
Presentation → Domain ← Data
```
Domain layer must have NO dependencies on Presentation or Data layers.

**Rules:**
- ✅ ViewModels belong in Presentation layer
- ✅ Use Cases belong in Domain layer
- ✅ Repository interfaces in Domain, implementations in Data
- ❌ Never import Presentation/Data code into Domain

### 2. Component Design Rules

**Stateless Composables:**
- All `@Composable` functions must be stateless
- State passed as parameters from ViewModels
- Use `remember` for local UI state only (animations, temporary UI state)

**Example:**
```kotlin
// ❌ Bad: State in Composable
@Composable
fun MyScreen() {
    var count by remember { mutableStateOf(0) }
    // ...
}

// ✅ Good: State from ViewModel
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val count by viewModel.count.collectAsState()
    // ...
}
```

**Component Size:**
- Single responsibility per component
- Split components > 200 lines
- Extract reusable logic to separate functions
- Use composition over large monolithic components

### 3. State Management Rules

**StateFlow for State:**
- ViewModels expose `StateFlow` for UI state
- Use `asStateFlow()` for read-only access
- Prefer sealed classes for UI states

**Example:**
```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

**Flow Collection:**
- Always collect Flows in `viewModelScope.launch`
- Use `collectAsState()` in Composables
- Cancel collections when ViewModel is cleared

### 4. Dependency Injection Rules

**Hilt Usage:**
- All ViewModels: `@HiltViewModel`
- All Repositories: `@Singleton` (if stateless)
- Module organization by feature/domain
- Avoid constructor injection in Composables (use `hiltViewModel()`)

**Module Structure:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoPlanlyDatabase {
        // ...
    }
}
```

### 5. Repository Pattern Rules

**Interface in Domain:**
```kotlin
// domain/repository/EventRepository.kt
interface EventRepository {
    fun getAllEvents(): Flow<List<Event>>
    suspend fun insertEvent(event: Event)
}
```

**Implementation in Data:**
```kotlin
// data/repository/EventRepositoryImpl.kt
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val firestoreEventDataSource: FirestoreEventDataSource
) : EventRepository {
    // Implementation
}
```

**Rules:**
- Repository coordinates between local and remote sources
- Handle mapping between domain models and data entities
- Implement offline-first: local first, sync to remote
- Handle errors and retries appropriately

### 6. Error Handling Rules

**Consistent Error Types:**
```kotlin
sealed class AppError : Exception() {
    data class NetworkError(val message: String) : AppError()
    data class ValidationError(val field: String, val message: String) : AppError()
    data class PermissionError(val permission: String) : AppError()
    data class UnknownError(val message: String, val cause: Throwable?) : AppError()
}
```

**Error Propagation:**
- Catch exceptions in Repository layer
- Convert to domain `AppError` types
- Handle in ViewModels, update UI state
- Log to Crashlytics for unknown errors

### 7. Testing Rules

**Unit Tests:**
- Test all Use Cases
- Test ViewModels with mocked repositories
- Test Repository implementations with in-memory database
- Aim for >70% code coverage

**UI Tests:**
- Test critical user flows
- Use Compose Testing API
- Test navigation flows
- Test error states and empty states

**Test Structure:**
```
test/
├── domain/
│   └── usecase/
├── data/
│   └── repository/
└── presentation/
    └── viewmodel/

androidTest/
└── presentation/
    └── screen/
```

---

## PR Rules Summary

### PR Structure Template

```markdown
## Title
[Type]: Short and clear description

## Summary
Brief description of what was changed and why.

## Changes
- Change 1
- Change 2
- Change 3

## Technical Notes
### Dependencies
- Added: dependency-name:version
- Removed: dependency-name:version
- Updated: dependency-name:old-version → new-version

### Migrations
- Database migration: version X → Y (if applicable)
- API changes: description (if applicable)

### Testing
1. Manual testing steps:
   - Step 1
   - Step 2
2. Automated tests:
   - Unit tests: ✅/❌
   - UI tests: ✅/❌

## Risks
- Potential side effect 1
- Potential side effect 2

## Screenshots (if UI changes)
[Attach screenshots]

## Related Issues
Closes #issue-number
```

### PR Requirements Checklist

**Before Creating PR:**
- [ ] Code follows project architecture rules
- [ ] All tests pass locally
- [ ] Linter (Detekt) passes without errors
- [ ] Build succeeds (`./gradlew clean assembleDebug`)
- [ ] Code is self-documented (KDoc comments)
- [ ] No hardcoded strings (use `stringResource()`)
- [ ] Accessibility tested (TalkBack, contrast)

**PR Content:**
- [ ] Clear title following conventional commits
- [ ] Detailed summary explaining why, not just what
- [ ] List of all changes
- [ ] Technical notes (dependencies, migrations)
- [ ] Testing instructions
- [ ] Risk assessment
- [ ] Screenshots for UI changes

**Code Quality:**
- [ ] Atomic commits (one logical change per commit)
- [ ] No breaking changes (unless explicitly required)
- [ ] Follows architecture & style rules
- [ ] Explanations of architectural decisions
- [ ] Mini-diff overview in PR description

### Conventional Commits Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(calendar): Add quick action menu for event creation

Add floating action button menu with common event types
for faster event creation. Includes preset templates for
school, doctor, and activity events.

Closes #123
```

```
fix(auth): Handle token refresh errors gracefully

Previously, token refresh failures would crash the app.
Now shows error message and allows retry.

Fixes #456
```

### Review Process

**For Authors:**
1. Self-review PR before requesting review
2. Address all CI/CD failures
3. Respond to review comments promptly
4. Update PR description if scope changes

**For Reviewers:**
1. Review within 24 hours (if assigned)
2. Check architecture compliance
3. Verify tests are adequate
4. Test manually if UI changes
5. Approve or request changes with specific feedback

---

## Final Recommendations

### Immediate Actions (This Week)

1. **Fix Critical UX Issues**
   - Add deletion confirmation dialogs
   - Improve error messages
   - Fix accessibility issues
   - **Impact:** High user satisfaction, legal compliance

2. **Improve Code Quality**
   - Extract reusable components
   - Add unit tests for ViewModels
   - Implement consistent error handling
   - **Impact:** Maintainability, reduced bugs

3. **Enhance Onboarding**
   - Create welcome flow
   - Add guided setup
   - **Impact:** User retention, reduced support

### Short-Term (Next Month)

1. **Add High-Value Features**
   - In-app messaging
   - Expense tracker
   - Quick actions for events
   - **Impact:** Engagement, differentiation

2. **Begin AI Integration**
   - Start with natural language event creation
   - Implement smart scheduling suggestions
   - **Impact:** Competitive advantage, premium feature

3. **Improve Performance**
   - Optimize calendar rendering
   - Add pagination for events
   - Implement caching strategies
   - **Impact:** Scalability, user experience

### Medium-Term (3-6 Months)

1. **Monetization Setup**
   - Implement premium subscription
   - Add in-app purchases
   - Set up analytics for conversion tracking
   - **Impact:** Revenue generation

2. **B2B Development**
   - Build therapist/mediator dashboard
   - Create partnership program
   - **Impact:** New revenue stream

3. **Platform Expansion**
   - Begin iOS development (KMM)
   - Create web dashboard
   - **Impact:** Market expansion

### Long-Term (6-12 Months)

1. **Advanced AI Features**
   - Full AI co-pilot suite
   - Predictive analytics
   - Automated agreement generation
   - **Impact:** Premium differentiation

2. **Ecosystem Building**
   - Marketplace for services
   - Integration partnerships
   - Community features
   - **Impact:** Network effects, retention

3. **Enterprise Features**
   - White-label solution
   - API access for partners
   - Custom branding
   - **Impact:** B2B revenue growth

### Success Metrics

**User Engagement:**
- Daily Active Users (DAU) / Monthly Active Users (MAU) ratio > 40%
- Average session duration > 5 minutes
- Events created per user per month > 10

**Retention:**
- Day 1 retention > 50%
- Day 7 retention > 30%
- Day 30 retention > 20%

**Monetization:**
- Free-to-Premium conversion > 5%
- Monthly Churn Rate < 5%
- Average Revenue Per User (ARPU) > $5/month

**Technical:**
- App crash rate < 0.1%
- API response time < 500ms (p95)
- Test coverage > 70%

### Risk Mitigation

**Technical Risks:**
- **AI API Costs:** Start with low-cost models, implement caching, consider on-device models
- **Scalability:** Implement pagination early, use Firestore efficiently, monitor costs
- **Data Privacy:** Ensure GDPR/CCPA compliance, implement data encryption, regular audits

**Business Risks:**
- **Competition:** Focus on AI differentiation, build strong user relationships
- **Market Fit:** Conduct user interviews, iterate based on feedback, pivot if needed
- **Monetization:** Test pricing early, offer multiple tiers, provide clear value

---

## Conclusion

CoPlanly has a solid foundation with modern architecture, clean code, and a clear value proposition. The roadmap outlined above focuses on:

1. **Immediate improvements** to UX and code quality
2. **High-impact features** that drive engagement and retention
3. **AI integration** for competitive differentiation
4. **Monetization** strategies for sustainable growth

By following this roadmap, CoPlanly can become the leading co-parenting calendar application with a sustainable business model and loyal user base.

**Next Steps:**
1. Review and prioritize roadmap items with team
2. Set up project management (Jira, Linear, etc.)
3. Begin Week 1 tasks immediately
4. Schedule monthly roadmap reviews

---

**Document Version:** 1.0
**Last Updated:** January 2025
**Next Review:** February 2025
