package com.asimut.core.model

sealed class PassPayload {
    data class StudentCard(
        val id: String,
        val firstName: String,
        val lastName: String,
        val matrikelnummer: String,
        val birthDate: String,
        val nfcTagId: String? = null,
        val nfcPayload: String? = null
    ) : PassPayload()

    data class DeutschlandTicket(
        val id: String,
        val title: String,
        val subtitle: String?,
        val barcodeMessage: String,
        val barcodeFormat: String,
        val validFrom: String?,
        val validTo: String?,
        val expirationDate: String?,
        val holder: String?
    ) : PassPayload()

    data class MensaCard(
        val id: String,
        val cardNumber: String,
        val balance: String?,
        val lastUpdated: String?
    ) : PassPayload()
}
