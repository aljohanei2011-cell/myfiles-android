package com.personal.myfiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.myfiles.ui.AppRoot
import com.personal.myfiles.ui.PermissionGate
import com.personal.myfiles.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MyFilesApp).container
        setContent {
            AppTheme(container.settings) {
                PermissionGate {
                    AppRoot()
                }
            }
        }
    }
}
