package com.asimut.models

data class StudentCard(
    val id: String,
    val firstName: String,
    val lastName: String,
    val matrikelnummer: String,
    val birthDate: String,
    val nfcTagId: String? = null,
    val nfcPayload: String? = null
)
