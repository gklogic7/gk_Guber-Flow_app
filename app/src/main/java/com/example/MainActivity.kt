package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GuberStudioApp
import com.example.ui.GuberStudioViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val studioViewModel: GuberStudioViewModel = viewModel(factory = GuberStudioViewModel.Factory)
            val settings by studioViewModel.studioSettings.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = settings.darkTheme) {
                GuberStudioApp(viewModel = studioViewModel)
            }
        }
    }
}
