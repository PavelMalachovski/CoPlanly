package com.coparently.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*

/**
 * Компонент для отображения Empty State с Lottie анимацией.
 * Используется когда нет данных для отображения (например, нет событий).
 *
 * @param title Заголовок empty state
 * @param description Описание empty state
 * @param modifier Модификатор для кастомизации
 * @param animationResId ID ресурса Lottie анимации (по умолчанию используется fallback)
 */
@Composable
fun LottieEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    animationResId: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie анимация
        // Если animationResId предоставлен, используем его, иначе показываем простой плейсхолдер
        if (animationResId != null) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(animationResId)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = true
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 24.dp)
            )
        } else {
            // Fallback: простой box плейсхолдер
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📭",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Заголовок
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Описание
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Компонент для отображения Success State с Lottie анимацией.
 * Используется после успешного выполнения операции (создание, обновление, удаление).
 *
 * @param message Сообщение об успехе
 * @param modifier Модификатор для кастомизации
 * @param animationResId ID ресурса Lottie анимации (по умолчанию используется fallback)
 */
@Composable
fun LottieSuccessState(
    message: String,
    modifier: Modifier = Modifier,
    animationResId: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie анимация (играет один раз)
        if (animationResId != null) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(animationResId)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = 1,
                isPlaying = true
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp)
            )
        } else {
            // Fallback: простой emoji
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✅",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Сообщение
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Компонент для отображения Error State с Lottie анимацией.
 * Используется при возникновении ошибки.
 *
 * @param message Сообщение об ошибке
 * @param modifier Модификатор для кастомизации
 * @param animationResId ID ресурса Lottie анимации (по умолчанию используется fallback)
 * @param onRetry Callback для повторной попытки (опционально)
 */
@Composable
fun LottieErrorState(
    message: String,
    modifier: Modifier = Modifier,
    animationResId: Int? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie анимация
        if (animationResId != null) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(animationResId)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = true
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .size(160.dp)
                    .padding(bottom = 24.dp)
            )
        } else {
            // Fallback: простой emoji
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Сообщение об ошибке
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )

        // Кнопка повторной попытки
        onRetry?.let { retry ->
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = retry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * Компонент для отображения Loading State с Lottie анимацией.
 * Используется во время загрузки данных.
 *
 * @param modifier Модификатор для кастомизации
 * @param animationResId ID ресурса Lottie анимации (по умолчанию используется fallback)
 * @param message Опциональное сообщение о загрузке
 */
@Composable
fun LottieLoadingState(
    modifier: Modifier = Modifier,
    animationResId: Int? = null,
    message: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie анимация
        if (animationResId != null) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(animationResId)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = true
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )
        } else {
            // Fallback: CircularProgressIndicator
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Сообщение о загрузке
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Компонент-обертка для автоматического отображения состояний с Lottie анимациями.
 *
 * @param uiState Текущее состояние UI
 * @param onRetry Callback для повторной попытки при ошибке
 * @param emptyStateTitle Заголовок для empty state
 * @param emptyStateDescription Описание для empty state
 * @param content Контент для отображения при успешном состоянии
 */
@Composable
fun <T> LottieStateHandler(
    uiState: T,
    onRetry: () -> Unit = {},
    emptyStateTitle: String = "No Data",
    emptyStateDescription: String = "There's nothing to show here yet",
    content: @Composable (T) -> Unit
) {
    when (uiState) {
        is com.coparently.app.presentation.event.EventUiState -> {
            when (uiState) {
                is com.coparently.app.presentation.event.EventUiState.Loading -> {
                    LottieLoadingState(
                        message = "Loading events..."
                    )
                }
                is com.coparently.app.presentation.event.EventUiState.Success -> {
                    if (uiState.events.isEmpty()) {
                        LottieEmptyState(
                            title = emptyStateTitle,
                            description = emptyStateDescription
                        )
                    } else {
                        content(uiState as T)
                    }
                }
                is com.coparently.app.presentation.event.EventUiState.Error -> {
                    LottieErrorState(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }
                is com.coparently.app.presentation.event.EventUiState.OperationSuccess -> {
                    LottieSuccessState(
                        message = uiState.message
                    )
                }
            }
        }
        else -> {
            content(uiState)
        }
    }
}
