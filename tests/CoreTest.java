import cn.g.pan.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.sevenz.*;

public final class CoreTest {
 private static int assertions;private static final byte[] DATA=new byte[6*1024*1024+37];
 private static File root;private static String base;private static final AtomicInteger ranged=new AtomicInteger(),plain=new AtomicInteger();
 private static void check(boolean b,String m){assertions++;if(!b)throw new AssertionError(m);}
 private static void fails(Checked action,String message)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,message);}
 private interface Checked{void run()throws Exception;}
 private static void same(File file,byte[] data)throws Exception{check(Arrays.equals(Files.readAllBytes(file.toPath()),data),"byte exact "+file.getName());}
 private static File out(String s){return new File(root,s);}
 public static void main(String[] args)throws Exception{
  root=Files.createTempDirectory("gpan-test-").toFile();new Random(77191).nextBytes(DATA);
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ExecutorService threads=Executors.newCachedThreadPool();server.setExecutor(threads);
  base="http://127.0.0.1:"+server.getAddress().getPort();
  server.createContext("/file",e->send(e,true,false,false));
  server.createContext("/single",e->send(e,false,false,false));
  server.createContext("/fallback",e->send(e,true,true,false));
  server.createContext("/unvalidated",e->send(e,true,false,true));
  server.createContext("/changed",e->sendChanged(e));
  server.createContext("/html",e->{byte[] data="<html>login</html>".getBytes("UTF-8");e.getResponseHeaders().add("Content-Type","text/html");e.sendResponseHeaders(200,data.length);try(OutputStream s=e.getResponseBody()){s.write(data);}});
  server.createContext("/page",e->{byte[] data=("<a download href='/file'>测试.zip</a><a href='https://ads.example/'>广告</a>").getBytes("UTF-8");e.getResponseHeaders().add("Content-Type","text/html");e.sendResponseHeaders(200,data.length);try(OutputStream s=e.getResponseBody()){s.write(data);}});
  server.createContext("/redirect",e->{e.getResponseHeaders().add("Location",base+"/file");e.sendResponseHeaders(302,-1);e.close();});
  server.createContext("/leak",e->{String cookie=e.getRequestHeaders().getFirst("Cookie"),auth=e.getRequestHeaders().getFirst("Authorization");byte[] data=((cookie==null?"":cookie)+"|"+(auth==null?"":auth)).getBytes("UTF-8");e.sendResponseHeaders(200,data.length);try(OutputStream s=e.getResponseBody()){s.write(data);}});
  server.createContext("/cross",e->{e.getResponseHeaders().add("Location","http://localhost:"+server.getAddress().getPort()+"/leak");e.sendResponseHeaders(302,-1);e.close();});
  server.start();
  try{links();downloads();archives();network();System.out.println("PASS "+assertions+" assertions (fixtures; no live provider account or installed UI claims)");}
  finally{server.stop(0);threads.shutdownNow();DownloadEngine.clean(root);}
 }
 private static void links()throws Exception{
  String[][] cases={{"https://pan.baidu.com/s/abc?pwd=1234","百度网盘"},{"https://pan.quark.cn/s/a","夸克网盘"},{"https://www.alipan.com/s/a","阿里云盘"},{"https://www.123865.com/s/abc-def","123 云盘"},{"https://a.lanzouv.com/iABC","蓝奏云"},{"https://cloud.189.cn/t/abc","天翼云盘"},{"https://yun.139.com/shareweb/#/w/i/a","中国移动云盘"},{"https://pan.xunlei.com/s/a","迅雷云盘"},{"https://115.com/s/a","115 网盘"},{"https://url12.ctfile.com/f/a","城通网盘"},{"https://1drv.ms/u/a","OneDrive"},{"https://drive.google.com/file/d/a/view","Google Drive"},{"https://www.dropbox.com/s/a","Dropbox"},{"https://mega.nz/file/a#b","MEGA"},{"https://pan.baidu.com.evil.example/s/a","网页 / HTTP 直链"}};
  for(String[] c:cases)check(Links.parse("链接："+c[0]+" 提取码：aB12").provider.equals(c[1]),"provider "+c[1]);
  check(Links.parse("https://example.com/a 提取码：aB12").password.equals("aB12"),"password text");
  check(Links.parse("https://example.com/a?pwd=1234").password.equals("1234"),"password query");
  check(Links.parse("https://example.com/a）。").url.equals("https://example.com/a"),"trailing punctuation");
  check(Links.parse("www.123pan.com/s/abc").url.startsWith("https://"),"missing scheme");
  check(Links.name("../../escape.zip").indexOf('/')<0,"filename path safe");
  check(Links.fileName("https://example.com/a","attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95.zip").equals("测试.zip"),"utf8 filename");
  fails(()->Links.parse("不是链接"),"invalid link");
  fails(()->Links.checked("file:///sdcard/test"),"file URL forbidden");
  fails(()->Links.checked("https://user:secret@example.com/a"),"userinfo forbidden");
  check(DownloadEngine.parseRange("bytes 5-9/10")[0]==5,"range parse");
  check(DownloadEngine.parseRange("bytes 10-9/10")==null,"bad range");
 }
 private static void downloads()throws Exception{
  DownloadEngine e=new DownloadEngine(new Net());Map<String,String> h=Collections.emptyMap();
  File parallel=e.download(base+"/file",h,out("parallel.bin"),4,(d,t,s)->{},()->false);same(parallel,DATA);check(ranged.get()>=5,"parallel ranges");
  File single=e.download(base+"/single",h,out("single.bin"),4,(d,t,s)->{},()->false);same(single,DATA);
  File fall=e.download(base+"/fallback",h,out("fallback.bin"),4,(d,t,s)->{},()->false);same(fall,DATA);check(plain.get()>0,"range fallback");
  AtomicBoolean pause=new AtomicBoolean();
  try{e.download(base+"/file",h,out("resume.bin"),4,(d,t,s)->{if(d>900000)pause.set(true);},pause::get);throw new AssertionError("pause expected");}catch(DownloadEngine.Paused expected){check(!out("resume.bin").exists(),"paused no completed file");}
  File partial=new File(root,".resume.bin.transfer");check(partial.isDirectory(),"partial retained");long[] before=Arrays.stream(partial.listFiles(f->f.getName().startsWith("part-"))).mapToLong(File::length).toArray();check(Arrays.stream(before).sum()>0,"partial bytes");
  File resumed=e.download(base+"/file",h,out("resume.bin"),4,(d,t,s)->{},()->false);same(resumed,DATA);check(!partial.exists(),"partial cleaned after success");
  File noValidator=e.download(base+"/unvalidated",h,out("unvalidated.bin"),4,(d,t,s)->{},()->false);same(noValidator,DATA);
  File changed=e.download(base+"/changed",h,out("changed.bin"),4,(d,t,s)->{},()->false);same(changed,DATA);
  File redirect=e.download(base+"/redirect",h,out("redirect.bin"),2,(d,t,s)->{},()->false);same(redirect,DATA);
  fails(()->e.download(base+"/html",h,out("login.bin"),4,(d,t,s)->{},()->false),"HTML not downloaded");
  check(!out("login.bin").exists(),"no false file for HTML");
  fails(()->e.download(base+"/file",h,parallel,4,(d,t,s)->{},()->false),"no overwrite");
  Resolver.Result page=new Resolver(new Net()).resolve(Links.parse(base+"/page"),"",null);check(page.files.size()==1,"static page download discovery");check(page.files.get(0).url.equals(base+"/file"),"static link resolved");
  Resolver.Result protectedPage=new Resolver(new Net()).resolve(Links.parse("https://pan.baidu.com/s/test"),"",null);check(protectedPage.browser&&protectedPage.files.isEmpty(),"browser capability honest");
 }
 private static void send(HttpExchange e,boolean range,boolean reject,boolean noValidator)throws IOException{
  long start=0,end=DATA.length-1;String request=e.getRequestHeaders().getFirst("Range");boolean probe="bytes=0-0".equals(request);
  boolean partial=range&&request!=null&&(!reject||probe);
  if(partial){String[] p=request.substring(6).split("-");start=Long.parseLong(p[0]);end=Long.parseLong(p[1]);ranged.incrementAndGet();}
  else plain.incrementAndGet();
  e.getResponseHeaders().add("Content-Type","application/octet-stream");e.getResponseHeaders().add("Content-Disposition","attachment; filename=fixture.zip");
  if(!noValidator)e.getResponseHeaders().add("ETag","\"test-v1\"");
  if(partial)e.getResponseHeaders().add("Content-Range","bytes "+start+"-"+end+"/"+DATA.length);
  try{e.sendResponseHeaders(partial?206:200,end-start+1);try(OutputStream out=e.getResponseBody()){int pos=(int)start;while(pos<=end){int n=Math.min(32768,(int)end-pos+1);out.write(DATA,pos,n);pos+=n;}}}catch(IOException ignored){e.close();}
 }
 private static void sendChanged(HttpExchange e)throws IOException{
  String r=e.getRequestHeaders().getFirst("Range");boolean probe="bytes=0-0".equals(r);
  e.getResponseHeaders().add("ETag",probe?"\"old\"":"\"new\"");e.getResponseHeaders().add("Content-Type","application/octet-stream");
  int start=0,end=DATA.length-1;if(r!=null){String[] p=r.substring(6).split("-");start=Integer.parseInt(p[0]);end=Integer.parseInt(p[1]);e.getResponseHeaders().add("Content-Range","bytes "+start+"-"+end+"/"+DATA.length);}
  try{e.sendResponseHeaders(r!=null?206:200,end-start+1);try(OutputStream out=e.getResponseBody()){out.write(DATA,start,end-start+1);}}catch(IOException ignored){e.close();}
 }
 private static void archives()throws Exception{
  byte[] text="免费本地解压\nhello\n".getBytes("UTF-8");File zip=out("normal.zip");
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(zip))){z.putNextEntry(new ZipEntry("目录/测试.txt"));z.write(text);z.closeEntry();}
  File dest=out("zip-output");new ArchiveEngine().extract(zip,dest,null,(a,b,c)->{},()->false);same(new File(dest,"目录/测试.txt"),text);
  File source=out("aes.txt");Files.write(source.toPath(),text);File encrypted=out("aes.zip");ZipParameters params=new ZipParameters();params.setEncryptFiles(true);params.setEncryptionMethod(EncryptionMethod.AES);
  try(net.lingala.zip4j.ZipFile z=new net.lingala.zip4j.ZipFile(encrypted,"secret".toCharArray())){z.addFile(source,params);}
  File aes=out("aes-output");char[] password="secret".toCharArray();new ArchiveEngine().extract(encrypted,aes,password,(a,b,c)->{},()->false);same(new File(aes,"aes.txt"),text);check(password[0]=='\0',"password cleared");
  File wrong=out("wrong-output");fails(()->new ArchiveEngine().extract(encrypted,wrong,"wrong".toCharArray(),(a,b,c)->{},()->false),"wrong password fails");check(!wrong.exists(),"wrong password no published result");
  File seven=out("normal.7z");try(SevenZOutputFile z=new SevenZOutputFile(seven)){SevenZArchiveEntry entry=z.createArchiveEntry(source,"folder/a.txt");z.putArchiveEntry(entry);z.write(text);z.closeArchiveEntry();}
  File sevenOut=out("7z-output");new ArchiveEngine().extract(seven,sevenOut,null,(a,b,c)->{},()->false);same(new File(sevenOut,"folder/a.txt"),text);
  File rar=out("stored.rar");writeRar(rar,"rar-file.txt",text);
  File rarOut=out("rar-output");new ArchiveEngine().extract(rar,rarOut,null,(a,b,c)->{},()->false);same(new File(rarOut,"rar-file.txt"),text);
  File rar5=out("new.rar");Files.write(rar5.toPath(),new byte[]{82,97,114,33,26,7,1,0});fails(()->new ArchiveEngine().extract(rar5,out("rar5-output"),null,(a,b,c)->{},()->false),"RAR5 explicit failure");
  File bad=out("bad.zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(bad))){z.putNextEntry(new ZipEntry("../escape.txt"));z.write(text);z.closeEntry();}
  fails(()->new ArchiveEngine().extract(bad,out("bad-output"),null,(a,b,c)->{},()->false),"zip slip rejected");check(!out("escape.txt").exists(),"zip slip no escape");
  fails(()->ArchiveEngine.safe(root,"C:\\escape"),"windows absolute rejected");
  fails(()->ArchiveEngine.safe(root,"x\\..\\escape"),"backslash traversal rejected");
  File cancelled=out("cancel-output");fails(()->new ArchiveEngine().extract(zip,cancelled,null,(a,b,c)->{},()->true),"cancel extract");check(!cancelled.exists(),"cancel no result");
  File duplicate=out("dupe.zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(duplicate))){z.putNextEntry(new ZipEntry("a/x"));z.write(text);z.closeEntry();z.putNextEntry(new ZipEntry("a\\x"));z.write(text);z.closeEntry();}
  fails(()->new ArchiveEngine().extract(duplicate,out("dupe-output"),null,(a,b,c)->{},()->false),"normalized duplicate rejected");
  fails(()->new ArchiveEngine().extract(zip,dest,null,(a,b,c)->{},()->false),"extract no overwrite");
 }
 private static void writeRar(File file,String name,byte[] data)throws Exception{
  try(OutputStream out=new FileOutputStream(file)){
   out.write(new byte[]{82,97,114,33,26,7,0});
   byte[] main=new byte[13];main[2]=0x73;little(main,5,13,2);headerCrc(main);out.write(main);
   byte[] n=name.getBytes("US-ASCII"),h=new byte[32+n.length];h[2]=0x74;little(h,3,0x8000,2);little(h,5,h.length,2);little(h,7,data.length,4);little(h,11,data.length,4);h[15]=2;CRC32 crc=new CRC32();crc.update(data);little(h,16,crc.getValue(),4);h[24]=20;h[25]=0x30;little(h,26,n.length,2);little(h,28,0x20,4);System.arraycopy(n,0,h,32,n.length);headerCrc(h);out.write(h);out.write(data);
   byte[] end=new byte[7];end[2]=0x7b;little(end,3,0x4000,2);little(end,5,7,2);headerCrc(end);out.write(end);
  }
 }
 private static void little(byte[] b,int p,long value,int bytes){for(int i=0;i<bytes;i++)b[p+i]=(byte)(value>>(i*8));}
 private static void headerCrc(byte[] b){CRC32 crc=new CRC32();crc.update(b,2,b.length-2);little(b,0,crc.getValue()&65535,2);}
 private static void network()throws Exception{
  Map<String,String> h=new HashMap<>();h.put("Cookie","session=private");h.put("Authorization","Bearer fixture-not-real");
  try(Net.Reply r=new Net().open(base+"/cross",h)){check(r.text(100).equals("|"),"cross origin credential stripping");}
  Map<String,String> headers=Net.headers("","","https://example.com/s/secret?token=private");check(headers.get("Referer").equals("https://example.com/"),"referer excludes share tokens");
 }
}
