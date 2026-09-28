package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import okhttp3.OkHttpClient

/** Builds the neutral client used by Mastodon tests without coupling tests to MisskeyApi. */
internal fun mastodonTestClient(client: OkHttpClient = OkHttpClient()): AuthenticatedHttpClient =
    AuthenticatedHttpClient(client)
