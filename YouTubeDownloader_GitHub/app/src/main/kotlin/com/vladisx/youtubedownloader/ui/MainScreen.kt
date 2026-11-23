package com.vladisx.youtubedownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladisx.youtubedownloader.data.AudioCodec
import com.vladisx.youtubedownloader.data.DownloadKind
import com.vladisx.youtubedownloader.data.DownloadState
import com.vladisx.youtubedownloader.data.MediaInfo
import com.vladisx.youtubedownloader.ui.theme.CosmicCard
import com.vladisx.youtubedownloader.ui.theme.Ion
import com.vladisx.youtubedownloader.ui.theme.Nebula
import com.vladisx.youtubedownloader.ui.theme.Solar
import com.vladisx.youtubedownloader.ui.theme.cosmicAccentBrush
import com.vladisx.youtubedownloader.ui.theme.cosmicBorderBrush
import com.vladisx.youtubedownloader.ui.theme.cosmicSelectedBrush

private const val AUTHOR = "Vladislav Phismatovich"

@Composable
fun MainScreen(
    ui: UiState,
    downloadState: DownloadState,
    onUrlChange: (String) -> Unit,
    onPasteRequested: (String) -> Unit,
    onKindChange: (DownloadKind) -> Unit,
    onAudioCodecChange: (AudioCodec) -> Unit,
    onQualityChange: (Int) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit,
    signedIn: Boolean,
    onLoginClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val busy = downloadState is DownloadState.Running ||
        downloadState is DownloadState.Preparing ||
        downloadState is DownloadState.Merging

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { Header() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UrlInput(
                url = ui.url,
                enabled = !busy,
                isLoading = ui.isLoadingInfo,
                onUrlChange = onUrlChange,
                onPaste = {
                    val text = clipboard.getText()?.text
                    if (!text.isNullOrBlank()) onPasteRequested(text.trim())
                },
                onSubmit = {
                    when {
                        ui.url.isBlank() -> {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrBlank()) onPasteRequested(text.trim())
                        }
                        !ui.isLoadingInfo && ui.media == null -> onPasteRequested(ui.url.trim())
                    }
                },
            )

            ui.initError?.let { ErrorCard("Помилка ініціалізації yt-dlp", it) }
            ui.infoError?.let { ErrorCard("Не вдалось отримати відео", it) }

            AccountCard(
                signedIn = signedIn,
                enabled = !busy,
                onLoginClick = onLoginClick,
                onSignOutClick = onSignOutClick,
            )

            if (ui.isLoadingInfo) LoadingCard()

            ui.media?.let { media ->
                VideoCard(media)

                SectionTitle("Що завантажити?", Icons.Filled.AutoAwesome)
                KindSelector(kind = ui.kind, enabled = !busy, onKindChange = onKindChange)

                if (ui.kind == DownloadKind.AUDIO_ONLY) {
                    AudioCodecSelector(
                        codec = ui.audioCodec,
                        enabled = !busy,
                        onChange = onAudioCodecChange,
                    )
                }

                if (ui.kind != DownloadKind.AUDIO_ONLY) {
                    QualitySelector(
                        ui = ui,
                        enabled = !busy,
                        onQualityChange = onQualityChange,
                    )
                }

                DownloadButton(
                    ui = ui,
                    downloadState = downloadState,
                    enabled = !busy && ui.canDownload,
                    onDownload = onDownload,
                )

                ProgressSection(
                    downloadState = downloadState,
                    onCancel = onCancel,
                    onDismiss = onDismissResult,
                )
            }

            if (ui.media == null && !ui.isLoadingInfo && ui.infoError == null) {
                HintCard()
            }
        }
    }
}

/* ------------------------------------------------------------------ */

/**
 * Header: a single cosmic signature badge with the author name, dead-centre.
 */
