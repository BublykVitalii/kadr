package com.lagradost.cloudstream3.kadr

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.ACTION_CLICK_DEFAULT
import com.lagradost.cloudstream3.ui.result.ACTION_SHOW_OPTIONS
import com.lagradost.cloudstream3.ui.result.EpisodeClickEvent
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment.getStoredData
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.ui.result.callback
import com.lagradost.cloudstream3.ui.result.getOptions
import com.lagradost.cloudstream3.ui.result.getTitle
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

/** Українські назви статусів для кнопки статусу */
private fun WatchType.uaLabel(): String = when (this) {
    WatchType.WATCHING -> "Переглядаю"
    WatchType.COMPLETED -> "Переглянуто"
    WatchType.ONHOLD -> "Відкладено"
    WatchType.DROPPED -> "Кинув"
    WatchType.PLANTOWATCH -> "Заплановано"
    WatchType.NONE -> "Додати в список"
}

/**
 * Нова сторінка фільму/серіалу «Вечорниці».
 * Уся логіка (завантаження, посилання, прогрес, статуси) — зі стандартного
 * ResultViewModel2 CloudStream; тут лише новий інтерфейс.
 */
class KadrResultFragment : Fragment() {
    private val viewModel: ResultViewModel2 by viewModels()
    private var loadingDialog: BottomSheetDialog? = null
    private var popupDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val playerAction = remember { getStoredData()?.playerAction ?: ACTION_CLICK_DEFAULT }
            KadrResultScreen(
                vm = viewModel,
                onBack = { findNavController().popBackStack() },
                onPlay = { ep -> viewModel.handleAction(EpisodeClickEvent(null, playerAction, ep)) },
                onOptions = { ep -> viewModel.handleAction(EpisodeClickEvent(null, ACTION_SHOW_OPTIONS, ep)) },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val stored = getStoredData() ?: return
        if (stored.restart || !viewModel.hasLoaded()) {
            viewModel.load(
                activity,
                stored.url,
                stored.apiName,
                stored.showFillers,
                stored.dubStatus,
                stored.start
            )
        }

        // Вікно «Завантаження посилань…» — як у стандартному CloudStream
        viewModel.loadedLinks.observe(viewLifecycleOwner) { load ->
            if (load == null) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
                return@observe
            }
            if (loadingDialog?.isShowing != true) {
                loadingDialog?.dismissSafe(activity)
                loadingDialog = null
            }
            loadingDialog = loadingDialog ?: context?.let { ctx ->
                BottomSheetDialog(ctx).apply {
                    setContentView(R.layout.bottom_loading)
                    setOnDismissListener {
                        loadingDialog = null
                        viewModel.cancelLinks()
                    }
                    setCanceledOnTouchOutside(true)
                    show()
                }
            }
            loadingDialog?.findViewById<MaterialButton>(R.id.overlay_loading_skip_button)?.apply {
                if (load.linksLoaded <= 0) {
                    visibility = View.INVISIBLE
                } else {
                    setOnClickListener { viewModel.skipLoading() }
                    visibility = View.VISIBLE
                    text = "${context.getString(R.string.skip_loading)} (${load.linksLoaded})"
                }
            }
        }

