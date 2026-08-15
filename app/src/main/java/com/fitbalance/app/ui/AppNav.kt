package com.fitbalance.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitbalance.app.ui.screens.CenterBriefScreen
import com.fitbalance.app.ui.screens.CenterScreen
import com.fitbalance.app.ui.screens.CourseDetailScreen
import com.fitbalance.app.ui.screens.FacilityScreen
import com.fitbalance.app.ui.screens.HomeMeasureScreen
import com.fitbalance.app.ui.screens.HomeScreen
import com.fitbalance.app.ui.screens.MapScreen
import com.fitbalance.app.ui.screens.MeasureScreen
import com.fitbalance.app.ui.screens.OnboardingScreen
import com.fitbalance.app.ui.screens.RecommendScreen
import com.fitbalance.app.ui.screens.ReportScreen
import com.fitbalance.app.ui.screens.SelfCheckScreen
import com.fitbalance.app.ui.screens.SettingsScreen
import com.fitbalance.app.ui.screens.SplashScreen
import com.fitbalance.app.ui.screens.WeakPickScreen
import com.fitbalance.app.ui.components.TutorialOverlay
import com.fitbalance.app.ui.components.TutorialStep
import com.fitbalance.app.ui.screens.TUTORIAL_CENTER
import com.fitbalance.app.ui.screens.TUTORIAL_HOME_MEASURE
import com.fitbalance.app.ui.screens.TUTORIAL_SELFCHECK
import com.fitbalance.app.ui.screens.TUTORIAL_SETTINGS

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MEASURE = "measure"
    const val HOME_MEASURE = "home_measure"
    const val SELFCHECK = "selfcheck"
    const val WEAKPICK = "weakpick"
    const val CENTERS = "centers"
    const val CENTER_BRIEF = "center_brief"
    const val FACILITIES = "facilities"
    const val REPORT = "report"
    const val RECOMMEND = "recommend"
    const val MAP = "map"
    const val COURSE = "course/{courseId}"
    const val SETTINGS = "settings"

    fun course(courseId: String) = "course/$courseId"
}

/**
 * 첫 실행 안내 장면.
 *
 * 홈 화면의 실제 요소를 하나씩 비춘다. 네 장을 넘지 않는다 — 그 이상은 읽지 않는다.
 */
