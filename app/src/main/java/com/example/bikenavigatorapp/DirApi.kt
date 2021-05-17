package com.example.bikenavigatorapp

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject


class DirApi(context:MainActivity) {
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
    ){
    }
    private val mapper = jacksonObjectMapper()
    var steps = emptyList<Step>()
    val queue: RequestQueue = Volley.newRequestQueue(context)

    private fun JSONObject.getNonEmptyArrayElement(key:String):JSONArray?{
        val arr = (this.get(key) as JSONArray)
        if (arr.length() == 0) {
            Log.w(REQ_TAG, "Array $key is  empty")
            return null
        }
        return arr
    }

    private val navRequest = JsonObjectRequest(
        Request.Method.GET, "$DIR_API_URL?${getUrlParams()}", null,
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
                Log.w(TAG,"Error while requesting new directions: code=${it.statusCode} msg=${String(it.data,Charsets.UTF_8)}")
            }
        }
    )

    private fun getUrlParams():String{
        return "origin=52.141117,20.717935&destination=52.097200,20.642875&mode=bicycling&key=${BuildConfig.DIR_API_KEY}"
    }

    fun updateSteps(){
        queue.add(navRequest)
    }
}