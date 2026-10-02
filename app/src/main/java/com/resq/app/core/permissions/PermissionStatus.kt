package com.resq.app.core.permissions

/**
 * Three states, not two — "denied" (fixable by the user, right now, in
 * this app) and "unavailable" (this device has no radio/sensor for it,
 * no amount of tapping "grant" will help) need different UI: a grant
 * button for the former, nothing actionable for the latter.
 */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    UNAVAILABLE,
}
