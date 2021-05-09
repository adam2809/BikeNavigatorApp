package com.example.bikenavigatorapp

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException

class VolleyJsonRequest(
    method:Int,
    url:String,
    private val paramMap:Map<String, String>,
    private val resListener:Response.Listener<JSONObject>,
    errorListener:Response.ErrorListener
) : Request<JSONObject>(
    method,
    url,
    errorListener
) {
    private companion object{
        const val TAG = "VolleyJsonRequest";
    }

    override fun getParams(): Map<String, String> {
        return paramMap
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> {
        return try {
            val jsonString = String(
                response.data,
                Charsets.UTF_8
            )
            Response.success(
                JSONObject(jsonString),
                HttpHeaderParser.parseCacheHeaders(response)
            )
        } catch (e: UnsupportedEncodingException) {
            Response.error(ParseError(e))
        } catch (je: JSONException) {
            Response.error(ParseError(je))
        }
    }

    override fun deliverResponse(response: JSONObject) {
        resListener.onResponse(response)
    }
}
