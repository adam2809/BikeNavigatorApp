package com.example.bikenavigatorapp

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
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

    private fun getUrlParams():String{
        return "origin=52.11479%2C21.00070&destination=52.235782%2C20.940026&mode=bicycling&key=${BuildConfig.DIR_API_KEY}"
    }

    private val navRequest = JsonObjectRequest(
        Request.Method.GET, "$DIR_API_URL?${getUrlParams()}", null,
        {
            Log.i(TAG,"Directions API returned: $it")
        },
        { error ->
            error.networkResponse.let {
                Log.w(TAG,"Error while requesting new directions: code=${it.statusCode} msg=${String(it.data,Charsets.UTF_8)}")
            }
        }
    )
    private val queue: RequestQueue = Volley.newRequestQueue(context)

    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    fun updateDirections(){
        queue.add(navRequest)
    }
}