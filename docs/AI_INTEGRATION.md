# CoPlanly AI Integration

## Overview
This document describes the AI integration implemented in CoPlanly using Google Gemini API.

## Features Implemented

### Day 1: Smart Scheduling Assistant

#### 1.1 AI-powered Conflict Resolution
- **Location**: `domain/usecase/ai/SmartSchedulingUseCase.kt`
- **Description**: Automatically detects scheduling conflicts and suggests optimal time slots
- **Key Components**:
  - `SmartSchedulingUseCase`: Main use case for finding optimal time slots
  - `ConflictDetector`: Detects conflicts in parent calendars
  - AI analyzes parent schedules and provides intelligent suggestions

#### 1.2 Proactive Conflict Alerts
- **Location**: `domain/usecase/ai/ProactiveConflictMonitorUseCase.kt`
- **UI**: `presentation/ai/ConflictAlertCard.kt`
- **ViewModel**: `presentation/ai/ConflictMonitorViewModel.kt`
- **Description**: Monitors upcoming events and proactively alerts about potential conflicts
- **Features**:
  - 7-day lookahead for conflict detection
  - Severity levels (LOW, MEDIUM, HIGH)
  - Suggested solutions for each conflict
  - Dismissible alerts

### Day 2: Natural Language Event Creation

#### 2.1 Voice-to-Event Conversion
- **Location**: `domain/usecase/ai/NaturalLanguageEventParserUseCase.kt`
- **UI**: `presentation/ai/NaturalLanguageEventScreen.kt`
- **ViewModel**: `presentation/ai/NaturalLanguageEventViewModel.kt`
- **Description**: Parse natural language text into structured events
- **Features**:
  - Understands common event patterns
  - Extracts: title, date/time, duration, location, type
  - Confidence scoring
  - Validation before saving
  - Edit capability after parsing

#### 2.2 Smart Event Suggestions
- **Location**: `domain/usecase/ai/EventSuggestionEngineUseCase.kt`
- **UI**: `presentation/ai/EventSuggestionsScreen.kt`
- **ViewModel**: `presentation/ai/EventSuggestionsViewModel.kt`
- **Description**: AI-generated event suggestions based on user history
- **Categories**:
  - RECURRING: Based on regular patterns
  - SEASONAL: Time-appropriate activities
  - AGE_APPROPRIATE: Child age-based suggestions
  - LOCATION_BASED: Local activities
  - WEATHER_BASED: Weather-appropriate suggestions
- **User Actions**: Accept, Reject, Modify, Ignore

## Architecture

### Domain Layer
```
domain/
├── model/ai/
│   ├── SchedulingModels.kt      # Conflict, TimeSlot, Calendar models
│   └── NaturalLanguageModels.kt # Parsing, Suggestion models
└── usecase/ai/
    ├── SmartSchedulingUseCase.kt
    ├── ProactiveConflictMonitorUseCase.kt
    ├── NaturalLanguageEventParserUseCase.kt
    └── EventSuggestionEngineUseCase.kt
```

### Data Layer
```
data/remote/ai/
├── AIService.kt          # Interface for AI operations
└── GeminiAIService.kt    # Gemini API implementation
```

### Presentation Layer
```
presentation/ai/
├── ConflictAlertCard.kt              # UI for conflict alerts
├── ConflictMonitorViewModel.kt       # State management
├── EventSuggestionsScreen.kt         # UI for suggestions
├── EventSuggestionsViewModel.kt      # State management
├── NaturalLanguageEventScreen.kt     # UI for text parsing
└── NaturalLanguageEventViewModel.kt  # State management
```

### Dependency Injection
```
di/
└── AIModule.kt           # Provides AI service dependencies
```

## Configuration

### Setup Gemini API Key

1. **Option 1: gradle.properties**
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```

2. **Option 2: Environment Variable**
   ```bash
   export GEMINI_API_KEY=your_api_key_here
   ```

3. **Option 3: BuildConfig (for CI/CD)**
   The API key is configured in `build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""}\"")
   ```

### Dependencies Added
```kotlin
// Generative AI - Gemini API
implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

// Retrofit for AI API calls
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// OkHttp for HTTP client
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
```

## Usage Examples

### 1. Smart Scheduling
```kotlin
val smartSchedulingUseCase: SmartSchedulingUseCase = // injected
val eventRequest = EventRequest(
    title = "Soccer Practice",
    duration = Duration.ofHours(2),
    preferredTimes = listOf(LocalDateTime.now().plusDays(1)),
    eventType = "sports"
)

val suggestions = smartSchedulingUseCase.findOptimalTimeSlot(
    eventRequest,
    parentCalendars
)
```

### 2. Proactive Conflict Monitoring
```kotlin
val viewModel: ConflictMonitorViewModel = hiltViewModel()
viewModel.checkForConflicts()

val conflictsState by viewModel.conflictsState.collectAsState()
when (conflictsState) {
    is ConflictsUiState.Success -> {
        // Display conflicts
    }
}
```

### 3. Natural Language Parsing
```kotlin
val viewModel: NaturalLanguageEventViewModel = hiltViewModel()
viewModel.parseEventFromText("Doctor appointment next Tuesday at 2pm")

val parsingState by viewModel.parsingState.collectAsState()
when (parsingState) {
    is ParsingUiState.Success -> {
        // Show parsed event for confirmation
    }
}
```

### 4. Event Suggestions
```kotlin
val viewModel: EventSuggestionsViewModel = hiltViewModel()
viewModel.loadSuggestions(
    userId = currentUserId,
    context = SuggestionContext(
        userId = currentUserId,
        childAge = 8,
        location = "San Francisco"
    )
)
```

## AI Prompt Engineering

The implementation uses carefully crafted prompts for optimal results:

### Scheduling Prompts
- Include event details, conflicts, and parent schedules
- Ask for specific JSON format responses
- Consider work schedules, child routines, travel time

### Natural Language Parsing Prompts
- Provide context (current date, timezone, recent events)
- Define extraction requirements clearly
- Include common pattern examples
- Request confidence scoring

### Suggestion Prompts
- Analyze 90-day event history
- Consider child age, location, weather
- Request categorized suggestions
- Include reasoning for each suggestion

## Testing

To test AI features:

1. **Add API Key** (as described in Configuration)
2. **Run the app**
3. **Test Features**:
   - Create events with natural language
   - Check for conflict alerts
   - View AI-generated suggestions
   - Accept/reject suggestions

## Next Steps

### Future Enhancements (Days 3-4)
- **Day 3: Communication Intelligence**
  - Tone analysis for messages
  - Smart conversation summaries

- **Day 4: Family Insights & Analytics**
  - AI-powered parenting insights
  - Predictive analytics

## Troubleshooting

### Common Issues

1. **API Key not found**
   - Ensure GEMINI_API_KEY is set in gradle.properties or environment
   - Rebuild project after adding key

2. **Network errors**
   - Check internet connection
   - Verify API key is valid
   - Check Logcat for detailed errors

3. **Parsing failures**
   - AI might not understand very ambiguous text
   - Try more specific descriptions
   - Check logs for raw AI responses

## Performance Considerations

- AI requests are async and don't block UI
- Responses are cached where appropriate
- Network timeouts are set to 30 seconds
- Loading states are shown during AI processing

## Security

- API key is stored in BuildConfig (not committed to git)
- Sensitive data is not sent to AI (only event metadata)
- All AI requests go through secure HTTPS

## Contributing

When adding new AI features:
1. Create domain models in `domain/model/ai/`
2. Implement use case in `domain/usecase/ai/`
3. Add UI in `presentation/ai/`
4. Update this documentation
