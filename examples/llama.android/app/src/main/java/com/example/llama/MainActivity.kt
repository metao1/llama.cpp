package com.example.llama

import android.Manifest
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.format.Formatter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField

import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlin.math.min
import androidx.core.net.toUri
import com.example.llama.ui.theme.LlamaAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
): ComponentActivity() {
    private val tag: String? = this::class.simpleName

    private val activityManager by lazy { activityManager ?: getSystemService<ActivityManager>()!! }
    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy {
        clipboardManager ?: getSystemService<ClipboardManager>()!!
    }

    private val viewModel: MainViewModel by viewModels()

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.log("Storage permissions granted")
        } else {
            viewModel.log("Storage permissions denied - downloads may fail")
        }
    }

    // Get a MemoryInfo object for the device's current memory status.
    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            // Android 12 and below use broad storage permissions
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        val permissions = getRequiredPermissions()
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(tag, "Requesting permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)

        viewModel.log("Current memory: $free / $total")
        viewModel.log("Downloads directory: ${getExternalFilesDir(null)}")

        // Check and request storage permissions
        if (!hasStoragePermissions()) {
            viewModel.log("Storage permissions not granted, requesting...")
            requestStoragePermissions()
        } else {
            viewModel.log("Storage permissions already granted")
        }

        val extFilesDir = getExternalFilesDir(null)
        Log.i("Storage", "extFilesDir: $extFilesDir")

        val models = listOf(
            Downloadable(
                "Phi-2 7B (Q4_0, 1.6 GiB)",
                "https://huggingface.co/ggml-org/models/resolve/main/phi-2/ggml-model-q4_0.gguf?download=true".toUri(),
                File(extFilesDir, "phi-2-q4_0.gguf"),
            ),
            Downloadable(
                "TinyLlama 1.1B (f16, 2.2 GiB)",
                "https://huggingface.co/ggml-org/models/resolve/main/tinyllama-1.1b/ggml-model-f16.gguf?download=true".toUri(),
                File(extFilesDir, "tinyllama-1.1-f16.gguf"),
            ),
            Downloadable(
                "Phi 2 DPO (Q3_K_M, 1.48 GiB)",
                "https://huggingface.co/TheBloke/phi-2-dpo-GGUF/resolve/main/phi-2-dpo.Q3_K_M.gguf?download=true".toUri(),
                File(extFilesDir, "phi-2-dpo.Q3_K_M.gguf")
            ),
            Downloadable(
                "Wizard Coder",
                Uri.parse("https://huggingface.co/bartowski/Fireball-Meta-Llama-3.1-8B-Instruct-Agent-0.003-128K-code-ds-auto-GGUF/resolve/main/Fireball-Meta-Llama-3.1-8B-Instruct-Agent-0.003-128K-code-ds-auto-Q6_K_L.gguf?download=true"),
                File(extFilesDir, "wizardcoder-15b.gguf")
            ),
            Downloadable(
                "3D Animation diffiusion",
                "https://huggingface.co/codegood/gemma-2b-it-Q4_K_M-GGUF/resolve/main/gemma-2b-it.Q4_K_M.gguf?download=true".toUri(),
                File(extFilesDir, "3d-animation.gguf")
            )
        )

        setContent {
            LlamaAndroidTheme {
                // A surface container with white background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    MainCompose(
                        context = this,
                        viewModel,
                        clipboardManager,
                        downloadManager,
                        models,
                        ::hasStoragePermissions,
                        ::requestStoragePermissions
                    )
                }

            }
        }
    }

    @Composable
    fun Chat(viewModel: MainViewModel) {
        val messages = viewModel.messages  // directly read list; Compose tracks changes
        val input = remember { mutableStateOf("") }
        val scrollState = rememberLazyListState()
        val configuration = LocalConfiguration.current

        // Calculate max width to maintain consistent proportions (max 600dp)
        val maxWidth = min(configuration.screenWidthDp.dp.value, 600f).dp

        val isNearBottom = remember {
            derivedStateOf {
                val lastVisible = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= messages.lastIndex - 1  // allow small tolerance
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxHeight()
            ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = input.value,
                    onValueChange = {
                        input.value = it
                        viewModel.updateMessage(it)
                    },
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .heightIn(min = 56.dp, max = 120.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        viewModel.send()
                        input.value = ""
                    },
                    modifier = Modifier.height(56.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Send")
                }
            }
            }

            // Scroll to bottom whenever messages list changes and we're near bottom
            LaunchedEffect(messages) {
                if (messages.isNotEmpty() && isNearBottom.value) {
                    delay(50)
                    scrollState.animateScrollToItem(messages.lastIndex)
                }
            }
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DrawerContent(
        models: MutableList<Downloadable>,
        viewModel: MainViewModel,
        dm: DownloadManager,
        hasStoragePermissions: () -> Boolean,
        requestStoragePermissions: () -> Unit,
        showDialog: MutableState<Boolean>,
        newName: MutableState<String>,
        newUrl: MutableState<String>,
        errorText: MutableState<String?>,
        clipboard: ClipboardManager
    ) {
        val configuration = LocalConfiguration.current
        // Limit drawer width to max 320dp or 40% of screen width, whichever is smaller
        val maxWidth = min(320f, configuration.screenWidthDp * 0.4f).dp

        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                "Tools",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
                color = Color.Black
            )

            Button(
                onClick = { viewModel.bench(8, 4, 1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Benchmark")
            }

            Button(
                onClick = { viewModel.clear() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Clear")
            }

            Button(
                onClick = {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "",
                            viewModel.messages.joinToString("\n")
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Copy")
            }

            Button(
                onClick = {
                    newName.value = ""
                    newUrl.value = ""
                    errorText.value = null
                    showDialog.value = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Add Model")
            }

            Text("Models", style = MaterialTheme.typography.titleSmall, color = Color.Black)

            Column {
                models.forEach { model ->
                    Downloadable.Button(
                        viewModel,
                        dm,
                        model,
                        hasStoragePermissions,
                        requestStoragePermissions
                    )
                }
            }
        }
    }

    @Composable
    fun MainCompose(
        context: Context,
        viewModel: MainViewModel,
        clipboard: ClipboardManager,
        dm: DownloadManager,
        initialModels: List<Downloadable>,
        hasStoragePermissions: () -> Boolean,
        requestStoragePermissions: () -> Unit
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current

        // Use mutableStateList for dynamic updates
        val models = remember { mutableStateListOf<Downloadable>().apply { addAll(initialModels) } }

        // Dialog states
        val showDialog = remember { mutableStateOf(false) }
        val newName = remember { mutableStateOf("") }
        val newUrl = remember { mutableStateOf("") }
        val errorText = remember { mutableStateOf<String?>(null) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            modifier = Modifier.fillMaxSize(),
            drawerContent = {
                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    color = Color.White
                ) {
                    DrawerContent(
                        models = models,
                        viewModel = viewModel,
                        dm = dm,
                        hasStoragePermissions = hasStoragePermissions,
                        requestStoragePermissions = requestStoragePermissions,
                        showDialog = showDialog,
                        newName = newName,
                        newUrl = newUrl,
                        errorText = errorText,
                        clipboard = clipboard
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = Color.White
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Chat(viewModel = viewModel)
                }
            }

        // Dialog for adding models
        if (showDialog.value) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text("Add Model") },
                    text = {
                        Column {
                            TextField(
                                value = newName.value,
                                onValueChange = { newName.value = it },
                                label = { Text("Model Name") },
                                singleLine = true,
                            )
                            TextField(
                                value = newUrl.value,
                                onValueChange = { newUrl.value = it },
                                label = { Text("Model URL") },
                                singleLine = true,
                            )
                            if (errorText.value != null) {
                                Text(
                                    text = errorText.value ?: "",
                                    color = Color.Red,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            // Validate inputs
                            if (newName.value.isBlank()) {
                                errorText.value = "Name cannot be empty"
                                return@Button
                            }
                            if (newUrl.value.isBlank() || !newUrl.value.startsWith("http")) {
                                errorText.value = "Invalid URL"
                                return@Button
                            }

                            // Add model
                            val fileName = newUrl.value.substringAfterLast('/').substringBefore('?')
                            val file = File(context.getExternalFilesDir(null), fileName)
                            models.add(
                                Downloadable(
                                    newName.value,
                                    newUrl.value.toUri(),
                                    file
                                )
                            )
                            showDialog.value = false
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showDialog.value = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
