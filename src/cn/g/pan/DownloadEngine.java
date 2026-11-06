package cn.g.pan;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Byte-exact transfer; partial files remain private until a verified merge. */
public final class DownloadEngine {
 public interface Progress {void update(long done,long total,String detail);}
 public interface Cancel {boolean stopped();}
 public static final class Paused extends IOException {public Paused(){super("已暂停，稍后可继续");}}
 private static final class RangeRejected extends IOException {RangeRejected(){super("服务器不支持可靠的分段下载");}}
 public static final class Probe {
  public long size=-1;public boolean ranges;public String validator="",validatorHeader="",name;
 }
 private final Net net;
 public DownloadEngine(Net n){net=n;}
 public Probe probe(String url,Map<String,String> h)throws Exception {
  Map<String,String> q=new HashMap<>(h);q.put("Range","bytes=0-0");
  try(Net.Reply r=net.open(url,q)){
   if(r.code!=200&&r.code!=206)throw new IOException("下载地址返回 HTTP "+r.code+"，可能已过期或需要重新登录");
   if(Net.html(r))throw new IOException("获取到的是网页，请在内置浏览器点击原站的下载按钮");
   String type=r.header("Content-Type").toLowerCase(Locale.ROOT);
   if(type.contains("application/json"))throw new IOException("服务器返回了接口提示，请重新解析或在原站检查下载权限");
   Probe p=new Probe();p.name=Links.fileName(r.url,r.header("Content-Disposition"));
   if(r.code==206){
    long[] range=parseRange(r.header("Content-Range"));
    if(range==null||range[0]!=0||range[1]!=0||range[2]<1)throw new RangeRejected();
    p.size=range[2];p.ranges=true;
   }else{try{p.size=Long.parseLong(r.header("Content-Length"));}catch(Exception ignored){}}
   String etag=r.header("ETag");if(!etag.isEmpty()&&!etag.startsWith("W/")){p.validator=etag;p.validatorHeader="ETag";}
   else if(!r.header("Last-Modified").isEmpty()){p.validator=r.header("Last-Modified");p.validatorHeader="Last-Modified";}
   return p;
  }
 }
 public static long[] parseRange(String s){
  Matcher m=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(s);
  if(!m.matches())return null;try{long a=Long.parseLong(m.group(1)),b=Long.parseLong(m.group(2)),c=Long.parseLong(m.group(3));return a<=b&&b<c?new long[]{a,b,c}:null;}catch(Exception e){return null;}
 }
 public File download(String url,Map<String,String> headers,File destination,int connections,Progress progress,Cancel cancel)throws Exception {
  Probe p=probe(url,headers);if(cancel.stopped())throw new Paused();
  File temp=new File(destination.getParentFile(),"."+destination.getName()+".transfer");
  if(!temp.exists()&&!temp.mkdirs())throw new IOException("无法创建下载目录");
  if(destination.exists())throw new IOException("同名文件已存在，请修改文件名");
  if(p.size>0&&destination.getParentFile().getUsableSpace()>0&&p.size>(destination.getParentFile().getUsableSpace()-16*1024*1024)/2)throw new IOException("存储空间不足，分段合并需要约两倍文件大小");
  if(p.validator.isEmpty())p.ranges=false;
  int count=p.ranges&&!p.validator.isEmpty()&&p.size>=4*1024*1024?Math.max(1,Math.min(4,connections)):1;
  try{return transfer(url,headers,destination,temp,p,count,progress,cancel);}
  catch(RangeRejected rejected){
   if(cancel.stopped())throw new Paused();
   clean(temp);temp.mkdirs();p.ranges=false;
   progress.update(0,p.size,"服务器拒绝分段，改用单连接重新下载");
   return transfer(url,headers,destination,temp,p,1,progress,cancel);
  }
 }
 private File transfer(String url,Map<String,String> headers,File destination,File temp,Probe p,int count,Progress progress,Cancel cancel)throws Exception{
  File meta=new File(temp,"state.properties");Properties old=new Properties();
  if(meta.exists())try(InputStream in=new FileInputStream(meta)){old.load(in);}
  boolean reusable=p.ranges&&!p.validator.isEmpty()&&url.equals(old.getProperty("url"))&&p.validator.equals(old.getProperty("validator"))&&Long.toString(p.size).equals(old.getProperty("size"))&&Integer.toString(count).equals(old.getProperty("count"));
  if(!reusable){clean(temp);if(!temp.mkdirs())throw new IOException("无法准备下载临时目录");}
  Properties state=new Properties();state.setProperty("url",url);state.setProperty("validator",p.validator);state.setProperty("size",Long.toString(p.size));state.setProperty("count",Integer.toString(count));
  try(OutputStream out=new FileOutputStream(meta)){state.store(out,"G pan private download state");}
  final AtomicLong done=new AtomicLong();final AtomicBoolean failed=new AtomicBoolean();
  final AtomicReference<Exception> failure=new AtomicReference<>();
  ExecutorService workers=Executors.newFixedThreadPool(count);
  List<Future<?>> jobs=new ArrayList<>();
  for(int i=0;i<count;i++){
   final int index=i;final long start=p.size<0?0:p.size*i/count;final long end=p.size<0?-1:p.size*(i+1)/count-1;
   File part=new File(temp,"part-"+index);
   if(part.exists()&&end>=0&&part.length()>end-start+1)throw new IOException("下载分段状态不正确，请重新新建任务");
   done.addAndGet(part.length());
   jobs.add(workers.submit(()->{
    try{segment(url,headers,new File(temp,"part-"+index),start,end,p,count,done,progress,()->cancel.stopped()||failed.get());}
    catch(Exception e){if(!(e instanceof Paused)||failure.get()==null)failure.compareAndSet(null,e);failed.set(true);}
   }));
  }
  workers.shutdown();
  try{for(Future<?> job:jobs)job.get();}catch(Exception e){failed.set(true);workers.shutdownNow();workers.awaitTermination(30,TimeUnit.SECONDS);throw e;}
  if(cancel.stopped())throw new Paused();
  if(failure.get()!=null)throw failure.get();
  progress.update(done.get(),p.size,"校验并合并文件");
  File merge=new File(temp,"merged");
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(merge))){
   byte[] buffer=new byte[128*1024];
   for(int i=0;i<count;i++){
    File part=new File(temp,"part-"+i);
    long expected=p.size<0?-1:p.size*(i+1)/count-p.size*i/count;
    if(expected>=0&&part.length()!=expected)throw new IOException("文件分段不完整，保留进度供重试");
    try(InputStream in=new BufferedInputStream(new FileInputStream(part))){int n;while((n=in.read(buffer))!=-1){if(cancel.stopped())throw new Paused();out.write(buffer,0,n);}}
   }
  }
  if(p.size>=0&&merge.length()!=p.size)throw new IOException("下载大小与服务器声明不符");
  if(destination.exists()||!merge.renameTo(destination))throw new IOException("无法保存完成文件，请检查空间与文件名");
  clean(temp);progress.update(destination.length(),destination.length(),"下载完成");return destination;
 }
 private void segment(String url,Map<String,String> headers,File part,long start,long end,Probe p,int count,AtomicLong done,Progress progress,Cancel cancel)throws Exception{
  long offset=part.length();long expected=end<0?-1:end-start+1;
  if(expected>=0&&offset==expected)return;
  if(cancel.stopped())throw new Paused();
  for(int attempt=0;attempt<3;attempt++){
   if(cancel.stopped())throw new Paused();
   long have=part.length();Map<String,String> h=new HashMap<>(headers);
   boolean useRange=p.ranges;
   if(useRange){h.put("Range","bytes="+(start+have)+"-"+end);if(!p.validator.isEmpty())h.put("If-Range",p.validator);}
   try(Net.Reply r=net.open(url,h)){
    if(useRange){
     long[] rr=parseRange(r.header("Content-Range"));
     if(r.code!=206||rr==null||rr[0]!=start+have||rr[1]!=end||rr[2]!=p.size)throw new RangeRejected();
     if(!p.validator.isEmpty()&&!p.validator.equals(r.header(p.validatorHeader)))throw new RangeRejected();
    }else{
     if(r.code!=200)throw new IOException("下载返回 HTTP "+r.code);
     if(have>0){done.addAndGet(-have);have=0;}
    }
    if(Net.html(r)||r.header("Content-Type").toLowerCase(Locale.ROOT).contains("application/json"))throw new IOException("下载地址返回网页或错误信息，请重新解析");
    try(InputStream in=new BufferedInputStream(r.stream());FileOutputStream out=new FileOutputStream(part,useRange&&have>0)){
     byte[] b=new byte[64*1024];int n;long wrote=have;
     while((n=in.read(b))!=-1){
      if(cancel.stopped())throw new Paused();
      if(expected>=0&&wrote+n>expected)throw new IOException("服务器返回的数据超出文件大小");
      out.write(b,0,n);wrote+=n;long total=done.addAndGet(n);
      progress.update(total,p.size,(count>1?count+" 连接并行":"单连接下载")+" · 可用带宽下载");
     }
     out.getFD().sync();
     if(expected>=0&&wrote!=expected)throw new EOFException("连接提前断开，保留已下载进度");
    }
    return;
   }catch(RangeRejected|Paused e){throw e;}catch(Exception e){if(attempt==2)throw e;if(cancel.stopped())throw new Paused();}
  }
 }
 public static void clean(File file)throws IOException{
  if(!file.exists())return;if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)clean(child);}
  if(!file.delete())throw new IOException("无法清理临时文件");
 }
}
