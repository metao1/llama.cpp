package com.metao.ai.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.metao.ai.presentation.categorize.FileCategorizeScreen
import com.metao.ai.presentation.categorize.FileCategorizeViewModel
import com.metao.ai.presentation.models.ModelsScreen
import org.koin.androidx.compose.koinViewModel
import kotlin.math.min

private const val DRAWER_MAX_WIDTH_DP = 370f
private const val DRAWER_WIDTH_PERCENTAGE = 0.6f

@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val windowInfo = LocalWindowInfo.current
    // Convert the window's width from pixels to DP
    val windowWidthDp = with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = Modifier.fillMaxSize(),
        drawerContent = {
            Surface(
                modifier = Modifier.width(min(DRAWER_MAX_WIDTH_DP, windowWidthDp.value * DRAWER_WIDTH_PERCENTAGE).dp)
                    .fillMaxHeight(), color = Color.White
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
                FileCategorizeScreen()
            }
        }
    }
}

@Preview
@Composable
private fun DrawerContent() {
    val windowInfo = LocalWindowInfo.current
    // Convert the window's width from pixels to DP
    val windowWidthDp = with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp()
    }
    Column(
        modifier = Modifier
            .width(min(DRAWER_MAX_WIDTH_DP, windowWidthDp.value * DRAWER_WIDTH_PERCENTAGE).dp)
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "File Categorizer",
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

        ResetCategorizationButton()

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        ModelsScreen()
    }
}

@Composable
private fun ResetCategorizationButton() {
    val fileCategorizeViewModel: FileCategorizeViewModel = koinViewModel()

    Button(
        onClick = { fileCategorizeViewModel.resetCategorization() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White
        )
    ) {
        Text("Reset Categorization")
    }
}
