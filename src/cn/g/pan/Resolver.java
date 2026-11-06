package cn.g.pan;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.CRC32;
import java.text.SimpleDateFormat;
import java.security.SecureRandom;
import org.json.*;

/** Uses public share-page protocols; browser fallback never claims successful parsing. */
public final class Resolver {
 public static final class Entry {
  public String name,url,kind,cookie="",referrer="";public JSONObject data;
  public Entry(String n,String u,String k){name=n;url=u;kind=k;}
 }
 public static final class Result {
  public final List<Entry> files=new ArrayList<>();public boolean browser;public String message="";
 }
 private final Net net;
 public Resolver(Net n){net=n;}
 private static String match(String regex,String text){Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(text);return m.find()?m.group(1):"";}
 private static String unescape(String s){return s.replace("&amp;","&").replace("&#38;","&").replace("&quot;","\"").replace("&#39;","'").replace("\\/","/");}
 public Result resolve(Links.Share share,String password,Entry selected)throws Exception{
  if(selected!=null&&selected.kind.equals("direct")){Result r=new Result();r.files.add(selected);return r;}
  if(share.provider.equals("123 云盘"))return pan123(share,password,selected);
  if(share.provider.equals("蓝奏云"))return lanzou(share,password,selected);
  String url=share.url;
  if(share.provider.equals("Dropbox")){
   url=url.replaceAll("([?&])dl=[01]","$1dl=1");if(!url.matches(".*[?&]dl=1.*"))url+=(url.contains("?")?"&":"?")+"dl=1";
  }
  if(share.provider.equals("Google Drive")){
   String id=match("/file/d/([a-zA-Z0-9_-]+)",url);if(!id.isEmpty())url="https://drive.google.com/uc?export=download&id="+id;
  }
  if(!share.provider.equals("网页 / HTTP 直链")&&!share.provider.equals("Dropbox")&&!share.provider.equals("Google Drive")){
   return browser(share.provider+"需要在原站取得下载地址。请打开分享页登录或验证，然后点击下载。");
  }
  return generic(url);
 }
 public static Result browser(String message){Result r=new Result();r.browser=true;r.message=message;return r;}
 private Result generic(String url)throws Exception{
  Map<String,String> h=new HashMap<>();h.put("Range","bytes=0-0");
  try(Net.Reply response=net.open(url,h)){
   if(response.code<200||response.code>=300)return browser("原站返回 HTTP "+response.code+"，请打开分享页检查");
   String type=response.header("Content-Type").toLowerCase(Locale.ROOT);
   if(!Net.html(response)&&!type.contains("json")&&(!type.isEmpty()||!response.header("Content-Disposition").isEmpty())){
    Result r=new Result();r.files.add(new Entry(Links.fileName(response.url,response.header("Content-Disposition")),url,"direct"));r.message="已取得文件地址";return r;
   }
   if(response.code==206)return browser("此链接返回网页，请在原站点击下载");
   String page=response.text(2*1024*1024);Result r=new Result();
   Matcher links=Pattern.compile("<a\\b([^>]*?)href\\s*=\\s*['\"]([^'\"]+)['\"]([^>]*)>(.*?)</a>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(page);
   Set<String> seen=new HashSet<>();
   while(links.find()&&r.files.size()<100){
    String attrs=links.group(1)+" "+links.group(3),target=unescape(links.group(2));
    if(!attrs.matches("(?is).*\\bdownload(?:\\s|=|$).*")&&!target.matches("(?i).*\\.(zip|7z|rar|apk|pdf|mp4|mp3|flac|iso|exe|tar|gz)(\\?.*)?"))continue;
    try{target=new URL(new URL(response.url),target).toString();Links.checked(target);if(!seen.add(target))continue;
     String name=unescape(links.group(4).replaceAll("<[^>]+>","").trim());if(name.isEmpty())name=Links.fileName(target,"");
     Entry entry=new Entry(Links.name(name),target,"direct");entry.referrer=response.url;r.files.add(entry);
    }catch(Exception ignored){}
   }
   if(r.files.isEmpty())return browser("此页面没有公开文件直链，请在原站点击下载。");
   r.message="找到 "+r.files.size()+" 个文件链接";return r;
  }
 }
 private String text(String url,Map<String,String> headers)throws Exception{try(Net.Reply r=net.open(url,headers)){return r.text(2*1024*1024);}}
 private Result lanzou(Links.Share share,String password,Entry selected)throws Exception{
  String link=selected!=null?selected.url:share.url;URL base=new URL(link);
  String origin=base.getProtocol()+"://"+base.getAuthority();Map<String,String> headers=Net.headers("","",link);
  String page=text(link,headers);
  if(page.contains("取消分享")||page.contains("文件不存在"))throw new Exception("分享已失效或文件不存在");
  if(page.contains("acw_sc__v2")&&!page.contains("iframe"))return browser("原站需要浏览器验证，请打开分享页后下载");
  if(page.contains("id=\"infos\"")||page.contains("id='infos'")){
   Map<String,String> params=ajaxForm(page);params.put("pwd",password);
   Result result=new Result();
   for(int pg=1;pg<=10;pg++){
    params.put("pg",Integer.toString(pg));JSONObject response;
    try(Net.Reply r=net.open(origin+"/filemoreajax.php",headers,Net.form(params))){response=new JSONObject(r.text(2*1024*1024));}
    JSONArray files=response.optJSONArray("text");if(files==null){if(pg==1)throw new Exception("文件列表获取失败："+response.optString("info","请检查提取码或打开原站"));break;}
    if(files.length()==0)break;
    for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String id=f.optString("id");if(id.isEmpty())continue;String fileUrl=new URL(base,"/"+id).toString();result.files.add(new Entry(f.optString("name_all",f.optString("name","文件")),fileUrl,"link"));}
   }
   result.message="蓝奏分享文件列表（最多 10 页，更多文件可打开原站）";if(result.files.isEmpty())return browser("未取得文件列表，请打开原站检查提取码");return result;
  }
  String work=page,api="";
  boolean protectedPage=page.contains("pwdload")||page.contains("passwddiv");
  if(!protectedPage){
   String iframe=unescape(match("<iframe\\b[^>]*src\\s*=\\s*['\"]([^'\"]+)['\"]",page));
   if(iframe.isEmpty())return browser("蓝奏页面结构暂不兼容，请打开原站下载");
   String inner=new URL(base,iframe).toString();work=text(inner,headers);
  }
  api=unescape(match("(?:var\\s+domain[12]\\s*=\\s*|url\\s*:\\s*)['\"]([^'\"]*(?:ajaxfile|ajaxm)\\.php[^'\"]*)['\"]",work));
  Map<String,String> params=new LinkedHashMap<>();
  String sign=match("(?:var\\s+)?wp_sign\\s*=\\s*['\"]([^'\"]+)['\"]",work);
  String ajaxdata=match("(?:var\\s+)?ajaxdata\\s*=\\s*['\"]([^'\"]*)['\"]",work);
  if(!sign.isEmpty()){
   params.put("action","downprocess");params.put("sign",sign);params.put("signs",ajaxdata);params.put("websignkey",ajaxdata);params.put("websign","2");params.put("kd","1");params.put("ves","1");
  }else params=ajaxForm(work);
  if(protectedPage){if(password.isEmpty())throw new Exception("此分享需要提取码，请填写后重试");params.put("p",password);}
  if(api.isEmpty())api=match("['\"]([^'\"]*/ajax(?:file|m)\\.php\\?file=\\d+[^'\"]*)['\"]",work);
  if(api.isEmpty()||params.isEmpty())return browser("蓝奏页面结构发生变化，请打开原站下载");
  api=new URL(base,api).toString();JSONObject data;
  try(Net.Reply reply=net.open(api,headers,Net.form(params))){data=new JSONObject(reply.text(2*1024*1024));}
  if(data.optInt("zt")!=1)throw new Exception("原站未提供下载地址："+data.optString("inf",data.optString("info","请检查提取码或在浏览器验证")));
  String domain=data.optString("dom"),path=data.optString("url");
  if(path.isEmpty()||domain.isEmpty())return browser("原站未返回有效文件地址");
  String download=path.startsWith("http")?path:domain.replaceAll("/$","")+"/file/"+path;
  Links.checked(download);String name=match("<title>(.*?)\\s*-\\s*蓝奏",page);
  if(name.isEmpty())name=match("var\\s+filename\\s*=\\s*['\"]([^'\"]+)['\"]",page);
  if(name.isEmpty())name=data.optString("inf","蓝奏下载文件");
  Entry entry=new Entry(Links.name(unescape(name)),download,"direct");entry.cookie="down_ip=1";entry.referrer=domain;
  Result result=new Result();result.files.add(entry);result.message="已取得蓝奏云文件地址";return result;
 }
 private static Map<String,String> ajaxForm(String page){
  String data=match("\\bdata\\s*:\\s*\\{([^}]+)\\}",page);Map<String,String> out=new LinkedHashMap<>();
  Matcher m=Pattern.compile("['\"]?([a-zA-Z_][a-zA-Z0-9_]*)['\"]?\\s*:\\s*(?:'([^']*)'|\"([^\"]*)\"|([a-zA-Z0-9_]+))").matcher(data);
  while(m.find()){
   String value=m.group(2)!=null?m.group(2):m.group(3);
   if(value==null){value=m.group(4);if(!value.matches("\\d+"))value=match("(?:var\\s+)?"+Pattern.quote(value)+"\\s*=\\s*['\"]([^'\"]*)['\"]",page);}
   out.put(m.group(1),value);
  }
  return out;
 }
 private JSONObject api123(String path,Map<String,String> query,JSONObject body)throws Exception{
  String url="https://yun.123pan.com/b/api"+path;Map<String,String> all=new LinkedHashMap<>(query);
  long now=System.currentTimeMillis();SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmm",Locale.ROOT);date.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
  String digits=date.format(new Date(now));StringBuilder coded=new StringBuilder();
  // Public web protocol encodes the clock's decimal digits before CRC signing.
  char[] alphabet={'a','d','e','f','g','h','l','m','y','i'};
  for(char digit:digits.toCharArray())coded.append(alphabet[digit-'0']);
  String key=crc(coded.toString()),random=Integer.toString(new SecureRandom().nextInt(10000000));
  String seconds=Long.toString(now/1000);
  String signature=crc(seconds+"|"+random+"|/b/api"+path+"|web|3|"+key);
  all.put(key,seconds+"-"+random+"-"+signature);url+="?"+Net.form(all);
  Map<String,String> headers=Net.headers("",Net.UA,"https://yun.123pan.com/");headers.put("Origin","https://yun.123pan.com");headers.put("platform","web");headers.put("app-version","3");
  if(body!=null)headers.put("Content-Type","application/json; charset=UTF-8");
  JSONObject response;try(Net.Reply r=net.open(url,headers,body==null?null:body.toString())){response=new JSONObject(r.text(2*1024*1024));}
  if(response.optInt("code",-1)!=0)throw new Exception("123 云盘："+response.optString("message","原站限制访问，请使用内置浏览器"));
  return response.getJSONObject("data");
 }
 private static String crc(String s)throws Exception{CRC32 crc=new CRC32();crc.update(s.getBytes("UTF-8"));return Long.toString(crc.getValue());}
 private Result pan123(Links.Share share,String password,Entry selected)throws Exception{
  String key=match("/s/([^/?#]+)",share.url);if(key.isEmpty())return browser("此 123 云盘地址不是标准分享链接，请打开原站");
  if(selected!=null&&selected.kind.equals("123file")){
   JSONObject f=selected.data,body=new JSONObject();body.put("shareKey",key);body.put("SharePwd",password);body.put("etag",f.optString("Etag"));body.put("fileId",f.optLong("FileId"));body.put("s3keyFlag",f.optString("S3KeyFlag"));body.put("size",f.optLong("Size"));
   JSONObject data=api123("/share/download/info",Collections.<String,String>emptyMap(),body);String url=data.optString("DownloadURL");if(url.isEmpty())return browser("原站未授予下载权限，请登录后再试");
   // Keep official URL intact; follow only server-provided redirects.
   try(Net.Reply r=net.open(url,Net.headers("","","https://yun.123pan.com/"))){
    if(r.code==200&&r.header("Content-Type").contains("json")){
     JSONObject answer=new JSONObject(r.text(2*1024*1024));String redirect=answer.optJSONObject("data")==null?"":answer.getJSONObject("data").optString("redirect_url");if(!redirect.isEmpty())url=redirect;
    }else if(r.code>=200&&r.code<300&&!Net.html(r))url=r.url;
   }
   Links.checked(url);Result result=new Result();Entry e=new Entry(selected.name,url,"direct");e.referrer="https://yun.123pan.com/";result.files.add(e);result.message="已取得原站授权的下载地址";return result;
  }
  String parent=selected==null?"0":selected.url;
  Map<String,String> q=new LinkedHashMap<>();q.put("limit","100");q.put("next","0");q.put("orderBy","file_id");q.put("orderDirection","desc");q.put("parentFileId",parent);q.put("shareKey",key);q.put("SharePwd",password);
  Result result=new Result();
  for(int page=1;page<=10;page++){
   q.put("Page",Integer.toString(page));JSONObject data=api123("/share/get",q,null);JSONArray files=data.optJSONArray("InfoList");if(files==null||files.length()==0)break;
   for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);boolean folder=f.optInt("Type")==1;Entry e=new Entry((folder?"📁 ":"")+f.optString("FileName","文件"),f.optString("FileId"),folder?"123folder":"123file");e.data=f;result.files.add(e);}
   if(data.optString("Next").equals("-1")||files.length()<100)break;
  }
  result.message="123 分享文件列表（每目录最多 1000 项，更多文件可打开原站）";if(result.files.isEmpty())result.message="此文件夹为空或未取得读取权限";return result;
 }
}
