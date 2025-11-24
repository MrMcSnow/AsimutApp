package com.asimut.core.sync

object CardSyncContract {
    const val PATH_BASE = "/card"
    const val PATH_REFRESH_REQUEST = "$PATH_BASE/request_refresh"
    const val PATH_SET_PRIMARY = "$PATH_BASE/set_primary"

    const val KEY_PAYLOAD = "payload"
    const val KEY_IMAGE = "image"
    const val KEY_TIMESTAMP = "timestamp"
}
