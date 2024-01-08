


import okhttp3.*
import java.io.IOException

class HttpClient(private val client: OkHttpClient) {
    fun makeRequest(url: String, callback: (String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    callback(body, null)
                } else {
                    callback(null, IOException("Unexpected code $response"))
                }
            }
        })
    }


}