@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Nebula.copy(alpha = 0.22f),
                            Ion.copy(alpha = 0.15f),
                        )
                    )
                )
                .border(1.dp, cosmicBorderBrush(), CircleShape)
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Ion,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                AUTHOR,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Ion, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ion,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun UrlInput(
    url: String,
    enabled: Boolean,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        label = { Text("Посилання на YouTube") },
        placeholder = { Text("https://youtube.com/watch?v=…") },
        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = Ion) },
        trailingIcon = {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Nebula,
                )
                url.isNotBlank() -> IconButton(onClick = { onUrlChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Очистити")
                }
                else -> IconButton(onClick = onPaste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Вставити")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    )
}

/**
 * Sign-in card. Cookies are what unlock 1080p+ — without them YouTube answers
 * 403 on every high-resolution DASH stream.
 */
@Composable
private fun AccountCard(
    signedIn: Boolean,
    enabled: Boolean,
    onLoginClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val accent = if (signedIn) Ion else MaterialTheme.colorScheme.onSurfaceVariant

    CosmicCard(modifier = Modifier.fillMaxWidth(), glow = signedIn) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (signedIn) Icons.Filled.CheckCircle else Icons.Filled.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (signedIn) "YouTube підключено" else "Вхід у YouTube",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (signedIn) Ion else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (signedIn) "Доступна максимальна якість (1080p+)"
                    else "Потрібен для 1080p, 1440p і 4K",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ion.copy(alpha = 0.8f),
                )
            }
            TextButton(
                onClick = if (signedIn) onSignOutClick else onLoginClick,
                enabled = enabled,
            ) {
                Text(
                    if (signedIn) "Вийти" else "Увійти",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    CosmicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Ion,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Отримуємо інформацію про відео…",
                style = MaterialTheme.typography.bodyMedium,
                color = Ion,
            )
        }
    }
}

@Composable
private fun HintCard() {
    CosmicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Ion,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Підтримка посилань",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Ion,
                )
            }
            Text(
                "• youtube.com/watch?v=…\n• youtu.be/…\n• youtube.com/shorts/…",
                style = MaterialTheme.typography.bodySmall,
                color = Ion.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ErrorCard(title: String, message: String) {
    val error = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(error.copy(alpha = 0.12f))
            .border(1.dp, error.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = error,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ion,
                )
            }
        }
    }
}

@Composable
private fun VideoCard(media: MediaInfo) {
    CosmicCard(modifier = Modifier.fillMaxWidth(), glow = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                media.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ion,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                media.uploader?.let { add(it) }
                media.durationSeconds?.let { add(formatDuration(it)) }
                if (media.isLive) add("LIVE")
                if (media.webpageUrl.contains("/shorts/")) add("Short")
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ion,
                )
            }
        }
    }
}

@Composable
private fun KindSelector(
    kind: DownloadKind,
    enabled: Boolean,
    onKindChange: (DownloadKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip(
                label = "Відео + звук",
                icon = Icons.Filled.Movie,
                selected = kind == DownloadKind.VIDEO_WITH_AUDIO,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onKindChange(DownloadKind.VIDEO_WITH_AUDIO) },
            )
            KindChip(
                label = "Тільки аудіо",
                icon = Icons.Filled.MusicNote,
                selected = kind == DownloadKind.AUDIO_ONLY,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onKindChange(DownloadKind.AUDIO_ONLY) },
            )
        }
        KindChip(
            label = "Відео без звуку",
            icon = Icons.Filled.Videocam,
            selected = kind == DownloadKind.VIDEO_NO_AUDIO,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onKindChange(DownloadKind.VIDEO_NO_AUDIO) },
        )
    }
}

@Composable
private fun KindChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Nebula,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            labelColor = Ion,
            iconColor = Ion,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            selectedBorderColor = Solar.copy(alpha = 0.9f),
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Composable
private fun AudioCodecSelector(
    codec: AudioCodec,
    enabled: Boolean,
    onChange: (AudioCodec) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Формат аудіо",
            style = MaterialTheme.typography.labelLarge,
            color = Ion,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioCodec.entries.forEach { option ->
                FilterChip(
                    selected = codec == option,
                    onClick = { onChange(option) },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(option.label) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Audiotrack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Nebula,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        labelColor = Ion,
                        iconColor = Ion,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = enabled,
                        selected = codec == option,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        selectedBorderColor = Solar.copy(alpha = 0.9f),
                        selectedBorderWidth = 1.dp,
                    ),
                )
            }
        }
    }
}

