package me.foxtails.palustris.data.transport

import java.io.IOException

/** A protocol-neutral HTTP failure with the bounded response body. */
class HttpStatusFailure(val status: Int, val body: String) : IOException("HTTP status $status")

/** The response body exceeded the caller-owned bound. */
class ResponseLimitExceeded : IOException()
