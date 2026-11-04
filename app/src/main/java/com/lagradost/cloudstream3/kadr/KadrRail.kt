package com.lagradost.cloudstream3.kadr

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.ui.NavigationUI
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigationrail.NavigationRailView
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.library.LibraryViewModel
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx

/**
 * Вечорниці: бічна панель з підписами і прямими пунктами для списків
 * (Переглядаю / Збережене / Відкладено / Заплановано).
 *
 * Весь код тут, щоб у MainActivity був лише один рядок виклику —
 * так оновлення з upstream CloudStream не конфліктують.
 */
object KadrRail {
    private data class ListTarget(val titleRes: Int, val fallbackIndex: Int)

    // fallbackIndex — порядок сторінок у локальній бібліотеці CloudStream
    private val targets = mapOf(
        R.id.kadr_rail_watching to ListTarget(WatchType.WATCHING.stringRes, 0),
        R.id.kadr_rail_onhold to ListTarget(WatchType.ONHOLD.stringRes, 2),
        R.id.kadr_rail_planned to ListTarget(WatchType.PLANTOWATCH.stringRes, 4),
        R.id.kadr_rail_saved to ListTarget(R.string.favorites_list_name, 5),
    )

    fun setup(activity: MainActivity, navController: NavController) {
        // Лише телефон/планшет; на ТВ лишаємо стандартну панель
        if (!isLayout(PHONE)) return
        val rail = activity.binding?.navRailView ?: return

        rail.menu.clear()
        rail.inflateMenu(R.menu.kadr_rail_menu)
        rail.labelVisibilityMode = NavigationRailView.LABEL_VISIBILITY_LABELED
        rail.layoutParams = rail.layoutParams.apply { width = 96.toPx }

        rail.setOnItemSelectedListener { item ->
            val target = targets[item.itemId]
            if (target == null) {
                NavigationUI.onNavDestinationSelected(item, navController)
            } else {
                openList(activity, navController, target)
                true
            }
        }
    }

    private fun openList(
        activity: MainActivity,
        navController: NavController,
        target: ListTarget
    ) {
        val viewModel = ViewModelProvider(activity)[LibraryViewModel::class.java]
        val wanted = activity.getString(target.titleRes)
        val pages = (viewModel.pages.value as? Resource.Success)?.value
        val index = pages
            ?.indexOfFirst { it.title.asString(activity) == wanted }
            ?.takeIf { it >= 0 }
            ?: target.fallbackIndex

        viewModel.switchPage(index)

        if (navController.currentDestination?.id == R.id.navigation_library) {
            activity.binding?.root
                ?.findViewById<ViewPager2?>(R.id.viewpager)
                ?.setCurrentItem(index, true)
        } else {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.graph.startDestinationId, false)
                .build()
            navController.navigate(R.id.navigation_library, null, options)
        }
    }
}