/**
 * Quality picker: every resolution the video can actually be downloaded at.
 * DASH streams (720p+) are marked because they get merged with the best audio.
 */
@Composable
private fun QualitySelector(
    ui: UiState,
    enabled: Boolean,
    onQualityChange: (Int) -> Unit,
) {
    val selected = ui.selectedQuality ?: return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.HighQuality,
                contentDescription = null,
                tint = Ion,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Якість відео",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ion,
            )
            Spacer(Modifier.weight(1f))
            selected.estimatedBytes?.let {
                Text(
                    "≈ ${formatBytes(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ion,
                )
            }
        }

        ui.qualities.forEach { quality ->
            QualityRow(
                label = quality.label,
                hint = if (quality.isMergeRequired) "відео + аудіо" else "готовий файл",
                selected = quality.height == selected.height,
                enabled = enabled,
                onClick = { onQualityChange(quality.height) },
            )
        }

        val maxHeight = ui.maxHeight
        if (maxHeight != null && maxHeight >= 1080) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Доступна максимальна безкоштовна якість до ${maxHeight}p. " +
                        "Високі роздільності качаються окремими потоками та зливаються ffmpeg.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ion.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val fill: Brush = if (selected) cosmicSelectedBrush()
    else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        )
    )
    val border: Brush = if (selected) cosmicBorderBrush()
    else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Ion,
        )
        Spacer(Modifier.weight(1f))
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = Ion.copy(alpha = 0.75f),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Ion,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DownloadButton(
    ui: UiState,
    downloadState: DownloadState,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    val label = when (ui.kind) {
        DownloadKind.AUDIO_ONLY -> "Завантажити аудіо"
        DownloadKind.VIDEO_NO_AUDIO -> "Завантажити відео без звуку"
        DownloadKind.VIDEO_WITH_AUDIO -> "Завантажити відео"
    }

    val active = enabled && downloadState !is DownloadState.Running
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
                if (active) cosmicAccentBrush()
                else Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                )
            )
            .border(
                1.dp,
                if (active) Solar.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape,
            )
            .clickable(enabled = active, onClick = onDownload),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = if (active) Color.White else Ion,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                color = if (active) Color.White else Ion,
            )
        }
    }
}

@Composable
private fun ProgressSection(
    downloadState: DownloadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = downloadState !is DownloadState.Idle) {
        CosmicCard(modifier = Modifier.fillMaxWidth(), glow = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (downloadState) {
                    is DownloadState.Preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Ion,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            downloadState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ion,
                        )
                    }

                    is DownloadState.Running -> {
                        val animated by animateFloatAsState(
                            targetValue = (downloadState.percent / 100.0).toFloat(),
                            label = "progress",
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                downloadState.stage,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Ion,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${downloadState.percent.toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Ion,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Nebula,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        val details = buildList {
                            if (downloadState.totalBytes > 0) {
                                add(
                                    "${formatBytes(downloadState.downloadedBytes)} / " +
                                        formatBytes(downloadState.totalBytes)
                                )
                            }
                            downloadState.speed?.let { add(it) }
                            downloadState.eta?.let { add("залишилось $it") }
                        }
                        if (details.isNotEmpty()) {
                            Text(
                                details.joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Ion.copy(alpha = 0.8f),
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Скасувати")
                        }
                    }

                    is DownloadState.Merging -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Ion,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            downloadState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ion,
                        )
                    }

                    is DownloadState.Completed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Ion,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Готово",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Ion,
                            )
                            Text(
                                downloadState.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Ion.copy(alpha = 0.8f),
                            )
                            Text(
                                "Download/YouTubeDownloader",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ion,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрити")
                        }
                    }

                    is DownloadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            downloadState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрити")
                        }
                    }

                    DownloadState.Idle -> Unit
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes B" else "%.1f %s".format(value, units[index])
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
