package com.localdev.webdavserver.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.data.local.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores users in [EncryptedSharedPreferences]. Although DataStore is the
 * stated persistence layer for settings, user credentials must be encrypted at
 * rest, so we wrap EncryptedSharedPreferences behind a DataStore-style reactive
 * adapter exposing a [StateFlow].
 */
class UserPreferencesDataStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "webdav_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _users = MutableStateFlow(loadAll())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    private fun loadAll(): List<UserEntity> {
        val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toUser() }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<UserEntity>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_USERS, arr.toString()).apply()
        _users.value = list
    }

    suspend fun upsert(user: UserEntity) = withContext(Dispatchers.IO) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.id == user.id }
        if (idx >= 0) current[idx] = user else current.add(user)
        persist(current)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        persist(loadAll().filterNot { it.id == id })
    }

    fun snapshot(): List<UserEntity> = _users.value

    fun findByUsername(username: String): UserEntity? =
        _users.value.firstOrNull { it.username.equals(username, ignoreCase = true) }

    private fun JSONObject.toUser() = UserEntity(
        id = getString("id"),
        username = getString("username"),
        passwordHash = getString("passwordHash"),
        salt = getString("salt"),
        role = Role.valueOf(getString("role")),
        readOnly = getBoolean("readOnly"),
        rootFolderUri = if (isNull("rootFolderUri")) null else getString("rootFolderUri")
    )

    private fun UserEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("username", username)
        put("passwordHash", passwordHash)
        put("salt", salt)
        put("role", role.name)
        put("readOnly", readOnly)
        put("rootFolderUri", rootFolderUri ?: JSONObject.NULL)
    }

    private companion object {
        const val KEY_USERS = "users_json"
    }
}
