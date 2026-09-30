package com.cardpregrade.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cardpregrade.app.navigation.AppNavHost
import com.cardpregrade.app.ui.theme.CardPregradeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CardPregradeApplication).container
        setContent {
            CardPregradeTheme {
                AppNavHost(container)
            }
        }
    }
}
