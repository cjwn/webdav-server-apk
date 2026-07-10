package com.localdev.webdavserver.di

import android.content.Context
import com.localdev.webdavserver.data.local.datastore.SettingsDataStore
import com.localdev.webdavserver.data.local.datastore.UserPreferencesDataStore
import com.localdev.webdavserver.data.server.WebDavEngine
import com.localdev.webdavserver.domain.repository.LogRepository
import com.localdev.webdavserver.domain.repository.SettingsRepository
import com.localdev.webdavserver.domain.repository.UserRepository
import com.localdev.webdavserver.domain.usecase.AddUserUseCase
import com.localdev.webdavserver.domain.usecase.AuthenticateUseCase
import com.localdev.webdavserver.domain.usecase.BrowseFilesUseCase
import com.localdev.webdavserver.domain.usecase.DeleteUserUseCase
import com.localdev.webdavserver.domain.usecase.StartServerUseCase
import com.localdev.webdavserver.domain.usecase.StopServerUseCase

/**
 * Manual dependency container — a process-wide singleton holding lazily created
 * dependencies. Avoids an annotation processor (Hilt) to keep build times low.
 *
 * Must be initialized once from [com.localdev.webdavserver.WebDavApp.onCreate].
 */
object AppModule {

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = appContext ?: error("AppModule.init(context) must be called in Application.onCreate")

    // ---- Data sources ----
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(context) }
    val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    // ---- Repositories ----
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val userRepository: UserRepository by lazy { UserRepository(userPreferencesDataStore) }
    val logRepository: LogRepository by lazy { LogRepository() }

    // ---- Server engine (single shared instance) ----
    val webDavEngine: WebDavEngine by lazy {
        WebDavEngine(context, userRepository, settingsRepository, logRepository)
    }

    // ---- Use cases ----
    val startServerUseCase: StartServerUseCase by lazy { StartServerUseCase(context) }
    val stopServerUseCase: StopServerUseCase by lazy { StopServerUseCase(context) }
    val addUserUseCase: AddUserUseCase by lazy {
        AddUserUseCase(userRepository, settingsRepository)
    }
    val deleteUserUseCase: DeleteUserUseCase by lazy { DeleteUserUseCase(userRepository) }
    val authenticateUseCase: AuthenticateUseCase by lazy { AuthenticateUseCase(userRepository) }
    val browseFilesUseCase: BrowseFilesUseCase by lazy { BrowseFilesUseCase(context) }
    val fileOperationsUseCase: com.localdev.webdavserver.domain.usecase.FileOperationsUseCase by lazy {
        com.localdev.webdavserver.domain.usecase.FileOperationsUseCase()
    }
}
