package com.auth2fa.app

import android.app.Application
import com.auth2fa.app.data.AccountRepository
import com.auth2fa.app.rust.RustBridge

class App : Application() {

    lateinit var repository: AccountRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AccountRepository.getInstance(this)

        // Initialize Rust core with database path
        val dbPath = getDatabasePath("auth2fa_database").absolutePath
        RustBridge.init(dbPath)
    }
}
