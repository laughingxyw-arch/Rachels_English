package com.rachelsenglish.practice

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

// The listening session survives Activity recreation and background navigation.
class PracticeApplication: Application(), ViewModelStoreOwner {
    override val viewModelStore=ViewModelStore()
    val model: PracticeModel by lazy {
        ViewModelProvider(viewModelStore,ViewModelProvider.AndroidViewModelFactory(this))[PracticeModel::class.java]
    }
}
