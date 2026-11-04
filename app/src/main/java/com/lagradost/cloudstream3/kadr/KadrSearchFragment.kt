package com.lagradost.cloudstream3.kadr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import kotlinx.coroutines.delay

/** Один тайтл, знайдений на кількох сайтах */
private data class MergedResult(
    val main: SearchResponse,
    val sites: List<SearchResponse>,
    val year: Int?,
)

private enum class TypeFilter(val label: String) {
    ALL("Усе"), SERIES("Серіали"), MOVIES("Фільми"), ANIME("Аніме"), CARTOONS("Мультфільми");

    fun matches(type: TvType?): Boolean = when (this) {
        ALL -> true
        SERIES -> type == TvType.TvSeries || type == TvType.AsianDrama
        MOVIES -> type == TvType.Movie || type == TvType.Documentary
        ANIME -> type == TvType.Anime || type == TvType.AnimeMovie || type == TvType.OVA
        CARTOONS -> type == TvType.Cartoon
    }
}

private fun TvType?.uaName(): String = when (this) {
    TvType.Movie -> "Фільм"
    TvType.TvSeries -> "Серіал"
    TvType.Anime -> "Аніме"
    TvType.AnimeMovie -> "Аніме-фільм"
    TvType.OVA -> "OVA"
    TvType.Cartoon -> "Мультфільм"
    TvType.AsianDrama -> "Дорама"
    TvType.Documentary -> "Документальний"
    null -> ""
    else -> this?.name ?: ""
}

private fun normalize(name: String): String =
    name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Рік, якщо сайт його віддав */
private fun SearchResponse.yearOrNull(): Int? = when (this) {
    is MovieSearchResponse -> year
    is TvSeriesSearchResponse -> year
    is AnimeSearchResponse -> year
    else -> null
}

/**
 * Зводить результати з усіх сайтів: однакова назва + однаковий рік → одна картка.
 * Якщо на якомусь сайті року немає — приєднуємо до найбільшої групи з такою назвою.
 */
private fun merge(bySite: Map<String, List<SearchResponse>>): List<MergedResult> {
    val byName = LinkedHashMap<String, MutableList<SearchResponse>>()
    bySite.values.forEach { list ->
        list.forEach { card ->
            val key = normalize(card.name)
            if (key.isNotEmpty()) byName.getOrPut(key) { mutableListOf() }.add(card)
        }
    }
    val result = mutableListOf<MergedResult>()
    byName.values.forEach { cards ->
        val withYear = cards.filter { it.yearOrNull() != null }.groupBy { it.yearOrNull() }
        val noYear = cards.filter { it.yearOrNull() == null }
        val groups = withYear.values.map { it.toMutableList() }.toMutableList()
        if (groups.isEmpty()) groups.add(mutableListOf()) 
        groups.maxByOrNull { it.size }?.addAll(noYear)
        groups.filter { it.isNotEmpty() }.forEach { group ->
            val unique = group.distinctBy { it.apiName }
            result.add(
                MergedResult(
                    main = unique.firstOrNull { it.posterUrl != null } ?: unique.first(),
                    sites = unique,
                    year = group.firstNotNullOfOrNull { it.yearOrNull() }
                )
            )
        }
    }
    return result.sortedByDescending { it.sites.size }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class KadrSearchFragment : Fragment() {
    private val searchViewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        val initial = arguments?.getString(SearchFragment.SEARCH_QUERY)
            ?: searchViewModel.lastQuery ?: ""
        arguments?.remove(SearchFragment.SEARCH_QUERY)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KadrSearchScreen(searchViewModel, initial)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchViewModel.reloadRepos()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KadrSearchScreen(vm: SearchViewModel, initialQuery: String) {
    var query by remember { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(TypeFilter.ALL) }
    val perSite by vm.currentSearch.asState()
    val response by vm.searchResponse.asState()
    val focus = remember { FocusRequester() }
    val view = LocalView.current

    fun runSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.length < 2) return
        if (trimmed == vm.lastQuery) return
        vm.lastQuery = trimmed
        vm.searchAndCancel(query = trimmed)
    }

    // Пошук під час набору (з паузою, щоб не смикати сайти на кожну літеру)
    LaunchedEffect(query) {
        delay(600)
        runSearch(query)
    }
    LaunchedEffect(Unit) {
        if (initialQuery.isBlank()) {
            try {
                focus.requestFocus()
            } catch (_: Throwable) {
            }
        }
    }

    val bySite = perSite.orEmpty()
        .mapValues { (_, list) -> list.list }
        .filterValues { it.isNotEmpty() }
    val merged = merge(bySite).filter { filter.matches(it.main.type) }
    val loading = response is Resource.Loading

    val open: (SearchResponse) -> Unit = { card ->
        SearchHelper.handleSearchClickCallback(SearchClickCallback(SEARCH_ACTION_LOAD, view, -1, card))
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(330.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(KColors.bg),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KColors.card)
                    .border(1.dp, KColors.accent, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("Назва фільму, серіалу чи аніме", color = Color(0xFF7D808A), fontSize = 18.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = KColors.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(KColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.lastQuery = null
                        runSearch(query)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeFilter.entries.forEach { f ->
                    Pill(f.label, selected = f == filter) { filter = f }
                }
                Box(Modifier.weight(1f))
                val status = when {
                    loading && bySite.isEmpty() -> "Шукаю на всіх сайтах…"
                    bySite.isEmpty() && (vm.lastQuery ?: "").isNotEmpty() && !loading -> "Нічого не знайдено"
                    bySite.isEmpty() -> ""
                    else -> "Знайдено на ${bySite.size} сайтах · однакові назви об'єднано" +
                            if (loading) " · шукаю далі…" else ""
                }
                Text(status, color = KColors.muted, fontSize = 13.sp)
            }
        }

        items(merged) { result ->
            ResultCard(
                result,
                onOpenCard = {
                    if (result.sites.size <= 1) {
                        open(result.main)
                    } else {
                        // Кілька сайтів — питаємо, звідки дивитись
                        view.context.findActivity()?.showBottomDialogInstant(
                            result.sites.map { site ->
                                listOfNotNull(site.apiName, site.quality?.name).joinToString(" · ")
                            },
                            "Де дивитись «${result.main.name}»?",
                            {},
                            { index -> result.sites.getOrNull(index)?.let(open) }
                        )
                    }
                },
                onOpenSite = open
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    result: MergedResult,
    onOpenCard: () -> Unit,
    onOpenSite: (SearchResponse) -> Unit,
) {
    val card = result.main
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15171C))
            .clickable { onOpenCard() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .width(92.dp)
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KColors.card)
        ) {
            KImage(card.posterUrl, card.posterHeaders, Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.name, color = KColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                result.year?.toString(),
                card.type.uaName().takeIf { it.isNotEmpty() },
                card.quality?.name
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = KColors.muted, fontSize = 13.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                result.sites.forEach { site ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22252D))
                            .clickable { onOpenSite(site) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(site.apiName, color = Color(0xFFD4D6DC), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (result.sites.size > 1) {
                Text("Є на ${result.sites.size} сайтах — обери, звідки дивитись",
                    color = KColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
