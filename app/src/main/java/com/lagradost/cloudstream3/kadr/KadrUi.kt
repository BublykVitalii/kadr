package com.lagradost.cloudstream3.kadr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.USER_AGENT

/** Кольори з макета «Вечорниці» */
internal object KColors {
    val bg = Color(0xFF0E0F13)
    val panel = Color(0xFF14161B)
    val heroPanel = Color(0xFF1B1F29)
    val card = Color(0xFF1A1C23)
    val chipBorder = Color(0xFF2A2D36)
    val text = Color(0xFFF2F2F4)
    val muted = Color(0xFFA0A3AD)
    val accent = Color(0xFFFFB020)
    val onAccent = Color(0xFF14120B)
}

/** LiveData → Compose State без додаткових бібліотек */
@Composable
internal fun <T> LiveData<T>.asState(): State<T?> {
    val state = remember(this) { mutableStateOf(this.value) }
    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }
    return state
}

/** Картинка з тими ж заголовками, що й у CloudStream (деякі сайти без них не віддають постери) */
@Composable
internal fun KImage(url: String?, headers: Map<String, String>?, modifier: Modifier) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(NetworkHeaders.Builder().also { builder ->
                builder["User-Agent"] = USER_AGENT
                headers?.forEach { (key, value) -> builder[key] = value }
            }.build())
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** Кругла кнопка-чіп */
@Composable
internal fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, if (selected) KColors.accent else KColors.chipBorder, shape)
            .background(if (selected) KColors.accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) KColors.onAccent else Color(0xFFD4D6DC),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
