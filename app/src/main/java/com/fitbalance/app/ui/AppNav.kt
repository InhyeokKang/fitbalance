package com.fitbalance.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitbalance.app.ui.screens.CourseDetailScreen
import com.fitbalance.app.ui.screens.HomeScreen
import com.fitbalance.app.ui.screens.MapScreen
import com.fitbalance.app.ui.screens.MeasureScreen
import com.fitbalance.app.ui.screens.RecommendScreen
import com.fitbalance.app.ui.screens.ReportScreen
import com.fitbalance.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val MEASURE = "measure"
    const val REPORT = "report"
    const val RECOMMEND = "recommend"
    const val MAP = "map"
    const val COURSE = "course/{courseId}"
    const val SETTINGS = "settings"

    fun course(courseId: String) = "course/$courseId"
}

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val diagnosis by vm.diagnosis.collectAsStateWithLifecycle()
    val recommendation by vm.recommendation.collectAsStateWithLifecycle()
    val courseDetail by vm.courseDetail.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                lastDiagnosis = (diagnosis as? UiState.Success)?.data,
                onMeasure = { nav.navigate(Routes.MEASURE) },
                onRecommend = {
                    vm.recommend()
                    nav.navigate(Routes.RECOMMEND)
                },
                onReport = { nav.navigate(Routes.REPORT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.MEASURE) {
            MeasureScreen(
                deviceId = vm.deviceId,
                onBack = { nav.popBackStack() },
                onSubmit = { req ->
                    vm.diagnose(req)
                    nav.navigate(Routes.REPORT)
                },
            )
        }

        composable(Routes.REPORT) {
            ReportScreen(
                state = diagnosis,
                // 리포트의 "홈으로"는 측정 화면을 건너뛰고 홈까지 돌아간다.
                onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onRetry = { nav.popBackStack() },
                onSeeCourses = {
                    vm.recommend()
                    nav.navigate(Routes.RECOMMEND)
                },
            )
        }

        composable(Routes.RECOMMEND) {
            RecommendScreen(
                state = recommendation,
                onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onRetry = { vm.recommend() },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onCourseClick = { id -> nav.navigate(Routes.course(id)) },
                onMap = { nav.navigate(Routes.MAP) },
            )
        }

        composable(Routes.MAP) {
            MapScreen(
                state = recommendation,
                settings = settings,
                onBack = { nav.popBackStack() },
                onRetry = { vm.recommend() },
                onCourseClick = { id -> nav.navigate(Routes.course(id)) },
            )
        }

        composable(
            route = Routes.COURSE,
            arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
        ) { entry ->
            val courseId = entry.arguments?.getString("courseId").orEmpty()
            LaunchedEffect(courseId) { vm.loadCourse(courseId) }
            CourseDetailScreen(
                state = courseDetail,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadCourse(courseId) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                current = settings,
                deviceId = vm.deviceId,
                onBack = { nav.popBackStack() },
                onSave = {
                    vm.saveSettings(it)
                    nav.popBackStack()
                },
            )
        }
    }
}
