package com.rootdrop.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rootdrop.share.server.DropShareServer
import com.rootdrop.share.ui.DropRoute
import com.rootdrop.share.ui.DropViewModel
import com.rootdrop.share.ui.theme.DropTheme
import kotlinx.serialization.Serializable

/** The single navigation destination: one screen, one job. */
@Serializable
object DropDestination

class MainActivity : ComponentActivity() {

    // Manual construction: one server instance for the life of the activity,
    // handed to the ViewModel. No code generation, no annotation processor.
    private val dropViewModel: DropViewModel by lazy {
        val server = DropShareServer(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DropViewModel::class.java)) {
                    "Unknown ViewModel class"
                }
                return DropViewModel(applicationContext, server) as T
            }
        }
        ViewModelProvider(this, factory)[DropViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DropTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = DropDestination,
                ) {
                    composable<DropDestination> {
                        DropRoute(dropViewModel)
                    }
                }
            }
        }
    }
}
