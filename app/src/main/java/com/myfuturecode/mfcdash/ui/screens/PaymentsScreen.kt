package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState

/** Payments: har app ki list (route "payments"). */
@Composable
fun PaymentsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
}

/** Payments: ek app ke saare payments (route "payments/app/{pkg}"). */
@Composable
fun PaymentsAppScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
}
