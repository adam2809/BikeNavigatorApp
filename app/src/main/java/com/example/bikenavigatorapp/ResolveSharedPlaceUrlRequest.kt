package com.example.bikenavigatorapp

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import java.net.HttpURLConnection


class ResolveSharePlaceUrlRequest(
    url: String,
    onSuccessCb: (Loc, Int) -> Unit
) : Request<Map<String, String>>(Method.GET, url, errorListener@{ error ->
    error?.networkResponse?.let {
        if (it.statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            handleRedirect(error, onSuccessCb)
        } else {
            Log.w(TAG, "Error while getting location from share place link")
            return@errorListener
        }
    } ?: run {
        Log.w(TAG, "Error network response is null")
        return@errorListener
    }

}) {
    companion object {
        val TAG = "${ResolveSharePlaceUrlRequest::class.java.simpleName}(bnalt)"
    }

    private val listener: Response.Listener<Map<String, String>> = Response.Listener {
        Log.i(TAG, "Got headers: $it")
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<Map<String, String>>? {
        Log.i(TAG, "Got response with code ${response.statusCode}")
        return Response.success(response.headers, HttpHeaderParser.parseCacheHeaders(response))
    }

    override fun deliverResponse(response: Map<String, String>?) {
        listener.onResponse(response)
    }

}

private fun handleRedirect(error: VolleyError, onSuccessCb: (Loc, Int) -> Unit) {
    val locationRedirectUrl: String = error.networkResponse?.headers?.get("Location") ?: run {
        Log.e(ResolveSharePlaceUrlRequest.TAG, "Location header missing")
        return
    }
    val dest = getLocationFromRedirectUrl(locationRedirectUrl)?.also {
        Log.w(ResolveSharePlaceUrlRequest.TAG, "Extracted location from redirect url: $it")
    } ?: run {
        Log.e(ResolveSharePlaceUrlRequest.TAG, "Could not find location in url")
        return
    }

    val routeIndex = getRouteIndexFromRedirectUrl(locationRedirectUrl).also {
        Log.w(ResolveSharePlaceUrlRequest.TAG, "Extracted route index from redirect url: $it")
    }

    onSuccessCb(dest, routeIndex)
}


private fun getLocationFromRedirectUrl(url: String): Loc? {
    val locRes = DESTINATION_LOCATION_REGEX.find(url)
    locRes?.groupValues?.let {
        if (it.size != 3) {
            return@let null
        }
        return Loc(it[1].toDouble(), it[2].toDouble())
    } ?: run {
        return null
    }
}

private fun getRouteIndexFromRedirectUrl(url: String): Int {
    val indexDataRes = ROUTE_INDEX_DATA_REGEX.find(url)

    val groups = indexDataRes?.groupValues ?: run {
        Log.w(ResolveSharePlaceUrlRequest.TAG, "Route index data regex groups are null")
        return 0
    }
    if (groups.size != 2) {
        return 0
    }

    groups[1].let {
        return if (it.isEmpty()) {
            0
        } else {
            it[it.lastIndex] - '0'
        }
    }
}