private val TUTORIAL_STEPS = listOf(
    TutorialStep(
        targetKey = null,
        title = "3분이면 시작합니다",
        body = "체력을 재고, 퇴근 동선 안의 공공 체육 강좌와 시설을 받는 앱입니다.",
    ),
    TutorialStep(
        targetKey = TUTORIAL_HOME_MEASURE,
        title = "먼저 집에서 재세요",
        body = "줄자와 초시계로 3가지를 재면 또래 100명 중 몇 등인지 나옵니다.",
    ),
    TutorialStep(
        targetKey = TUTORIAL_SELFCHECK,
        title = "잴 여건이 안 되면",
        body = "도구 없이 6문항으로도 약점을 추정할 수 있습니다.",
    ),
    TutorialStep(
        targetKey = TUTORIAL_CENTER,
        title = "근력과 심폐는 센터에서",
        body = "집에서 못 재는 두 항목은 전국 78개 센터에서 무료로 잽니다.",
    ),
    TutorialStep(
        targetKey = TUTORIAL_SETTINGS,
        title = "출퇴근 위치를 먼저",
        body = "직장·집과 퇴근 시각을 넣어야 동선에 맞는 곳을 골라 드립니다.",
    ),
)

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val diagnosis by vm.diagnosis.collectAsStateWithLifecycle()
    val recommendation by vm.recommendation.collectAsStateWithLifecycle()
    val courseDetail by vm.courseDetail.collectAsStateWithLifecycle()
    val centers by vm.centers.collectAsStateWithLifecycle()
    val facilities by vm.facilities.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val showTutorial by vm.showTutorial.collectAsStateWithLifecycle()
    val showOnboarding by vm.showOnboarding.collectAsStateWithLifecycle()

    // 튜토리얼은 홈 화면 위에서만 띄운다. 다른 화면으로 넘어간 뒤에 뜨면 가리킬 곳이 없다.
    val entry by nav.currentBackStackEntryAsState()
    val onHome = entry?.destination?.route == Routes.HOME

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(onDone = {
                // 위치를 아직 안 받았으면 홈보다 먼저 받는다. 그래야 진단 직후
                // 바로 맞는 강좌가 나온다.
                val next = if (showOnboarding) Routes.ONBOARDING else Routes.HOME
                nav.navigate(next) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                current = settings,
                onDone = {
                    vm.finishOnboarding(it)
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                lastDiagnosis = (diagnosis as? UiState.Success)?.data,
                onHomeMeasure = { nav.navigate(Routes.HOME_MEASURE) },
                onMeasure = { nav.navigate(Routes.MEASURE) },
                onSelfCheck = { nav.navigate(Routes.SELFCHECK) },
                onPickWeak = { nav.navigate(Routes.WEAKPICK) },
                onFindCenter = {
                    vm.loadCenters()
                    nav.navigate(Routes.CENTERS)
                },
                onFacilities = {
                    vm.loadFacilities()
                    nav.navigate(Routes.FACILITIES)
                },
                onRecommend = {
                    vm.recommend()
                    nav.navigate(Routes.RECOMMEND)
                },
                onReport = { nav.navigate(Routes.REPORT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.HOME_MEASURE) {
            HomeMeasureScreen(
                deviceId = vm.deviceId,
                onBack = { nav.popBackStack() },
                onFindCenter = {
                    vm.loadCenters()
                    nav.navigate(Routes.CENTERS)
                },
                onSubmit = { req ->
                    vm.diagnoseHome(req)
                    nav.navigate(Routes.REPORT)
                },
            )
        }

        composable(Routes.WEAKPICK) {
            WeakPickScreen(
                onBack = { nav.popBackStack() },
                onSelfCheck = { nav.navigate(Routes.SELFCHECK) },
                onSubmit = { weak ->
                    vm.recommendFor(weak)
                    nav.navigate(Routes.RECOMMEND)
                },
            )
        }

        composable(Routes.MEASURE) {
            MeasureScreen(
                deviceId = vm.deviceId,
                onBack = { nav.popBackStack() },
                onPickWeak = { nav.navigate(Routes.WEAKPICK) },
                onFindCenter = {
                    vm.loadCenters()
                    nav.navigate(Routes.CENTERS)
                },
                onSubmit = { req ->
                    vm.diagnose(req)
                    nav.navigate(Routes.REPORT)
                },
            )
        }

        composable(Routes.FACILITIES) {
            FacilityScreen(
                state = facilities,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadFacilities() },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CENTERS) {
            CenterScreen(
                state = centers,
                hasDiagnosis = diagnosis is UiState.Success,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadCenters() },
                onBrief = { nav.navigate(Routes.CENTER_BRIEF) },
            )
        }

        composable(Routes.CENTER_BRIEF) {
            CenterBriefScreen(
                diagnosis = (diagnosis as? UiState.Success)?.data,
                recommendation = recommendation,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.SELFCHECK) {
            SelfCheckScreen(
                deviceId = vm.deviceId,
                onBack = { nav.popBackStack() },
                onSubmit = { req ->
                    vm.selfCheck(req)
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
                initialServerUrl = vm.serverUrl,
                onBack = { nav.popBackStack() },
                onSave = {
                    vm.saveSettings(it)
                    nav.popBackStack()
                },
                onSaveServerUrl = { vm.saveServerUrl(it) },
            )
        }
    }

    if (showTutorial && onHome) {
        TutorialOverlay(steps = TUTORIAL_STEPS, onFinish = { vm.finishTutorial() })
    }
    }
}
