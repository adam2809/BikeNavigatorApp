package com.example.bikenavigatorapp

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException

class Navigator(private val context:Context) {
    private companion object{
        const val DIR_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
        const val TAG = "Navigator";
    }

    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }
    private val queue: RequestQueue = Volley.newRequestQueue(context)

    private val navRequest = object : JsonObjectRequest(
        Method.GET, DIR_API_URL, JSONObject(mapOf(
            "origin" to "52.11479,21.00070",
            "destination" to "52.235782,20.940026",
            "mode" to "bicycling",
            "key" to BuildConfig.DIR_API_KEY
        )),
        {
            Log.i(TAG,"Directions API returned: $it")
        },
        { error ->
            error.networkResponse.let {
                Log.w(TAG,"Error while requesting new directions: code=${it.statusCode} msg=${String(it.data,kotlin.text.Charsets.UTF_8)}")
            }
        }
    ) {
        override fun getParams(): Map<String, String>{
            return mapOf(
                "origin" to "52.11479,21.00070",
                "destination" to "52.235782,20.940026",
                "mode" to "bicycling",
                "key" to BuildConfig.DIR_API_KEY
            )
        }
    }

    fun updateDirections(){
        val req = VolleyJsonRequest(
            Request.Method.GET,
            DIR_API_URL,
            mapOf(
                "origin" to "52.11479,21.00070",
                "destination" to "52.235782,20.940026",
                "mode" to "bicycling",
                "key" to BuildConfig.DIR_API_KEY
            ), Response.Listener {
                Log.i(TAG,"Directions API returned: $it")
            },Response.ErrorListener { error ->
                error.networkResponse.let {
                    Log.w(
                        TAG,
                        "Error while requesting new directions: code=${it.statusCode} msg=${
                            String(
                                it.data,
                                kotlin.text.Charsets.UTF_8
                            )
                        }"
                    )
                }
            }
        )
        req.setShouldCache(false)
        queue.add(req)
    }
}