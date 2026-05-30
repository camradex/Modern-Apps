package com.vayunmathur.messages.util

import com.vayunmathur.messages.gmessages.GMessagesClient
import com.vayunmathur.messages.gvoice.GVoiceClient

/**
 * Source-agnostic connection state.
 *
 * Both [GMessagesClient] and [GVoiceClient] have their own internal state
 * machines that share the same conceptual phases (idle / awaiting-setup /
 * connecting / connected / disconnected). This sealed type collapses
 * them so the UI + notification path can treat both sources uniformly,
 * without `when (source)` casts to the source-specific state class.
 */
sealed interface SourceConnectionState {
    data object Idle : SourceConnectionState
    /** Awaiting user setup. [setupHint] is a tiny inline action label
     *  the inbox uses ("Set up", "Sign in", etc). */
    data class NeedsSetup(val setupHint: String) : SourceConnectionState
    /** Active setup-in-progress with a QR code to render (Messages-for-Web
     *  only). */
    data class Pairing(val qrUrl: String) : SourceConnectionState
    data object Connecting : SourceConnectionState
    data object Connected : SourceConnectionState
    data class Disconnected(val reason: String) : SourceConnectionState
}

fun GMessagesClient.State.toUnified(): SourceConnectionState = when (this) {
    GMessagesClient.State.Idle -> SourceConnectionState.NeedsSetup("Set up")
    is GMessagesClient.State.Pairing -> SourceConnectionState.Pairing(qrUrl)
    GMessagesClient.State.Connected -> SourceConnectionState.Connected
    is GMessagesClient.State.Disconnected -> SourceConnectionState.Disconnected(reason)
}

fun GVoiceClient.State.toUnified(): SourceConnectionState = when (this) {
    GVoiceClient.State.Idle -> SourceConnectionState.Idle
    GVoiceClient.State.NeedsSetup -> SourceConnectionState.NeedsSetup("Sign in")
    GVoiceClient.State.Connecting -> SourceConnectionState.Connecting
    GVoiceClient.State.Connected -> SourceConnectionState.Connected
    is GVoiceClient.State.Disconnected -> SourceConnectionState.Disconnected(reason)
}
