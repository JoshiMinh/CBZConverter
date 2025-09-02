package com.joshiminh.cbzconverter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.ContextHelper
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.ui.CbzConverterTheme
import com.joshiminh.cbzconverter.ui.MihonMode
import com.joshiminh.cbzconverter.ui.NormalMode

private const val PREFS_NAME = "cbz_prefs"
private const val KEY_LAST_SCREEN = "last_screen"

private enum class Screen { HOME, MIHON, NORMAL }

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contextHelper = ContextHelper(this)
        val viewModel = MainViewModel(contextHelper)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialScreen: Screen = runCatching {
            Screen.valueOf(prefs.getString(KEY_LAST_SCREEN, Screen.HOME.name) ?: Screen.HOME.name)
        }.getOrDefault(Screen.HOME)

        setContent {
            CbzConverterTheme {
                MainApp(
                    activity = this@MainActivity,
                    viewModel = viewModel,
                    initialScreen = initialScreen,
                    onPersistScreen = { screen ->
                        prefs.edit().putString(KEY_LAST_SCREEN, screen.name).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    activity: ComponentActivity,
    viewModel: MainViewModel,
    initialScreen: Screen,
    onPersistScreen: (Screen) -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }
    LaunchedEffect(currentScreen) { onPersistScreen(currentScreen) }

    when (currentScreen) {
        Screen.HOME -> {
            Scaffold { inner ->
                HomeScreen(
                    modifier = Modifier
                        .padding(inner)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    onOpenMihon = { currentScreen = Screen.MIHON },
                    onOpenNormal = { currentScreen = Screen.NORMAL }
                )
            }
        }

        Screen.MIHON -> MihonScreen(
            activity = activity,
            viewModel = viewModel,
            onBack = { currentScreen = Screen.HOME }
        )

        Screen.NORMAL -> NormalScreen(
            activity = activity,
            viewModel = viewModel,
            onBack = { currentScreen = Screen.HOME }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonScreen(
    activity: ComponentActivity,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isCurrentlyConverting by viewModel.isCurrentlyConverting.collectAsState()
    val currentTaskStatus by viewModel.currentTaskStatus.collectAsState()
    val currentSubTaskStatus by viewModel.currentSubTaskStatus.collectAsState()

    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFilesUri by viewModel.selectedFileUri.collectAsState()

    val maxNumberOfPages by viewModel.maxNumberOfPages.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val overrideSortOrderToUseOffset by viewModel.overrideSortOrderToUseOffset.collectAsState()
    val overrideMergeFiles by viewModel.overrideMergeFiles.collectAsState()
    val overrideFileName by viewModel.overrideFileName.collectAsState()
    val overrideOutputDirectoryUri by viewModel.overrideOutputDirectoryUri.collectAsState()
    val compressOutputPdf by viewModel.compressOutputPdf.collectAsState()
    val autoNameWithChapters by viewModel.autoNameWithChapters.collectAsState()
    val mihonDirectoryUri by viewModel.mihonDirectoryUri.collectAsState()
    val mihonMangaEntries by viewModel.mihonMangaEntries.collectAsState()
    val isLoadingMihonManga by viewModel.isLoadingMihonManga.collectAsState()
    val mihonLoadProgress by viewModel.mihonLoadProgress.collectAsState()

    val directoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateOverrideOutputPathFromUserInput(it) }
        }

    val mihonDirectoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateMihonDirectoryUri(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mihon Mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            MihonMode(
                selectedFileName = selectedFileName,
                viewModel = viewModel,
                activity = activity,
                isCurrentlyConverting = isCurrentlyConverting,
                selectedFilesUri = selectedFilesUri,
                mihonManga = mihonMangaEntries,
                currentTaskStatus = currentTaskStatus,
                currentSubTaskStatus = currentSubTaskStatus,
                maxNumberOfPages = maxNumberOfPages,
                batchSize = batchSize,
                overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
                overrideMergeFiles = overrideMergeFiles,
                overrideFileName = overrideFileName,
                overrideOutputDirectoryUri = overrideOutputDirectoryUri,
                compressOutputPdf = compressOutputPdf,
                autoNameWithChapters = autoNameWithChapters,
                directoryPickerLauncher = directoryPickerLauncher,
                mihonDirectoryUri = mihonDirectoryUri,
                isLoadingMihonManga = isLoadingMihonManga,
                mihonLoadProgress = mihonLoadProgress,
                onSelectMihonDirectory = {
                    viewModel.checkPermissionAndSelectDirectoryAction(
                        activity,
                        mihonDirectoryPickerLauncher
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalScreen(
    activity: ComponentActivity,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isCurrentlyConverting by viewModel.isCurrentlyConverting.collectAsState()
    val currentTaskStatus by viewModel.currentTaskStatus.collectAsState()
    val currentSubTaskStatus by viewModel.currentSubTaskStatus.collectAsState()

    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFilesUri by viewModel.selectedFileUri.collectAsState()

    val maxNumberOfPages by viewModel.maxNumberOfPages.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val overrideSortOrderToUseOffset by viewModel.overrideSortOrderToUseOffset.collectAsState()
    val overrideMergeFiles by viewModel.overrideMergeFiles.collectAsState()
    val overrideFileName by viewModel.overrideFileName.collectAsState()
    val overrideOutputDirectoryUri by viewModel.overrideOutputDirectoryUri.collectAsState()
    val compressOutputPdf by viewModel.compressOutputPdf.collectAsState()

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) viewModel.updateUpdateSelectedFileUriFromUserInput(uris)
        }

    val directoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateOverrideOutputPathFromUserInput(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Normal Mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            NormalMode(
                selectedFileName = selectedFileName,
                viewModel = viewModel,
                activity = activity,
                filePickerLauncher = filePickerLauncher,
                isCurrentlyConverting = isCurrentlyConverting,
                selectedFilesUri = selectedFilesUri,
                currentTaskStatus = currentTaskStatus,
                currentSubTaskStatus = currentSubTaskStatus,
                maxNumberOfPages = maxNumberOfPages,
                batchSize = batchSize,
                overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
                overrideMergeFiles = overrideMergeFiles,
                overrideFileName = overrideFileName,
                overrideOutputDirectoryUri = overrideOutputDirectoryUri,
                compressOutputPdf = compressOutputPdf,
                directoryPickerLauncher = directoryPickerLauncher
            )
        }
    }
}

/* ---------------------------- HOME ---------------------------- */

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenMihon: () -> Unit,
    onOpenNormal: () -> Unit
) {
    val config = LocalConfiguration.current
    val cardHeight: Dp = (config.screenHeightDp * 0.16f).dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Select Mode",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )

        ModeCard(
            iconRes = R.drawable.mihon,
            title = "Mihon Mode",
            subtitle = "Load downloaded CBZ files from Mihon directory.",
            height = cardHeight,
            onClick = onOpenMihon
        )

        ModeCard(
            iconRes = R.drawable.cbz,
            title = "Normal Mode",
            subtitle = "Pick individual CBZ files.",
            height = cardHeight,
            onClick = onOpenNormal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    height: Dp,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(end = 16.dp),
                contentScale = ContentScale.Fit
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}