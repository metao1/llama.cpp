package com.metao.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metao.ai.presentation.chat.ChatScreen
import com.metao.ai.presentation.chat.ChatViewModel
import com.metao.ai.presentation.models.ModelsScreen
import com.metao.ai.presentation.models.ModelsViewModel
import com.metao.ai.ui.theme.LlamaAndroidTheme
import org.koin.androidx.compose.koinViewModel
import kotlin.math.min
import android.util.Log

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate started")

        // Request storage permissions
        requestStoragePermissions()

        Log.d("MainActivity", "Setting content")

        setContent {
            LlamaAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    MainScreen()
                }
            }
        }

        Log.d("MainActivity", "onCreate completed")
    }

    private fun requestStoragePermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val hasPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            requestPermissionLauncher.launch(permissions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val configuration = LocalConfiguration.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = Modifier.fillMaxSize(),
        drawerContent = {
            Surface(
                modifier = Modifier
                    .width(min(370f, configuration.screenWidthDp * 0.6f).dp)
                    .fillMaxHeight(),
                color = Color.White
            ) {
                DrawerContent()
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
                ChatScreen()
            }
        }
    }
}

@Composable
private fun DrawerContent() {
    val configuration = LocalConfiguration.current
    val maxWidth = min(configuration.screenWidthDp.dp.value, 600f).dp

    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = { /* TODO: Implement benchmark */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("Benchmark")
        }

        ClearChatButton()

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        ModelsScreen()
    }
}

@Composable
private fun ClearChatButton() {
    val chatViewModel: ChatViewModel = koinViewModel()

    Button(
        onClick = { chatViewModel.clearMessages() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White
        )
    ) {
        Text("Clear Chat")
    }
}
