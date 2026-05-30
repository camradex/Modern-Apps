package com.vayunmathur.findfamily.uwb

import kotlinx.serialization.Serializable

/**
 * Wire format for UWB session-setup messages sent through the existing
 * E2E-encrypted UWB endpoints.
 *
 * Only the small handshake (request / ack / config / cancel) flows over the
 * server; all RangingResult samples flow peer-to-peer over the UWB radio
 * after the session is established.
 *
 * (NB: avoid writing literal `/ api / uwb / *` patterns in KDoc blocks —
 * Kotlin's block comments are nestable, so the embedded `slash-star` opens
 * a nested comment that breaks the whole file.)
 */
@Serializable
data class UwbEnvelope(
    /** Random UUID identifying this ranging session end-to-end. */
    val sessionId: String,
    /** The userid (signed long, encoded as ULong on the wire) of the sender. */
    val sender: ULong,
    /** Sender's platform tag: `"android"` or `"ios"`. */
    val senderPlatform: String,
    /** Envelope kind: `"request"`, `"ack"`, `"config"`, `"cancel"`. */
    val kind: String,
    /** Optional payload — see [UwbHandshake]. */
    val payload: UwbHandshake? = null
)

/**
 * Cross-platform UWB handshake payload. Fields are populated based on the
 * pairing — iOS↔iOS uses [discoveryTokenB64], Android↔Android uses the FiRa
 * fields, Android↔iOS uses the accessory-protocol fields.
 *
 * All fields are nullable so a single struct works for every pairing and the
 * JSON decoder tolerates older senders.
 */
@Serializable
data class UwbHandshake(
    // --- Android↔Android FiRa fields ---
    /** Local UWB MAC address of the sender (2 bytes), base64-encoded. */
    val addressB64: String? = null,
    /** FiRa complex-channel number chosen by the controller. */
    val channelNumber: Int? = null,
    /** FiRa preamble index chosen by the controller. */
    val preambleIndex: Int? = null,
    /** Random 8-byte session key chosen by the controller, base64-encoded. */
    val sessionKeyB64: String? = null,
    /** FiRa session id (Int) chosen by the controller. */
    val sessionId: Int? = null,

    // --- iOS↔iOS NearbyInteraction fields ---
    /** NIDiscoveryToken archived via NSKeyedArchiver, base64. */
    val discoveryTokenB64: String? = null,

    // --- Cross-platform (Phase 5) Apple accessory-protocol fields ---
    /** "accessoryData" sent Android → iOS at session start. */
    val accessoryConfigDataB64: String? = null,
    /** "shareableConfigurationData" sent iOS → Android. */
    val shareableConfigDataB64: String? = null,
)

object UwbEnvelopeKind {
    const val REQUEST = "request"
    const val ACK = "ack"
    const val CONFIG = "config"
    const val CANCEL = "cancel"
}
