package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage

/** `WasteCollectionResultChannel.results` replays its last value to every new collector. Only a
 * result echoing the *current* session may change what the operator sees or end their session. */
fun isResultForSession(result: WasteCollectionResultMessage, currentSessionId: String): Boolean =
    currentSessionId.isNotBlank() && result.operatorSessionId == currentSessionId
