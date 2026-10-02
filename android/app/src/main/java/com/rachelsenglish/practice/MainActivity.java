package com.rachelsenglish.practice;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Local interface and playback; the network is used only for course data. */
public class MainActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net/";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WebView web;
    private volatile JSONObject catalog;
    private JSONObject bundled;
    private volatile String active="";
    private volatile boolean busy=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            bundled=new JSONObject(read(getAssets().open("site/catalog.json")));
            File saved=new File(getFilesDir(),"catalog.json");
            catalog=bundled;validate(bundled);
            if(saved.exists()){try{JSONObject savedCatalog=new JSONObject(read(new FileInputStream(saved)));validate(savedCatalog);catalog=savedCatalog;}catch(Exception ignored){}}
        } catch(Exception e) { throw new IllegalStateException("Bundled catalog unavailable",e); }
        web=new WebView(this);
        web.setBackgroundColor(0xfff7f8fb);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(),"PracticeApp");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri u=request.getUrl();
                if("appassets.androidplatform.net".equals(u.getHost())&&"https".equals(u.getScheme()))return false;
                if(request.isForMainFrame()&&"https".equals(u.getScheme())) {
                    try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                }
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
                active=Optional.ofNullable(Uri.parse(url).getQueryParameter("id")).orElse("");
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                Uri u=request.getUrl();
                if(!"appassets.androidplatform.net".equals(u.getHost())||!"https".equals(u.getScheme()))return response("text/plain",new ByteArrayInputStream(new byte[0]));
                String path=u.getPath();if(path==null||path.equals("/"))path="/index.html";
                path=path.substring(1);
                try{
                    if(!safe(path))throw new IOException("Invalid path");
                    if(path.startsWith("lessons/")&&path.endsWith(".js")){
                        String id=path.substring(8,path.length()-3);File dir=installed(id);
                        if(dir!=null){String json=new JSONObject(read(new FileInputStream(new File(dir,"lesson.json")))).toString();return response("text/javascript",new ByteArrayInputStream(("window.LESSON = JSON.parse("+JSONObject.quote(json)+");").getBytes("UTF-8")));}
                    }
                    File dir=installed(active);
                    if(dir!=null){File f=new File(dir,path);if(f.isFile())return response(mime(path),new FileInputStream(f));}
                    String coverVersion="";JSONArray coverCourses=catalog.getJSONArray("courses");for(int i=0;i<coverCourses.length();i++){JSONObject c=coverCourses.getJSONObject(i);if(path.equals(c.getString("cover"))){coverVersion=c.getString("version");break;}}
                    File cover=new File(getFilesDir(),"covers/"+coverVersion+"/"+path);
                    if(cover.isFile())return response(mime(path),new FileInputStream(cover));
                    return response(mime(path),getAssets().open("site/"+path));
                }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            }
        });
        // Keep controls clear of Android 16 system bars and gesture navigation.
        web.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}return insets;});
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(web.canGoBack())web.goBack();else finish();});
        setContentView(web);web.loadUrl(ORIGIN+"index.html");sync(false);
    }
    private static String mime(String p){if(p.endsWith(".html"))return "text/html";if(p.endsWith(".js"))return "text/javascript";if(p.endsWith(".css"))return "text/css";if(p.endsWith(".webp"))return "image/webp";if(p.endsWith(".wav"))return "audio/wav";if(p.endsWith(".mp3"))return "audio/mpeg";return "application/octet-stream";}
    private static WebResourceResponse response(String mime,InputStream data){return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),data);}
    private static boolean safe(String s){return !s.isEmpty()&&!s.startsWith("/")&&!s.contains("..")&&!s.contains("\\")&&!s.contains(":");}
    private static String read(InputStream input)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}}
    private JSONObject course(JSONObject list,String id)throws JSONException{JSONArray a=list.getJSONArray("courses");for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);if(c.getString("id").equals(id))return c;}return null;}
    private File installed(String id){if(!id.matches("[A-Za-z0-9_-]{11}"))return null;String version=getPreferences(0).getString("installed."+id,"");if(version.isEmpty())return null;File dir=new File(getFilesDir(),"courses/"+id+"/"+version);return new File(dir,"lesson.json").isFile()?dir:null;}
    private static void validate(JSONObject list)throws JSONException{if(list.getInt("schemaVersion")!=1)throw new JSONException("Unsupported catalog");JSONArray a=list.getJSONArray("courses");if(a.length()>10000)throw new JSONException("Catalog too large");Set<String> ids=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);String id=c.getString("id");if(!id.matches("[A-Za-z0-9_-]{11}")||!ids.add(id)||!c.getString("version").matches("[a-f0-9]{16}")||!c.getString("sha256").matches("[a-f0-9]{64}")||!safe(c.getString("bundle"))||!safe(c.getString("cover")))throw new JSONException("Invalid course");c.getString("title");}}
    private byte[] fetch(String path,int limit)throws Exception{
        if(!safe(path)||!BuildConfig.CONTENT_BASE_URL.startsWith("https://"))throw new IOException("Cloud endpoint unavailable");
        HttpURLConnection conn=(HttpURLConnection)new URL(BuildConfig.CONTENT_BASE_URL+path).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(30000);conn.setInstanceFollowRedirects(false);
        try {if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Download too large");out.write(b,0,n);}return out.toByteArray();}}finally{conn.disconnect();}
    }
    private static void save(File file,byte[] bytes)throws IOException{file.getParentFile().mkdirs();File temp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    private void notifyUser(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_SHORT).show());}
    private void changed(){runOnUiThread(()->web.evaluateJavascript("window.refreshCourses && window.refreshCourses()",null));}
    private synchronized void sync(boolean explicit){if(busy){if(explicit)notifyUser("正在同步课程");return;}busy=true;io.execute(()->{try{JSONObject next=new JSONObject(new String(fetch("catalog.json",2*1024*1024),"UTF-8"));validate(next);JSONArray a=next.getJSONArray("courses");for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);String p=c.getString("cover");File f=new File(getFilesDir(),"covers/"+c.getString("version")+"/"+p);if(!f.exists()){try{save(f,fetch(p,2*1024*1024));}catch(Exception ignored){}}}save(new File(getFilesDir(),"catalog.json"),next.toString().getBytes("UTF-8"));catalog=next;changed();if(explicit)notifyUser("课程已更新");}catch(Exception e){if(explicit)notifyUser("同步失败，仍可学习已下载课程");}finally{busy=false;}});}
    private void open(String id){if(!id.matches("[A-Za-z0-9_-]{11}"))return;
        try{JSONObject c=course(catalog,id),built=course(bundled,id);if(c==null)return;String version=c.getString("version");if(version.equals(getPreferences(0).getString("installed."+id,""))||(built!=null&&version.equals(built.getString("version")))){runOnUiThread(()->web.loadUrl(ORIGIN+"lesson.html?id="+id));return;}}catch(Exception ignored){}
        io.execute(()->{try{JSONObject c=course(catalog,id);if(c==null)return;String version=c.getString("version");JSONObject built=course(bundled,id);String installedVersion=getPreferences(0).getString("installed."+id,"");if(!version.equals(installedVersion)&&!(built!=null&&version.equals(built.getString("version")))){
        notifyUser("正在下载课程");byte[] bytes=fetch(c.getString("bundle"),25*1024*1024);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals(c.getString("sha256")))throw new IOException("Checksum mismatch");
        File parent=new File(getFilesDir(),"courses/"+id);parent.mkdirs();File stage=new File(parent,version+".part");delete(stage);stage.mkdirs();long total=0;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;byte[] buffer=new byte[16384];while((entry=zip.getNextEntry())!=null){if(++entries>1000||!safe(entry.getName()))throw new IOException("Invalid bundle");File f=new File(stage,entry.getName());if(entry.isDirectory()){f.mkdirs();continue;}f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>100*1024*1024)throw new IOException("Bundle too large");out.write(buffer,0,n);}}}}
        JSONObject lesson=new JSONObject(read(new FileInputStream(new File(stage,"lesson.json"))));JSONArray groups=lesson.getJSONArray("groups");for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);if(g.getInt("id")!=i||!safe(g.getString("audioFile"))||!new File(stage,g.getString("audioFile")).isFile())throw new IOException("Invalid lesson audio");}JSONArray drill=lesson.getJSONArray("drill");for(int i=0;i<drill.length();i++)if(!safe(drill.getJSONObject(i).getString("audioFile"))||!new File(stage,drill.getJSONObject(i).getString("audioFile")).isFile())throw new IOException("Invalid drill audio");
        File destination=new File(parent,version);delete(destination);if(!stage.renameTo(destination))throw new IOException("Install failed");getPreferences(0).edit().putString("installed."+id,version).apply();
        }
        runOnUiThread(()->web.loadUrl(ORIGIN+"lesson.html?id="+id));
        }catch(Exception e){if(installed(id)!=null){notifyUser("更新失败，打开已下载版本");runOnUiThread(()->web.loadUrl(ORIGIN+"lesson.html?id="+id));}else notifyUser("下载失败，请稍后重试");}});}
    private static void delete(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);}f.delete();}
    final class Bridge {
        @JavascriptInterface public String getCourses(){try{return catalog.getJSONArray("courses").toString();}catch(Exception e){return "[]";}}
        @JavascriptInterface public void syncCourses(){sync(true);}
        @JavascriptInterface public void openCourse(String id){open(id);}
    }
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("typeof stop === 'function' && stop()",null);web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){web.removeJavascriptInterface("PracticeApp");web.destroy();io.shutdownNow();super.onDestroy();}
}
