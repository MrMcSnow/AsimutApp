package com.asimut.models

data class Dticket(
    val id: String,
    val title: String,
    val subtitle: String?,
    val barcodeMessage: String,
    val barcodeFormat: String,
    val validFrom: String?,
    val validTo: String?,
    val expirationDate: String?,
    val holder: String?,
    val pkpassLocalPath: String
)
