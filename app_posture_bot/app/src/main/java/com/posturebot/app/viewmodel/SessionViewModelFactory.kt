package com.posturebot.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.posturebot.app.data.db.PostureDao

class SessionViewModelFactory(
    private val application: Application,
    private val postureDao: PostureDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(application).apply {
                initialize(postureDao)
            } as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
