package com.rachelsenglish.practice

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom

data class StudyContribution(val device: String,val row: StudyDay)
/** Merge per-installation monotonic counters, then aggregate. Retries cannot add time twice. */
fun mergeStudy(localDevice: String,local: List<StudyDay>,remote: List<StudyContribution>): List<StudyDay> {
    val shards=remote.associateBy {Triple(it.device,it.row.day,it.row.course)}.toMutableMap()
    for(row in local){val key=Triple(localDevice,row.day,row.course);val previous=shards[key]?.row
        shards[key]=StudyContribution(localDevice,row.copy(audioMs=maxOf(row.audioMs,previous?.audioMs?:0),shadowMs=maxOf(row.shadowMs,previous?.shadowMs?:0)))}
    return shards.values.groupBy {it.row.day to it.row.course}.map {(key,items)->StudyDay(key.first,key.second,items.sumOf {it.row.audioMs},items.sumOf {it.row.shadowMs})}
}
fun studySecret(): String=ByteArray(32).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
class StudyCloud(private val base: String=BuildConfig.CONTENT_BASE_URL) {
    private fun request(secret: String,method: String,body: JSONObject?=null,offset: Int=0): JSONObject {
        val connection=URI(base.trimEnd('/')+"/api/study?offset=$offset").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15_000;connection.readTimeout=20_000;connection.instanceFollowRedirects=false
            connection.requestMethod=method;connection.setRequestProperty("Authorization","Bearer $secret")
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            check(connection.responseCode==200){"Sync unavailable"}
            return JSONObject(connection.inputStream.bufferedReader().use {it.readText()})
        }finally{connection.disconnect()}
    }
    fun upload(secret: String,device: String,rows: List<StudyDay>) {
        val batches=if(rows.isEmpty())listOf(emptyList()) else rows.chunked(100)
        for(batch in batches){val array=JSONArray();batch.forEach {array.put(JSONObject().put("day",it.day).put("course",it.course).put("audioMs",it.audioMs).put("shadowMs",it.shadowMs))}
            request(secret,"PUT",JSONObject().put("device",device).put("rows",array))}
    }
    fun download(secret: String): List<StudyContribution> {
        val rows=mutableListOf<StudyContribution>();var offset=0
        do {val body=request(secret,"GET",offset=offset);val array=body.getJSONArray("rows")
            for(i in 0 until array.length()){val r=array.getJSONObject(i);rows.add(StudyContribution(r.getString("device"),StudyDay(r.getString("day"),r.getString("course"),r.getLong("audioMs"),r.getLong("shadowMs"))))}
            offset=if(body.isNull("next"))-1 else body.getInt("next")
        }while(offset>=0)
        return rows
    }
}
