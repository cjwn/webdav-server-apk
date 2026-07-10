package com.localdev.webdavserver.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Generic ViewModel factory that delegates construction to a lambda. Lets us
 * inject AppModule dependencies without an annotation processor.
 */
class GenericViewModelFactory(
    private val creator: () -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
