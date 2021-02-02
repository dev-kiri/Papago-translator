package com.kiri.papago

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.jsoup.Jsoup
import org.json.JSONObject
import org.jsoup.Connection

class Papago(
    var source: String,
    val target: String,
    val text: String
) {

    companion object {
        const val algorithm = "HmacMD5"
        const val auth_key = "v1.5.3_e6026dbabc"
        const val URL_DETECT = "https://papago.naver.com/apis/langs/dect"
        const val URL_TRANSLATE = "https://papago.naver.com/apis/n2mt/translate"
    }

    private fun encrypt(m: String): String {
        val hash = Mac.getInstance(algorithm).run {
            init(SecretKeySpec(auth_key.toByteArray(), algorithm))
            doFinal(m.toByteArray(charset("ASCII")))
        }
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun getUUID(): String = UUID.randomUUID().toString()

    fun translate(honorific: Boolean = false): HashMap<String, String?> {
        val map = HashMap<String, String?>()
        val time = Date().time
        val uuid = getUUID()
        val hashedString = encrypt("$uuid\n$URL_TRANSLATE\n$time")
        val auth = "PPG $uuid:$hashedString"
        if (source == "detect") {
            val body = Jsoup.connect(URL_DETECT).run {
                header("Authorization", "PPG $uuid:${encrypt("$uuid\n$URL_DETECT\n$time")}")
                header("Timestamp", "$time")
                data("query", "text")
                ignoreContentType(true)
                post()
            }.text()
            val langCode = JSONObject(body).getString("langCode")
            if (langCode == "unk") throw Exception("cannot detect text")
            source = langCode
        }
        val response = Jsoup.connect(URL_TRANSLATE).run {
            method(Connection.Method.POST)
            header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            header("Authoriztion", auth)
            header("Timestamp", "$time")
            header("Device-Type", "pc")
            header("X-Apigw-Partnerid", "papago")
            referrer("https://papago.naver.com/")
            data("deviceId", uuid)
            data("locale", "ko")
            data("dict", "true")
            data("dictDisplay", "30")
            data("honorific", "$honorific")
            data("instant", "false")
            data("paging", "false")
            data("source", source)
            data("target", target)
            data("text", text)
            data("authorization", auth)
            data("timestamp", "$time")
            ignoreContentType(true)
            execute()
        }
        val body = response.body()
        when (response.statusCode()) {
            200 -> {
                val json = JSONObject(body)
                val translatedText = json.getString("translatedText")
                var sound: String? = null
                var srcSound: String? = null
                if (json.has("tlit")) {
                    val result = ArrayList<String>()
                    val tlits = json.getJSONObject("tlit").getJSONObject("message").getJSONArray("tlitResult")
                    for (i in 0 until tlits.length()) {
                        val tlit = tlits.getJSONObject(i)
                        result.add(tlit.getString("phoneme"))
                    }
                    sound = result.joinToString(separator = " ")
                }
                if (json.has("tlitSrc")) {
                    val result = ArrayList<String>()
                    val tlits = json.getJSONObject("tlitSrc").getJSONObject("message").getJSONArray("tlitResult")
                    for (i in 0 until tlits.length()) {
                        val tlit = tlits.getJSONObject(i)
                        result.add(tlit.getString("phoneme"))
                    }
                    srcSound = result.joinToString(separator = " ")
                }
                map.run {
                    put("source", source)
                    put("target", target)
                    put("text", text)
                    put("translatedText", translatedText)
                    put("sound", sound)
                    put("srcSound", srcSound)
                }
                return map
            }
            403, 404 -> throw Exception(body)
            500 -> throw Exception("Invalid langCode parameter")
            else -> throw Exception("Unexpected Error")
        }
    }
}
