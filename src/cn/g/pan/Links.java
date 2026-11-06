package cn.g.pan;
import java.net.*;
import java.util.*;
import java.util.regex.*;
/** Recognition does not imply permission to download. */
public final class Links {
 public static final class Share {
  public final String url,password,provider;
  Share(String u,String p){url=u;password=p;provider=provider(u);}
 }
 private static final String[][] PROVIDERS={
 {"百度网盘","pan.baidu.com","yun.baidu.com"},{"夸克网盘","pan.quark.cn"},{"阿里云盘","alipan.com","aliyundrive.com"},
 {"UC 网盘","drive.uc.cn","fast.uc.cn"},
 {"123 云盘","123pan.com","123pan.cn","123865.com","123684.com","123912.com","123952.com","123592.com","123446.com","123335.com","123332.com","123878.com","123pan.net"},
 {"蓝奏云","lanzou.com","lanzoui.com","lanzoux.com","lanzous.com","lanzouv.com","lanzoub.com","lanzoue.com","lanzouf.com","lanzoug.com","lanzouh.com","lanzouj.com","lanzouk.com","lanzoul.com","lanzoum.com","lanzouo.com","lanzoup.com","lanzouq.com","lanzout.com","lanzouw.com","lanzouy.com","lanzouz.com","lanzn.com","lanzv.com","lanzx.com"},
 {"天翼云盘","cloud.189.cn"},{"中国移动云盘","yun.139.com","caiyun.139.com"},{"迅雷云盘","pan.xunlei.com"},
 {"115 网盘","115.com","115cdn.com","anxia.com"},{"城通网盘","ctfile.com","545c.com","400gb.com"},
 {"微云","share.weiyun.com","weiyun.com"},{"坚果云","jianguoyun.com"},{"OneDrive","1drv.ms","onedrive.live.com","sharepoint.com"},
 {"Google Drive","drive.google.com"},{"Dropbox","dropbox.com","dropboxusercontent.com"},{"MEGA","mega.nz","mega.co.nz"},
 {"PikPak","mypikpak.com"},{"文叔叔","wenshushu.cn"},{"奶牛快传","cowtransfer.com"},{"飞猫云","feimaoyun.com","feimaopan.com"},
 {"华为云空间","cloud.huawei.com"},{"小米云盘","i.mi.com"},{"豆包云盘","drive.doubao.com"}};
 private static final Pattern URL=Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>\\\"“”‘’]+|(?:[a-z0-9-]+\\.)+(?:com|cn|net|ms|nz)/(?:s/|t/|[a-z0-9])[^\\s<>\\\"“”‘’]*");
 public static Share parse(String text)throws Exception{
  Matcher m=URL.matcher(text==null?"":text.trim());
  if(!m.find())throw new Exception("没有找到有效链接，请粘贴完整分享文字或 HTTP 下载地址");
  String u=m.group().replaceAll("[，。；、）)\\]】〉》]+$","");
  if(!u.toLowerCase(Locale.ROOT).startsWith("http"))u="https://"+u;
  checked(u);String p="";
  Matcher pm=Pattern.compile("(?i)(?:提取码|访问码|密码|口令|pwd|password|code)\\s*[:：=]?\\s*([a-z0-9]{2,16})").matcher(text);
  if(pm.find())p=pm.group(1);
  if(p.isEmpty()){Matcher q=Pattern.compile("(?i)[?&#](?:pwd|password|code)=([a-z0-9]{2,16})").matcher(u);if(q.find())p=q.group(1);}
  return new Share(u,p);
 }
 public static URL checked(String s)throws Exception{
  URL u=new URL(s);
  if(!("http".equalsIgnoreCase(u.getProtocol())||"https".equalsIgnoreCase(u.getProtocol()))||u.getHost().isEmpty()||u.getUserInfo()!=null||s.matches("(?s).*[\\r\\n\\x00].*"))throw new Exception("仅支持 HTTP/HTTPS 链接");
  return u;
 }
 public static boolean domain(String h,String d){return h.equals(d)||h.endsWith("."+d);}
 public static String provider(String u){
  try{String h=new URL(u).getHost().toLowerCase(Locale.ROOT);for(String[] p:PROVIDERS)for(int i=1;i<p.length;i++)if(domain(h,p[i]))return p[0];}catch(Exception ignored){}
  return "网页 / HTTP 直链";
 }
 public static String providers(){StringBuilder s=new StringBuilder();for(String[] p:PROVIDERS){if(s.length()>0)s.append("、");s.append(p[0]);}return s.toString();}
 public static String name(String s){
  if(s==null)s="";s=s.replaceAll("[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]","_").trim();while(s.startsWith("."))s=s.substring(1);
  if(s.isEmpty())s="下载文件";if(s.length()>160)s=s.substring(0,160);return s;
 }
 public static String fileName(String url,String disposition){
  if(disposition!=null){
   Matcher utf=Pattern.compile("(?i)filename\\*=\\s*UTF-8''([^;]+)").matcher(disposition);
   if(utf.find())try{return name(URLDecoder.decode(utf.group(1).trim(),"UTF-8"));}catch(Exception ignored){}
   Matcher plain=Pattern.compile("(?i)filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))").matcher(disposition);
   if(plain.find())return name(plain.group(1)==null?plain.group(2):plain.group(1));
  }
  try{String path=new URL(url).getPath();String last=path.substring(path.lastIndexOf('/')+1);return name(URLDecoder.decode(last.replace("+","%2B"),"UTF-8"));}catch(Exception e){return "下载文件";}
 }
 public static boolean archive(String n){return n.toLowerCase(Locale.ROOT).matches(".*\\.(zip|7z|rar)");}
 public static String size(long n){
  if(n<0)return "大小未知";if(n<1024)return n+" B";String[] a={"KB","MB","GB","TB"};double d=n;int i=-1;
  do{d/=1024;i++;}while(d>=1024&&i<a.length-1);return String.format(Locale.ROOT,"%.1f %s",d,a[i]);
 }
}
