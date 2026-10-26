package xyz.ximanta.pluck

import android.app.Application
import xyz.ximanta.pluck.data.ApiClient
import xyz.ximanta.pluck.data.AuthStore

class PluckApplication : Application() {
    lateinit var authStore: AuthStore
        private set

    lateinit var apiClient: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        authStore = AuthStore(this)
        apiClient = ApiClient(apiKeyProvider = { authStore.currentKey() })
    }
}
