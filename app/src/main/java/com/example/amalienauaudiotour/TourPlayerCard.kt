package com.example.amalienauaudiotour

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Плавающая кнопка центровки карты на местоположении пользователя. */
@Composable
fun LocateMeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text("🎯", style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Нижняя контрольная панель плеера: заголовок и описание текущей точки (или вступления)
 * плюс кнопка воспроизведения/паузы.
 */
@Composable
fun TourPlayerCard(
    modifier: Modifier = Modifier,
    selectedStop: TourStop?,
    isThisTrackPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val titleText = selectedStop?.title ?: "Введение"
    val descText = selectedStop?.description
        ?: "Добро пожаловать в Амалиенау! Выберите точку на карте или нажмите «СЛУШАТЬ ВВЕДЕНИЕ»."

    Card(
        modifier = modifier.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = titleText, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Text(text = descText, style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = onTogglePlay,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                val buttonText = when {
                    isThisTrackPlaying -> "⏸ ПАУЗА"
                    selectedStop != null -> "▶️ ВОСПРОИЗВЕСТИ АУДИО"
                    else -> "🎧 СЛУШАТЬ ВВЕДЕНИЕ"
                }
                Text(text = buttonText)
            }
        }
    }
}
