package cn.g.pan;
import java.io.*;
import java.net.*;
import java.util.*;
public final class Net {
 public static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
 private final CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
 public static final class Reply implements Closeable {
  public final HttpURLConnection c;public final int code;public final String url;
  Reply(HttpURLConnection x)throws IOException{c=x;code=c.getResponseCode();url=c.getURL().toString();}
  public String header(String h){String v=c.getHeaderField(h);return v==null?"":v;}
  public InputStream stream()throws IOException{if(code<200||code>=300)throw new IOException("服务器返回 HTTP "+code);return c.getInputStream();}
  public String text(int max)throws IOException{
   try(InputStream in=stream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("页面过大，请使用内置浏览器");out.write(b,0,n);}return out.toString("UTF-8");
   }
  }
  public void close(){c.disconnect();}
 }
 public Reply open(String url,Map<String,String> headers,String body)throws Exception{
  URL original=Links.checked(url),current=original;String method=body==null?"GET":"POST";
  for(int redirect=0;redirect<9;redirect++){
   HttpURLConnection c=(HttpURLConnection)current.openConnection();
   try{
    c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);
    c.setRequestMethod(method);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept-Encoding","identity");
    Map<String,List<String>> cookies=jar.get(current.toURI(),Collections.<String,List<String>>emptyMap());
    for(Map.Entry<String,List<String>> e:cookies.entrySet())if(!e.getValue().isEmpty())c.setRequestProperty(e.getKey(),String.join("; ",e.getValue()));
    if(headers!=null)for(Map.Entry<String,String> e:headers.entrySet()){
     if(e.getValue()==null||e.getValue().isEmpty())continue;String h=e.getKey().toLowerCase(Locale.ROOT);
     if((h.equals("cookie")||h.equals("authorization")||h.equals("origin"))&&!origin(original).equals(origin(current)))continue;
     c.setRequestProperty(e.getKey(),e.getValue());
    }
    if(method.equals("POST")){c.setDoOutput(true);if(headers==null||!headers.containsKey("Content-Type"))c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
    int status=c.getResponseCode();jar.put(current.toURI(),c.getHeaderFields());
    if(status==301||status==302||status==303||status==307||status==308){
     String location=c.getHeaderField("Location");if(location==null)throw new IOException("跳转缺少目标地址");
     URL next=Links.checked(new URL(current,location).toString());
     if(current.getProtocol().equals("https")&&next.getProtocol().equals("http"))throw new IOException("服务器尝试从 HTTPS 降级到 HTTP，请在原站检查链接");
     c.disconnect();current=next;if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body=null;}continue;
    }
    return new Reply(c);
   }catch(Exception e){c.disconnect();throw e;}
  }
  throw new IOException("网页跳转次数过多");
 }
 public Reply open(String url,Map<String,String> h)throws Exception{return open(url,h,null);}
 public static String origin(URL u){return u.getProtocol()+"://"+u.getHost().toLowerCase(Locale.ROOT)+":"+(u.getPort()<0?u.getDefaultPort():u.getPort());}
 public static String referer(String url){try{URL u=new URL(url);return u.getProtocol()+"://"+u.getAuthority()+"/";}catch(Exception e){return "";}}
 public static Map<String,String> headers(String cookie,String ua,String ref){Map<String,String> m=new HashMap<>();if(cookie!=null&&!cookie.isEmpty())m.put("Cookie",cookie);if(ua!=null&&!ua.isEmpty())m.put("User-Agent",ua);if(ref!=null&&!ref.isEmpty())m.put("Referer",referer(ref));return m;}
 public static String form(Map<String,String> m)throws Exception{StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:m.entrySet()){if(b.length()>0)b.append('&');b.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}return b.toString();}
 public static boolean html(Reply r){String t=r.header("Content-Type").toLowerCase(Locale.ROOT);return t.contains("text/html")||t.contains("application/xhtml");}
}
