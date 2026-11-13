package team.datayou.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import team.datayou.app.ui.DataYouTheme
import team.datayou.app.ui.DriveScreen
import team.datayou.app.ui.ReportScreen
import team.datayou.app.ui.StartScreen

class MainActivity : ComponentActivity() {

    /**
     * 차량 속도(CAR_SPEED)와 핸들 각도(READ_CAR_STEERING)는 '위험' 등급 런타임 권한이라
     * 매니페스트 선언만으로는 부족하고 사용자 허용이 필요하다.
     * (Android Studio ▶ Run이나 adb install(-g 없이)로 설치하면 허용되지 않은 상태로 시작한다.)
     * 일반 권한(Pleos CAR_DRIVE 등)은 목록에 있어도 대화상자 없이 지나간다.
     */
    private val runtimePermissions = arrayOf(
        "android.car.permission.CAR_SPEED",
        "android.car.permission.READ_CAR_STEERING",
        "android.car.permission.CAR_EXTERIOR_LIGHTS",
        "pleos.car.permission.CAR_DRIVE",
        "pleos.car.permission.CAR_LIGHTS",
    )

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Log.i("DataYouPerm", "permission result: $result")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = runtimePermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        Log.i("DataYouPerm", "missing permissions: $missing")
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
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
