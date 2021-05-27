package com.example.bikenavigatorapp

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import java.net.HttpURLConnection


class HeadersRequest(
    url: String,
    private val onSuccessCb: (DirApi.Location?) -> Unit
) : Request<Map<String, String>>(Method.GET, url, errorListener@{ error ->
    error?.networkResponse?.let {
        if (it.statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            handleRedirect(error)
        } else {
            Log.w(TAG, "Error while getting location from share place link")
            return@errorListener
        }
    } ?: run {
        Log.w(TAG, "Error network response is null")
        return@errorListener
    }

}) {
    private companion object {
        const val TAG = "HeadersRequest";
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


    fun handleRedirect(error: VolleyError) {
        val locationRedirectUrl: String = error.networkResponse?.headers?.get("Location") ?: run {
            Log.e(TAG, "Location header missing")
            return
        }
        getLocationFromRedirectUrl(locationRedirectUrl)?.let {
            Log.w(TAG, "Extracted location from redirect url: $it")
            onSuccessCb(it)
        } ?: run {
            Log.e(TAG, "Could not find location in url")
            return
        }
    }


    private fun getLocationFromRedirectUrl(url: String): DirApi.Location? {
        val locRes = LOCATION_REGEX.find(url)
        locRes?.groupValues?.let {
            if (it.size != 3) {
                return@let null
            }
            return DirApi.Location(it[1].toDouble(), it[2].toDouble())
        } ?: run {
            return null
        }
    }
}
