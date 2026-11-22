package de.ellens.shoppinglist.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.ellens.shoppinglist.AppContainer
import de.ellens.shoppinglist.data.settings.AppSettings
import de.ellens.shoppinglist.ui.catalog.CatalogScreen
import de.ellens.shoppinglist.ui.catalog.CatalogViewModel
import de.ellens.shoppinglist.ui.combined.CombinedScreen
import de.ellens.shoppinglist.ui.combined.CombinedViewModel
import de.ellens.shoppinglist.ui.list.ListScreen
import de.ellens.shoppinglist.ui.list.ListViewModel
import de.ellens.shoppinglist.ui.overview.OverviewScreen
import de.ellens.shoppinglist.ui.overview.OverviewViewModel
import de.ellens.shoppinglist.ui.settings.SettingsScreen
import de.ellens.shoppinglist.ui.settings.SettingsViewModel

object Routes {
    const val OVERVIEW = "overview"
    const val LIST = "list/{listId}"
    const val SETTINGS = "settings"
    const val CATALOG = "catalog"
    const val COMBINED = "combined/{listIds}"
    fun list(id: Long) = "list/$id"
    fun combined(ids: List<Long>) = "combined/" + ids.joinToString(",")
}

/**
 * Navigation. Der Back-Stack wird von Android im Instanzzustand gesichert: Nach einem
 * App-Wechsel (auch wenn Android den Prozess zwischenzeitlich beendet hat) landet man wieder
 * in derselben Liste. Ein neuer, regulärer Start beginnt mit der Übersicht.
 */
@Composable
fun AppNavHost(container: AppContainer, settings: AppSettings) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) {
            val vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
            OverviewScreen(
                viewModel = vm,
                keepScreenOn = settings.keepScreenOn,
                onOpenList = { id -> nav.navigate(Routes.list(id)) { launchSingleTop = true } },
                onOpenCombined = { ids -> nav.navigate(Routes.combined(ids)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.LIST,
            arguments = listOf(navArgument("listId") { type = NavType.LongType }),
        ) {
            val vm: ListViewModel = viewModel(factory = ListViewModel.factory(container))
            ListScreen(
                viewModel = vm,
                keepScreenOn = settings.keepScreenOn,
                onBack = { nav.popBackStack(Routes.OVERVIEW, inclusive = false) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.COMBINED,
            arguments = listOf(navArgument("listIds") { type = NavType.StringType }),
        ) {
            val vm: CombinedViewModel = viewModel(factory = CombinedViewModel.factory(container))
            CombinedScreen(
                viewModel = vm,
                keepScreenOn = settings.keepScreenOn,
                onBack = { nav.popBackStack(Routes.OVERVIEW, inclusive = false) },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(
                viewModel = vm,
                settings = settings,
                onBack = { nav.popBackStack() },
                onOpenCatalog = { nav.navigate(Routes.CATALOG) { launchSingleTop = true } },
            )
        }
        composable(Routes.CATALOG) {
            val vm: CatalogViewModel = viewModel(factory = CatalogViewModel.factory(container))
            CatalogScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
    }
}
