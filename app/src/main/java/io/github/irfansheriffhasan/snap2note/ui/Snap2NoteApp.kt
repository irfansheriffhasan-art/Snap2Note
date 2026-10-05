package io.github.irfansheriffhasan.snap2note.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.irfansheriffhasan.snap2note.ui.screens.FolderScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.FoldersScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.HomeScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.PageScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.SaveToFolderScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.ScanScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.SettingsScreen
import io.github.irfansheriffhasan.snap2note.ui.screens.StudyChatScreen

object Routes {
  const val ARG_FOLDER_ID = "folderId"
  const val ARG_PAGE_ID = "pageId"

  const val HOME = "home"
  const val FOLDERS = "folders"
  const val SETTINGS = "settings"
  const val SAVE = "save"
  const val FOLDER = "folder/{$ARG_FOLDER_ID}"
  const val PAGE = "folder/{$ARG_FOLDER_ID}/page/{$ARG_PAGE_ID}"
  const val SCAN = "folder/{$ARG_FOLDER_ID}/scan"
  const val ASSISTANT = "folder/{$ARG_FOLDER_ID}/assistant"

  fun folder(folderId: Long) = "folder/$folderId"

  fun page(folderId: Long, pageId: Long) = "folder/$folderId/page/$pageId"

  fun scan(folderId: Long) = "folder/$folderId/scan"

  fun assistant(folderId: Long) = "folder/$folderId/assistant"
}

private data class TopLevelDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val icon: ImageVector,
)

private val TopLevelDestinations = listOf(
  TopLevelDestination(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
  TopLevelDestination(Routes.FOLDERS, "Folders", Icons.Filled.Folder, Icons.Outlined.Folder),
  TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private val FolderIdArgument = navArgument(Routes.ARG_FOLDER_ID) { type = NavType.LongType }

@Composable
fun Snap2NoteApp(viewModel: NoteViewModel) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route
  val showBottomBar = TopLevelDestinations.any { it.route == currentRoute }
  val context = LocalContext.current

  LaunchedEffect(viewModel) {
    viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    bottomBar = {
      AnimatedVisibility(visible = showBottomBar, enter = expandVertically(), exit = shrinkVertically()) {
        NavigationBar {
          TopLevelDestinations.forEach { destination ->
            val selected = destination.route == currentRoute
            NavigationBarItem(
              selected = selected,
              onClick = { navController.navigateToTopLevel(destination.route) },
              icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
              label = { Text(destination.label) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Routes.HOME,
      modifier = Modifier
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding),
      enterTransition = { fadeIn(tween(220)) },
      exitTransition = { fadeOut(tween(180)) },
    ) {
      composable(Routes.HOME) {
        HomeScreen(
          viewModel = viewModel,
          onOpenFolder = { navController.navigate(Routes.folder(it)) },
          onSeeAllFolders = { navController.navigateToTopLevel(Routes.FOLDERS) },
          onPagesReady = { navController.navigate(Routes.SAVE) },
        )
      }

      composable(Routes.FOLDERS) {
        FoldersScreen(
          viewModel = viewModel,
          onOpenFolder = { navController.navigate(Routes.folder(it)) },
          onScanInto = { navController.navigate(Routes.scan(it)) },
        )
      }

      composable(Routes.SETTINGS) {
        SettingsScreen(viewModel = viewModel)
      }

      composable(Routes.SAVE) {
        SaveToFolderScreen(
          viewModel = viewModel,
          onClose = { navController.popBackStack() },
          onSaved = { folderId ->
            navController.navigate(Routes.folder(folderId)) {
              popUpTo(Routes.SAVE) { inclusive = true }
            }
          },
        )
      }

      composable(Routes.FOLDER, arguments = listOf(FolderIdArgument)) { entry ->
        val folderId = entry.arguments?.getLong(Routes.ARG_FOLDER_ID) ?: return@composable
        FolderScreen(
          folderId = folderId,
          viewModel = viewModel,
          onBack = { navController.popBackStack() },
          onOpenPage = { navController.navigate(Routes.page(folderId, it)) },
          onScan = { navController.navigate(Routes.scan(folderId)) },
          onOpenAssistant = { navController.navigate(Routes.assistant(folderId)) },
          onOpenSettings = { navController.openSettings() },
        )
      }

      composable(
        Routes.PAGE,
        arguments = listOf(FolderIdArgument, navArgument(Routes.ARG_PAGE_ID) { type = NavType.LongType }),
      ) { entry ->
        val folderId = entry.arguments?.getLong(Routes.ARG_FOLDER_ID) ?: return@composable
        val pageId = entry.arguments?.getLong(Routes.ARG_PAGE_ID) ?: return@composable
        PageScreen(
          folderId = folderId,
          initialPageId = pageId,
          viewModel = viewModel,
          onBack = { navController.popBackStack() },
          onOpenSettings = { navController.openSettings() },
        )
      }

      composable(Routes.SCAN, arguments = listOf(FolderIdArgument)) { entry ->
        val folderId = entry.arguments?.getLong(Routes.ARG_FOLDER_ID) ?: return@composable
        ScanScreen(
          folderId = folderId,
          viewModel = viewModel,
          onDone = {
            // Land on the folder after scanning, even when the scan started from the folder list.
            if (navController.previousBackStackEntry?.destination?.route == Routes.FOLDER) {
              navController.popBackStack()
            } else {
              navController.navigate(Routes.folder(folderId)) {
                popUpTo(Routes.SCAN) { inclusive = true }
              }
            }
          },
        )
      }

      composable(Routes.ASSISTANT, arguments = listOf(FolderIdArgument)) {
        val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
        StudyChatScreen(
          viewModel = viewModel(factory = StudyChatViewModel.Factory),
          hasApiKey = apiKey.isNotBlank(),
          onBack = { navController.popBackStack() },
          onOpenSettings = { navController.openSettings() },
        )
      }
    }
  }
}

private fun NavController.navigateToTopLevel(route: String) {
  navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/** Opens Settings on top of the current screen, so Back returns to where the user was. */
private fun NavController.openSettings() {
  navigate(Routes.SETTINGS) { launchSingleTop = true }
}
