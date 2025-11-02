package com.asimut.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asimut.wear.data.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CardListViewModel(private val repository: CardRepository) : ViewModel() {

    val cards: StateFlow<List<CardRepository.CardEntry>> = repository.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun isDeviceSecure(): Boolean = repository.hasDeviceSecurity()

    class Factory(private val repository: CardRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CardListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CardListViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
