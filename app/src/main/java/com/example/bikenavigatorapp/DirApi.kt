package com.example.bikenavigatorapp

import android.annotation.SuppressLint
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class DirApi(private val context:MainActivity) {
    private companion object{
        const val DIR_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
        const val TAG = "Navigator";
        const val REQ_TAG = "DirApiRequest";
    }

    data class TextVal(val text:String,val value:Int)
    data class Location(val lat:Double,val lng:Double)
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Step(
        val distance: TextVal,
        val duration: TextVal,
        val maneuver: String?,
        @JsonProperty("start_location")
        val startLocation: Location,
        @JsonProperty("end_location")
        val endLocation: Location,
        @JsonProperty("html_instructions")
        val htmlInstructions: String,
        @JsonProperty("travel_mode")
        val travelMode: String
    ) {
    }

    private val mapper = jacksonObjectMapper()
    var steps = emptyList<Step>()
    private val queue: RequestQueue = Volley.newRequestQueue(context, object : HurlStack() {
        @Throws(IOException::class)
        override fun createConnection(url: URL?): HttpURLConnection {
            val connection: HttpURLConnection = super.createConnection(url)
            connection.instanceFollowRedirects = false
            return connection
        }
    })

    class HeadersRequest(
        url: String,
        private val listener: Response.Listener<Map<String, String>>,
        errorListener: Response.ErrorListener
    ) : Request<Map<String, String>>(Method.GET, url, errorListener) {
        private companion object {
            const val TAG = "HeadersRequest";
        }

        override fun parseNetworkResponse(response: NetworkResponse): Response<Map<String, String>>? {
            Log.i(TAG, "Got response with code ${response.statusCode}")
            return Response.success(response.headers, HttpHeaderParser.parseCacheHeaders(response))
        }

        override fun deliverResponse(response: Map<String, String>?) {
            listener.onResponse(response)
        }
    }

    private fun JSONObject.getNonEmptyArrayElement(key: String): JSONArray? {
        val arr = (this.get(key) as JSONArray)
        if (arr.length() == 0) {
            Log.w(REQ_TAG, "Array $key is  empty")
            return null
        }
        return arr
    }

    inner class DirApiRequest(params:String) :  JsonObjectRequest(
        Request.Method.GET, "$DIR_API_URL?${params}", null,
        resListener@{ res ->
            Log.i(REQ_TAG,"Response: $res")
            if(res.get("status") != "OK"){
                Log.w(TAG,"Response has error status")
                return@resListener
            }

            val stepsJson:JSONArray = res.let {
                it.getNonEmptyArrayElement("routes")?.get(0) as JSONObject
            }.let {
                it.getNonEmptyArrayElement("legs")?.get(0) as JSONObject
            }.getNonEmptyArrayElement("steps") ?: return@resListener


            steps = mapper.readValue(stepsJson.toString().also { Log.d(REQ_TAG,"Steps array: $it") })

            Log.i(REQ_TAG,"Steps successfully updated new count is ${steps.size}")
            Log.d(REQ_TAG,"New steps: $steps")
        },
        { error ->
            error.networkResponse.let {
                Log.w(
                    TAG,
                    "Error while requesting new directions: code=${it.statusCode} msg=${
                        String(
                            it.data,
                            Charsets.UTF_8
                        )
                    }"
                )
            }
        }
    )

    private fun getUrlParams(origin:Location,dest:Location): String {
        return "origin=${origin.lat},${origin.lng}&destination=${dest.lat},${dest.lng}&mode=bicycling&key=${BuildConfig.DIR_API_KEY}"
    }

    fun updateStepsFromSharePlaceUrl(url: String) {
        queue.add(HeadersRequest(url, Response.Listener {
            Log.i(TAG, "Got headers: $it")
        }, Response.ErrorListener errorListener@{ error ->
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

        }))
    }

    fun getLocationFromRedirectUrl(url: String): Location? {
        val locRes = LOCATION_REGEX.find(url)
        locRes?.groupValues?.let {
            if (it.size != 3) {
                return@let null
            }
            return Location(it[1].toDouble(), it[2].toDouble())
        } ?: run {
            return null
        }
    }

    fun handleRedirect(error: VolleyError) {
        val locationRedirectUrl: String = error.networkResponse?.headers?.get("Location") ?: run {
            Log.e(TAG, "Location header missing")
            return
        }
        getLocationFromRedirectUrl(locationRedirectUrl)?.let {
            Log.w(TAG, "Extracted location from redirect url: $it")
            updateSteps(it)
        } ?: run {
            Log.e(TAG, "Could not find location in url")
            return
        }
    }

    @SuppressLint("MissingPermission")
    fun updateSteps(dest:Location) {
        context.locClient.lastLocation.addOnSuccessListener listener@{ res ->
            queue.add(DirApiRequest(getUrlParams(
                Location(res.latitude,res.longitude),
                dest
            )))
        }
    }
}