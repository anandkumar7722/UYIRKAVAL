package com.hacksrm.nirbhay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hacksrm.nirbhay.screens.HomeScreen
import com.hacksrm.nirbhay.screens.SettingsScreen
import com.hacksrm.nirbhay.screens.SosCountdownScreen
import com.hacksrm.nirbhay.screens.Stealth_Dashboard.StealthDashboardScreen
import com.hacksrm.nirbhay.ui.theme.NirbhayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NirbhayTheme {
                SettingsScreen()
            }
        }
    }
}