package team.datayou.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import team.datayou.app.ui.DataYouTheme
import team.datayou.app.ui.DriveScreen
import team.datayou.app.ui.ReportScreen
import team.datayou.app.ui.StartScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataYouTheme {
                val vm: DataYouViewModel = viewModel()
                val screen by vm.screen.collectAsStateWithLifecycle()
                when (val s = screen) {
                    is Screen.Start -> StartScreen(s, vm)
                    is Screen.Driving -> DriveScreen(s, vm)
                    is Screen.Report -> ReportScreen(s, vm)
                }
            }
        }
    }
}
