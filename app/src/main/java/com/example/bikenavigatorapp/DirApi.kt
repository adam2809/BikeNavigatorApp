package com.example.bikenavigatorapp

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class DirApi(
    context: Context,
    private val onSuccessCb: (List<Step>) -> Unit
) {
    private companion object {
        const val DIR_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private val TAG = "${DirApi::class.java.simpleName}(bnalt)"
        const val REQ_TAG = "DirApiRequest";
    }

    private val mapper = jacksonObjectMapper()
    private val queue: RequestQueue = Volley.newRequestQueue(context, object : HurlStack() {
        @Throws(IOException::class)
        override fun createConnection(url: URL?): HttpURLConnection {
            val connection: HttpURLConnection = super.createConnection(url)
            connection.instanceFollowRedirects = false
            return connection
        }
    })


    private fun JSONObject.getNonEmptyArrayElement(key: String): JSONArray? {
        val arr = (this.get(key) as JSONArray)
        if (arr.length() == 0) {
            Log.w(REQ_TAG, "Array $key is  empty")
            return null
        }
        return arr
    }

    private fun List<Step>.addIndexes() {
        this.forEachIndexed { i, step ->
            step.index = i
        }
    }

    inner class DirApiRequest(params: String, routeIndex: Int) : JsonObjectRequest(
        Request.Method.GET, "$DIR_API_URL?${params}", null,
        resListener@{ res ->
            Log.i(REQ_TAG, "Response: $res")
            if (res.get("status") != "OK") {
                Log.w(TAG, "Response has error status")
                return@resListener
            }

            val stepsJson: JSONArray = res.let {
                val routes = it.getNonEmptyArrayElement("routes")

                return@let if (routeIndex <= routes?.length()?.minus(1) ?: routeIndex) {
                    routes?.get(routeIndex) as JSONObject
                } else {
                    routes?.get(0) as JSONObject
                }
            }.let {
                it.getNonEmptyArrayElement("legs")?.get(0) as JSONObject
            }.getNonEmptyArrayElement("steps") ?: return@resListener


            val steps = mapper.readValue<List<Step>>(
                stepsJson.toString().also { Log.d(REQ_TAG, "Steps array: $it") })
            steps.addIndexes()

            Log.i(REQ_TAG, "Steps successfully updated new count is ${steps.size}")
            Log.d(REQ_TAG, "New steps: $steps")

            onSuccessCb(steps)
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

    private fun getUrlParams(origin: Loc, dest: Loc): String {
        return "origin=${origin.lat},${origin.lng}&destination=${dest.lat},${dest.lng}&mode=bicycling&key=${BuildConfig.DIR_API_KEY}&alternatives=true"
    }

    fun updateSteps(start: Loc, sharePlaceUrl: String) {
        queue.add(ResolveSharePlaceUrlRequest(sharePlaceUrl) { dest, routeIndex ->
            updateSteps(start, dest, routeIndex)
        })
    }


    @SuppressLint("MissingPermission")
    fun updateSteps(start: Loc, dest: Loc, routeIndex: Int) {
        queue.add(
            DirApiRequest(
                getUrlParams(
                    start,
                    dest
                ),
                routeIndex
            )
        )
    }
}