package com.lanshare.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lanshare.app.ui.LanShareApp
import com.lanshare.app.viewmodel.MainViewModel

fun main() = application {
    val viewModel = remember { MainViewModel() }

    Window(
        onCloseRequest = {
            viewModel.shutdown()
            exitApplication()
        },
        title = "LanShare Desktop"
    ) {
        MaterialTheme {
            LanShareApp(viewModel)
        }
    }
}