        // Вибір джерела/якості, опції серії тощо
        viewModel.selectPopup.observe(viewLifecycleOwner) { popup ->
            popupDialog?.dismissSafe(activity)
            popupDialog = null
            if (popup == null) return@observe
            popupDialog = activity?.let { act ->
                act.showBottomDialogInstant(
                    popup.getOptions(act), popup.getTitle(act),
                    {
                        popupDialog = null
                        popup.callback(null)
                    }, {
                        popupDialog = null
                        popup.callback(it)
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        loadingDialog?.dismissSafe(activity)
        popupDialog?.dismissSafe(activity)
        loadingDialog = null
        popupDialog = null
        super.onDestroyView()
    }
}

@Composable
private fun KadrResultScreen(
    vm: ResultViewModel2,
    onBack: () -> Unit,
    onPlay: (ResultEpisode) -> Unit,
    onOptions: (ResultEpisode) -> Unit,
) {
    val context = LocalContext.current
    val page by vm.page.asState()
    val episodesRes by vm.episodes.asState()
    val movieRes by vm.movie.asState()
    val seasons by vm.seasonSelections.asState()
    val seasonIndex by vm.selectedSeasonIndex.asState()
    val dubs by vm.dubSubSelections.asState()
    val dubIndex by vm.selectedDubStatusIndex.asState()
    val watchStatus by vm.watchStatus.asState()
    val favorite by vm.favoriteStatus.asState()

    val data = (page as? Resource.Success)?.value
    if (data == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(KColors.bg), contentAlignment = Alignment.Center
        ) {
            val text = when (val p = page) {
                is Resource.Failure -> "Не вдалося відкрити: ${p.errorString}"
                else -> "Завантажую…"
            }
            Text(text, color = KColors.muted, fontSize = 15.sp)
        }
        return
    }

    val episodes = (episodesRes as? Resource.Success)?.value.orEmpty()
    val movie = (movieRes as? Resource.Success)?.value?.second
    val nextUp: ResultEpisode? = movie ?: episodes.firstOrNull {
        it.videoWatchState != VideoWatchState.Watched &&
                (it.duration <= 0 || it.position * 100 / it.duration < 90)
    } ?: episodes.firstOrNull()

    val meta = listOfNotNull(
        data.yearText?.asStringNull(context),
        data.typeText.asStringNull(context),
        data.durationText?.asStringNull(context),
        data.ratingText?.asStringNull(context),
        data.tags.take(3).joinToString(", ").takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(KColors.bg)
    ) {
        val wide = maxWidth >= 840.dp

        val side: @Composable (Modifier) -> Unit = { modifier ->
            SidePanel(
                modifier = modifier,
                nextUp = nextUp,
                isMovie = movie != null,
                dubs = dubs.orEmpty().map { (label, status) ->
                    (label?.asStringNull(context) ?: status.uaName()) to status
                },
                dubIndex = dubIndex ?: 0,
                apiName = data.apiName.asString(context),
                onDub = { vm.changeDubStatus(it) },
                onPlay = { nextUp?.let(onPlay) },
            )
        }

        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (wide) 220.dp else 180.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF2B3242))
                    ) {
                        KImage(
                            data.posterBackgroundImage ?: data.backgroundPosterUrl
                            ?: data.posterImage,
                            data.posterHeaders, Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .padding(16.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xBF0E0F13))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) { Text("←", color = KColors.text, fontSize = 20.sp) }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                data.title, color = KColors.text, fontSize = 28.sp,
                                fontWeight = FontWeight.Bold, lineHeight = 32.sp
                            )
                            if (meta.isNotBlank()) Text(meta, color = KColors.muted, fontSize = 14.sp)
                        }
                        StatusButton(watchStatus ?: WatchType.NONE) { type ->
                            vm.updateWatchStatus(type, context)
                        }
                        Pill(
                            text = if (favorite == true) "★ Збережено" else "☆ Зберегти",
                            selected = favorite == true
                        ) { vm.toggleFavoriteStatus(context) }
                    }
                }

                val plot = data.plotText.asStringNull(context)
                if (!plot.isNullOrBlank()) {
                    item {
                        Text(plot, color = Color(0xFFC4C7CF), fontSize = 14.sp, lineHeight = 21.sp,
                            maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (!wide) {
                    item { side(Modifier.fillMaxWidth()) }
                }

                val seasonList = seasons.orEmpty()
                if (seasonList.size > 1) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasonList.size) { i ->
                                val (label, season) = seasonList[i]
                                Pill(
                                    text = label?.asStringNull(context) ?: "Сезон $season",
                                    selected = i == (seasonIndex ?: 0)
                                ) { vm.changeSeason(season) }
                            }
                        }
                    }
                }

                if (movie == null && episodes.isNotEmpty()) {
                    val watched = episodes.count { it.videoWatchState == VideoWatchState.Watched }
                    item {
                        Text("Серії · переглянуто $watched з ${episodes.size}",
                            color = KColors.muted, fontSize = 13.sp)
                    }
                    val columns = if (wide) 2 else 1
                    items(episodes.chunked(columns)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { ep ->
                                EpisodeRow(
                                    ep = ep,
                                    current = ep == nextUp,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPlay(ep) },
                                    onLongClick = { onOptions(ep) }
                                )
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (wide) {
                side(
                    Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .background(KColors.panel)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun DubStatus.uaName(): String = when (this) {
    DubStatus.Dubbed -> "Дубляж"
    DubStatus.Subbed -> "Субтитри"
    else -> "Оригінал"
}

@Composable
private fun SidePanel(
    modifier: Modifier,
    nextUp: ResultEpisode?,
    isMovie: Boolean,
    dubs: List<Pair<String, DubStatus>>,
    dubIndex: Int,
    apiName: String,
    onDub: (DubStatus) -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Далі дивитись", color = KColors.muted, fontSize = 13.sp)
            val label = when {
                nextUp == null -> "—"
                isMovie -> "Фільм"
                else -> listOfNotNull(
                    nextUp.season?.let { "Сезон $it" },
                    "Серія ${nextUp.episode}"
                ).joinToString(" · ")
            }
            Text(label, color = KColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            nextUp?.name?.takeIf { !isMovie && it.isNotBlank() }?.let {
                Text(it, color = KColors.muted, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }

        if (dubs.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SideTitle("Озвучка")
                dubs.forEachIndexed { i, (label, status) ->
                    Pill(label, selected = i == dubIndex, modifier = Modifier.fillMaxWidth()) {
                        onDub(status)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SideTitle("Сайт")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, KColors.accent, RoundedCornerShape(14.dp))
                    .background(Color(0xFF1F1C14))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(apiName, color = KColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Якість і сервер обереш після натискання «Відтворити»",
                        color = KColors.muted, fontSize = 12.sp)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (nextUp != null) KColors.accent else KColors.chipBorder)
                .clickable(enabled = nextUp != null) { onPlay() },
            contentAlignment = Alignment.Center
        ) {
            Text("▶  Відтворити", color = KColors.onAccent, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SideTitle(text: String) {
    Text(text.uppercase(), color = Color(0xFFD4D6DC), fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

@Composable
private fun StatusButton(current: WatchType, onPick: (WatchType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill("${current.uaLabel()} ▾", selected = current != WatchType.NONE) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                WatchType.WATCHING, WatchType.PLANTOWATCH, WatchType.ONHOLD,
                WatchType.COMPLETED, WatchType.DROPPED, WatchType.NONE
            ).forEach { type ->
                DropdownMenuItem(
                    text = { Text(if (type == WatchType.NONE) "Прибрати зі списків" else type.uaLabel()) },
                    onClick = {
                        open = false
                        onPick(type)
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: ResultEpisode,
    current: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = when {
        ep.videoWatchState == VideoWatchState.Watched -> 1f
        ep.duration > 0 -> (ep.position.toFloat() / ep.duration).coerceIn(0f, 1f)
        else -> 0f
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (current) KColors.accent else Color(0xFF1F222A), RoundedCornerShape(14.dp))
            .background(if (current) Color(0xFF1F1C14) else Color(0xFF15171C))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KColors.card)
        ) {
            KImage(ep.poster, null, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(KColors.accent)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = ep.name?.takeIf { it.isNotBlank() }
            Text(
                if (title != null) "${ep.episode}. $title" else "Серія ${ep.episode}",
                color = KColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                ep.videoWatchState == VideoWatchState.Watched -> "Переглянуто"
                progress > 0f -> "Переглянуто ${(progress * 100).toInt()}%"
                ep.runTime != null -> "${ep.runTime} хв"
                else -> ""
            }
            if (sub.isNotEmpty()) Text(sub, color = KColors.muted, fontSize = 12.sp)
        }
    }
}
