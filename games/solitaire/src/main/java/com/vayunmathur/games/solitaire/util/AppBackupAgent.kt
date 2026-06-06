package com.vayunmathur.games.solitaire.util

import com.vayunmathur.library.util.BaseBackupAgent

class AppBackupAgent : BaseBackupAgent() {
    override val prefNames: List<String>
        get() = listOf("solitaire_stats")
}
