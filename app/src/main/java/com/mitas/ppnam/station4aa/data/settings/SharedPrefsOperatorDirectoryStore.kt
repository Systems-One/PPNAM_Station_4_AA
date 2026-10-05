package com.mitas.ppnam.station4aa.data.settings

import android.content.Context
import com.mitas.ppnam.station4aa.domain.login.OperatorDirectoryStore
import com.mitas.ppnam.station4aa.domain.model.OperatorEntry

/**
 * Operator directory cache in plain SharedPreferences `operator_directory` / `operators`, the
 * same file and key Station 1 AA uses, as a JSON array (see [OperatorDirectoryCodec]). Plain
 * prefs are fine: the list is display-only usernames and display names, nothing secret.
 */
class SharedPrefsOperatorDirectoryStore(context: Context) : OperatorDirectoryStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<OperatorEntry> = OperatorDirectoryCodec.decode(prefs.getString(KEY_OPERATORS, null))

    override fun save(entries: List<OperatorEntry>) {
        prefs.edit().putString(KEY_OPERATORS, OperatorDirectoryCodec.encode(entries)).apply()
    }

    private companion object {
        const val PREFS_NAME = "operator_directory"
        const val KEY_OPERATORS = "operators"
    }
}
