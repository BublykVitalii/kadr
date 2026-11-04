package com.lagradost.cloudstream3.kadr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.home.LoadClickCallback
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper

/** Кольори з макета «Вечорниці» */
private object K {
    val bg = Color(0xFF0E0F13)
    val panel = Color(0xFF1B1F29)
    val card = Color(0xFF1A1C23)
    val chipBorder = Color(0xFF2A2D36)
    val text = Color(0xFFF2F2F4)
    val muted = Color(0xFFA0A3AD)
    val accent = Color(0xFFFFB020)
    val onAccent = Color(0xFF14120B)
}

/**
 * Нова головна «Вечорниці» (Compose). Дані бере з того самого HomeViewModel,
 * що й стара головна CloudStream, тож плагіни працюють як раніше.
 */
class KadrHomeFragment : Fragment() {
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KadrHomeScreen(
                vm = homeViewModel,
                onSearch = { openSearch() },
                onPickSource = { pickSource() },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.reloadStored()
        homeViewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.reloadStored()
    }

    private fun openSearch() {
        try {
            findNavController().navigate(R.id.navigation_search)
        } catch (_: Throwable) {
        }
    }

    private fun pickSource() {
        val ctx = context ?: return
        with(HomeFragment.Companion) {
            ctx.selectHomepage(homeViewModel.apiName.value) { api ->
                homeViewModel.loadAndCancel(api, forceReload = true, fromUI = true)
            }
        }
    }
}

@Composable
private fun <T> LiveData<T>.asState(): State<T?> {
    val state = remember(this) { mutableStateOf(this.value) }
    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }
    return state
}

@Composable
private fun KadrHomeScreen(
    vm: HomeViewModel,
    onSearch: () -> Unit,
    onPickSource: () -> Unit,
) {
    val apiName by vm.apiName.asState()
    val preview by vm.preview.asState()
    val resume by vm.resumeWatching.asState()
    val page by vm.page.asState()
    val view = LocalView.current

    val openCard: (SearchResponse) -> Unit = { card ->
        SearchHelper.handleSearchClickCallback(
            SearchClickCallback(SEARCH_ACTION_LOAD, view, -1, card)
        )
    }

    val hero: LoadResponse? =
        (preview as? Resource.Success)?.value?.second?.firstOrNull()
    val lists = (page as? Resource.Success)?.value?.values?.toList().orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(K.bg),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            TopBar(
                source = apiName,
                onPickSource = onPickSource,
                onSearch = onSearch
            )
        }

        if (hero != null) {
            item {
                HeroBanner(hero) {
                    vm.click(LoadClickCallback(0, view, 0, hero))
                }
            }
        }

        val resumeList = resume.orEmpty()
        if (resumeList.isNotEmpty()) {
            item {
                Section("Продовжити перегляд") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(resumeList) { card -> ResumeCard(card) { openCard(card) } }
                    }
                }
            }
        }

        if (page is Resource.Loading && lists.isEmpty()) {
            item { Text("Завантажую…", color = K.muted, fontSize = 15.sp) }
        }
        if (page is Resource.Failure) {
            item {
                Text(
                    "Не вдалося завантажити сайт. Спробуй інше джерело.",
                    color = K.muted, fontSize = 15.sp
                )
            }
        }

        items(lists) { expandable ->
            val list = expandable.list
            Section(list.name) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(list.list) { card ->
                        if (list.isHorizontalImages) {
                            WideCard(card) { openCard(card) }
                        } else {
                            PosterCard(card) { openCard(card) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(source: String?, onPickSource: () -> Unit, onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(K.accent)
                .clickable { onPickSource() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Сайт: ${source ?: "обрати"}  ▾",
                color = K.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(K.card)
                .clickable { onSearch() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Пошук на всіх сайтах", color = K.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HeroBanner(hero: LoadResponse, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF2B3242))
            .clickable { onOpen() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .background(K.panel)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
        ) {
            val meta = listOfNotNull(
                hero.year?.toString(),
                hero.tags?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                hero.apiName
            ).joinToString(" · ")
            Text(meta, color = Color(0xFFB8BBC4), fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                hero.name, color = K.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp
            )
            hero.plot?.let {
                Text(it, color = Color(0xFFB8BBC4), fontSize = 14.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(K.accent)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("▶  Дивитись", color = K.onAccent, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Image(
            url = hero.backgroundPosterUrl ?: hero.posterUrl,
            headers = hero.posterHeaders,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = K.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ResumeCard(card: SearchResponse, onClick: () -> Unit) {
    val resume = card as? DataStoreHelper.ResumeWatchingResult
    val pos = resume?.watchPos
    val progress = if (pos != null && pos.duration > 0)
        (pos.position.toFloat() / pos.duration).coerceIn(0f, 1f) else 0f
    val episode = listOfNotNull(
        resume?.season?.let { "С$it" },
        resume?.episode?.let { "Е$it" }
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(K.card)
        ) {
            Image(card.posterUrl, card.posterHeaders, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x2EFFFFFF))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(K.accent)
            )
        }
        Text(card.name, color = K.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (episode.isNotEmpty()) {
            Text(episode, color = K.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PosterCard(card: SearchResponse, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(K.card)
        ) {
            Image(card.posterUrl, card.posterHeaders, Modifier.fillMaxSize())
            card.quality?.let { q ->
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xD90E0F13))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(q.name, color = K.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(card.name, color = K.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WideCard(card: SearchResponse, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(K.card)
                .border(1.dp, K.chipBorder, RoundedCornerShape(14.dp))
        ) {
            Image(card.posterUrl, card.posterHeaders, Modifier.fillMaxSize())
        }
        Text(card.name, color = K.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Image(url: String?, headers: Map<String, String>?, modifier: Modifier) {
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
