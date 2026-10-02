package com.rachelsenglish.practice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class OpenLesson(val course: Course, val lesson: Lesson, val directory: File?, val offlineFallback: Boolean=false)
class CourseRepository(private val context: Context) {
    // Preserve 1.x cached courses and private preference names during upgrades.
    private val prefs=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    private val syncMutex=Mutex()
    private val bundled=parseCatalog(context.assets.open("site/catalog.json").bufferedReader().use { it.readText() })
    var courses: List<Course> = runCatching { parseCatalog(File(context.filesDir,"catalog.json").readText()) }.getOrDefault(bundled)
        private set
    companion object {
        fun parseCatalog(raw: String): List<Course> {
            val json=JSONObject(raw);require(json.getInt("schemaVersion")==1)
            val a=json.getJSONArray("courses");require(a.length()<=10000)
            val courses=(0 until a.length()).map { val c=a.getJSONObject(it)
                Course(c.getString("id"),c.getString("title"),c.optString("label"),c.getString("cover"),c.getString("version"),c.getString("sha256"),c.getString("bundle"),c.getDouble("sourceSeconds").toInt(),c.getInt("groupCount")) }
            require(courses.map { it.id }.distinct().size==courses.size)
            courses.forEach { require(it.id.matches(Regex("[A-Za-z0-9_-]{11}"))&&it.version.matches(Regex("[a-f0-9]{16}"))&&it.sha256.matches(Regex("[a-f0-9]{64}"))&&safePath(it.bundle)&&safePath(it.cover)) }
            return courses
        }
    }
    suspend fun sync(): List<Course> = withContext(Dispatchers.IO) { syncMutex.withLock {
        val raw=fetch("catalog.json",2*1024*1024);val next=parseCatalog(raw.toString(Charsets.UTF_8))
        next.forEach { c -> val cover=File(context.filesDir,"covers/${c.version}/${c.cover}")
            if(!cover.exists())runCatching { save(cover,fetch(c.cover,2*1024*1024)) }
        }
        save(File(context.filesDir,"catalog.json"),raw);courses=next;next
    } }
    private fun installed(id: String): File? {
        val version=prefs.getString("installed.$id","")?:""
        if(!version.matches(Regex("[a-f0-9]{16}")))return null
        return File(context.filesDir,"courses/$id/$version").takeIf { File(it,"lesson.json").isFile }
    }
    suspend fun open(course: Course): OpenLesson = withContext(Dispatchers.IO) { mutex.withLock {
        val existing=installed(course.id);val built=bundled.find { it.id==course.id }
        if(existing!=null&&existing.name==course.version)return@withLock OpenLesson(course,Lesson.parse(File(existing,"lesson.json").readText()),existing)
        if(built?.version==course.version)return@withLock bundledLesson(course)
        try {
            val bytes=fetch(course.bundle,25*1024*1024)
            require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }==course.sha256) { "Checksum mismatch" }
            val parent=File(context.filesDir,"courses/${course.id}").apply { mkdirs() }
            val stage=File(parent,"${course.version}.part");stage.deleteRecursively();stage.mkdirs()
            try {
                var total=0L;var entries=0
                ZipInputStream(bytes.inputStream()).use { zip ->
                    var entry=zip.nextEntry
                    while(entry!=null){require(++entries<=1000&&safePath(entry.name));val file=File(stage,entry.name)
                        if(entry.isDirectory)file.mkdirs() else {file.parentFile?.mkdirs();file.outputStream().use { out ->
                            val buffer=ByteArray(16384);var size=zip.read(buffer)
                            while(size!=-1){total+=size;require(total<=100*1024*1024);out.write(buffer,0,size);size=zip.read(buffer)}
                        }};entry=zip.nextEntry
                    }
                }
                val lesson=Lesson.parse(File(stage,"lesson.json").readText())
                (lesson.groups.map { it.audioFile }+lesson.drill.map { it.audioFile }).forEach { require(File(stage,it).isFile) }
                val destination=File(parent,course.version);destination.deleteRecursively();check(stage.renameTo(destination))
                prefs.edit().putString("installed.${course.id}",course.version).apply()
                OpenLesson(course,lesson,destination)
            } finally {stage.deleteRecursively()}
        } catch(error: Exception) {
            when { existing!=null -> OpenLesson(course,Lesson.parse(File(existing,"lesson.json").readText()),existing,true)
                built!=null -> bundledLesson(course).copy(offlineFallback=true)
                else -> throw error }
        }
    } }
    private fun bundledLesson(c: Course)=OpenLesson(c,Lesson.parse(context.assets.open("site/lessons/${c.id}.json").bufferedReader().use { it.readText() }),null)
    fun audio(open: OpenLesson,path: String): Uri {require(safePath(path));return open.directory?.let { Uri.fromFile(File(it,path)) }?:Uri.parse("asset:///site/$path")}
    suspend fun cover(c: Course): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { val f=File(context.filesDir,"covers/${c.version}/${c.cover}")
            val options=BitmapFactory.Options().apply { inSampleSize=2 }
            if(f.isFile)BitmapFactory.decodeFile(f.path,options) else context.assets.open("site/${c.cover}").use { BitmapFactory.decodeStream(it,null,options) }
        }.getOrNull()
    }
    private fun fetch(path: String,limit: Int): ByteArray {
        require(safePath(path)&&BuildConfig.CONTENT_BASE_URL.startsWith("https://"))
        val connection=URL(BuildConfig.CONTENT_BASE_URL+path).openConnection() as HttpURLConnection
        connection.connectTimeout=10000;connection.readTimeout=30000;connection.instanceFollowRedirects=false
        try {check(connection.responseCode==200);return connection.inputStream.use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);var n=input.read(buffer)
            while(n!=-1){require(out.size()+n<=limit);out.write(buffer,0,n);n=input.read(buffer)};out.toByteArray()
        }}finally {connection.disconnect()}
    }
    private fun save(file: File,bytes: ByteArray) {file.parentFile?.mkdirs();val temp=File(file.path+".tmp")
        java.io.FileOutputStream(temp).use {it.write(bytes);it.fd.sync()};Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)
    }
}
