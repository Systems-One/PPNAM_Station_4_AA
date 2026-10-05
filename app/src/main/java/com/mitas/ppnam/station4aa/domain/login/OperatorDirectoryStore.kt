package com.mitas.ppnam.station4aa.domain.login

import com.mitas.ppnam.station4aa.domain.model.OperatorEntry

/**
 * Where the last accepted operator list lives between app starts, so the login dropdown is
 * populated before — or without — Station 4 answering `operator_list_requested`. Persisted in
 * production (SharedPreferences `operator_directory`, see `SharedPrefsOperatorDirectoryStore`);
 * in-memory for tests. Written only on an accepted response: any failure keeps the previous list.
 */
interface OperatorDirectoryStore {
    /** The cached list, or empty when nothing has been cached yet (or the cache is unreadable). */
    fun load(): List<OperatorEntry>

    fun save(entries: List<OperatorEntry>)
}

open class InMemoryOperatorDirectoryStore : OperatorDirectoryStore {
    private var entries: List<OperatorEntry> = emptyList()
    override fun load(): List<OperatorEntry> = entries
    override fun save(entries: List<OperatorEntry>) { this.entries = entries }
}
