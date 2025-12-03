import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkModule {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class HttpClient(private val client: OkHttpClient = NetworkModule.httpClient) {
    private val activeCalls = mutableSetOf<Call>()

    fun makeRequest(url: String, callback: (String?, Exception?) -> Unit): Call {
        val request = Request.Builder()
            .url(url)
            .build()

        val call = client.newCall(request)
        activeCalls.add(call)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activeCalls.remove(call)
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                activeCalls.remove(call)
                response.use {
                    val body = it.body?.string()
                    if (it.isSuccessful && body != null) {
                        callback(body, null)
                    } else {
                        callback(null, IOException("Unexpected code ${it.code}"))
                    }
                }
            }
        })

        return call
    }

    fun cancelAll() {
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
    }
}