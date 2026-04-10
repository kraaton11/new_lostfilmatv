package com.kraat.lostfilmnewtv.updates

enum class UpdateCheckMode(val storageValue: String) {
    MANUAL("manual"),
    QUIET_CHECK("quiet_check");

    companion object {
        fun fromStorageValue(value: String?): UpdateCheckMode =
            entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}
