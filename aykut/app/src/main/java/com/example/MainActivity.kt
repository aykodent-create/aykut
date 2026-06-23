package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.ProductRepository
import com.example.speech.SpeechHelper
import com.example.ui.BakkalApp
import com.example.ui.BakkalViewModel
import com.example.ui.BakkalViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Speech Helper for price audio readouts
        speechHelper = SpeechHelper(this)

        // Initialize Local SQLite Caching Database (Room)
        val database = AppDatabase.getDatabase(this)
        val repository = ProductRepository(database.productDao())

        // Initialize ViewModel via standard ViewModelProvider factory
        val viewModel = ViewModelProvider(
            this,
            BakkalViewModelFactory(repository, application)
        )[BakkalViewModel::class.java]

        // Link speech helper to ViewModel
        viewModel.setSpeechHelper(speechHelper)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BakkalApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.shutdown()
    }
}

