package com.auth2fa.app

import android.app.Application
import com.auth2fa.app.data.AccountRepository

class App : Application() {

    lateinit var repository: AccountRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AccountRepository.getInstance(this)
    }
}
