package tk.zwander.common.tools

import com.soywiz.korio.async.runBlockingNoJs
import com.soywiz.korio.net.http.Http
import com.soywiz.korio.stream.AsyncInputStream
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.request
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.internal.*

@DangerousInternalIoApi
class FusClient(
    var auth: String = "",
    var sessId: String = ""
) {
    var encNonce = ""
    var nonce = ""

    init {
        runBlockingNoJs {
            makeReq("NF_DownloadGenerateNonce.do")
        }
    }

    suspend fun makeReq(path: String, data: String = ""): String {
        val authV = "FUS nonce=\"\", signature=\"${this.auth}\", nc=\"\", type=\"\", realm=\"\", newauth=\"1\""

        val client = HttpClient()

        val response = client.request<HttpResponse>(HttpRequestBuilder().apply {
            url("https://neofussvr.sslcs.cdngc.net/${path}")
            method = HttpMethod.Post
            headers {
                append("Authorization", authV)
                append("User-Agent", "Kies2.0_FUS")
                append("Cookie", "JSESSIONID=${sessId}")
                append("Set-Cookie", "JSESSIONID=${sessId}")
            }
            body = data
        })

        if (response.headers["NONCE"] != null) {
            encNonce = response.headers["NONCE"] ?: ""
            nonce = Auth.decryptNonce(encNonce)
            auth = Auth.getAuth(nonce)
        }

        if (response.headers["Set-Cookie"] != null) {
            sessId = response.headers.entries().find { it.value.any { it.contains("JSESSIONID=") } }
                ?.value?.find {
                    it.contains("JSESSIONID=")
                }
                ?.replace("JSESSIONID=", "")
                ?.replace(Regex(";.*$"), "") ?: sessId
        }

        client.close()

        return response.readText()
    }

    suspend fun downloadFile(fileName: String, start: Long = 0): Pair<AsyncInputStream, String?> {
        val authV =
            "FUS nonce=\"${encNonce}\", signature=\"${this.auth}\", nc=\"\", type=\"\", realm=\"\", newauth=\"1\""

        val client = com.soywiz.korio.net.http.HttpClient()

        val response = client.request(
            Http.Method.GET,
            "http://cloud-neofussvr.sslcs.cdngc.net/NF_DownloadBinaryForMass.do?file=${fileName}",
            headers = Http.Headers(
                "Authorization" to authV,
                "User-Agent" to "Kies2.0_FUS"
            ).run {
                if (start > 0) {
                    this.plus(Http.Headers("Range" to "bytes=$start-"))
                } else this
            }
        )

        return response.content to response.headers["Content-MD5"]
    }
}