package cn.g.pan;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class TransferService extends Service {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Set<String> submitted=ConcurrentHashMap.newKeySet();
 private final ConcurrentHashMap<String,AtomicBoolean> stops=new ConcurrentHashMap<>();
 private final AtomicInteger pending=new AtomicInteger();
 private final Handler main=new Handler(Looper.getMainLooper());
 private Store store;private PowerManager.WakeLock wake;private int latestStart;private long lastNotify,lastSave;
 private static final String CHANNEL="transfers";
 public static void start(Context c,Store.Task t){Intent i=new Intent(c,TransferService.class).putExtra("id",t.id);c.startForegroundService(i);}
 public static void pause(Context c,String id){c.startService(new Intent(c,TransferService.class).setAction("pause").putExtra("id",id));}
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();store=Store.get(this);NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel(CHANNEL,"下载与解压",NotificationManager.IMPORTANCE_LOW));PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);wake=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Gpan:transfer");wake.setReferenceCounted(false);}
 public int onStartCommand(Intent intent,int flags,int startId){
  latestStart=startId;
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  String id=intent.getStringExtra("id");
  if("pause".equals(intent.getAction())){
   AtomicBoolean flag=stops.get(id);if(flag!=null)flag.set(true);
   Store.Task t=store.task(id);if(t!=null&&!t.status.equals("done")){t.status="pausing";t.detail="正在暂停，保留已下载分段";save();}
   if(pending.get()==0)stopSelf();return START_NOT_STICKY;
  }
  Store.Task task=store.task(id);
  if(task==null){if(pending.get()==0)stopSelf();return START_NOT_STICKY;}
  startForeground(1,notification(task,"准备任务"));
  if(task.cookie.isEmpty()&&task.type.equals("download")){String cookie=android.webkit.CookieManager.getInstance().getCookie(task.url);if(cookie!=null)task.cookie=cookie;}
  if(submitted.add(id)){
   AtomicBoolean flag=new AtomicBoolean();stops.put(id,flag);task.status="queued";task.detail="等待开始";save();pending.incrementAndGet();
   if(!wake.isHeld())wake.acquire(6*60*60*1000L);
   worker.submit(()->run(task,flag));
  }
  return START_NOT_STICKY;
 }
 private void run(Store.Task task,AtomicBoolean flag){
  File imported=null;
  try{
   if(flag.get())throw new DownloadEngine.Paused();task.status="running";task.detail="正在连接";save();
   if(task.type.equals("download")){
    String cookie=task.cookie;
    
    Map<String,String> h=Net.headers(cookie,task.ua,task.referrer);
    new DownloadEngine(new Net()).download(task.url,h,store.output(task),task.connections,(done,total,detail)->update(task,done,total,detail),flag::get);
   }else{
    File input;
    if(!task.uri.isEmpty()){
     File imports=new File(store.root,"Imports");imports.mkdirs();imported=new File(imports,task.id+".archive");input=imported;
     long max=Math.max(0,store.root.getUsableSpace()-128L*1024*1024);long done=0;
     try(InputStream in=getContentResolver().openInputStream(Uri.parse(task.uri));OutputStream out=new FileOutputStream(input)){
      if(in==null)throw new IOException("无法读取所选文件");byte[] b=new byte[128*1024];int n;
      while((n=in.read(b))!=-1){if(flag.get())throw new DownloadEngine.Paused();done+=n;if(done>max)throw new IOException("存储空间不足，无法导入压缩包");out.write(b,0,n);update(task,done,-1,"正在读取压缩包");}
     }
    }else{input=new File(task.input);if(!store.inside(input)||!input.isFile())throw new IOException("找不到本地压缩包");}
    new ArchiveEngine().extract(input,store.output(task),task.password,(entries,bytes,name)->update(task,bytes,-1,"已解压 "+entries+" 项 · "+name),flag::get);
   }
   task.status="done";task.detail=task.type.equals("extract")?"解压完成，点击查看文件":"下载完成，点击打开或保存到系统下载目录";
  }catch(DownloadEngine.Paused e){task.status="paused";task.detail=task.type.equals("extract")?"解压已停止，重试将重新解压":"已暂停，服务器支持时可断点续传";}
  catch(Throwable e){task.status="error";String reason=e.getMessage();task.detail=reason==null?e.getClass().getSimpleName():reason;if(task.type.equals("extract"))task.detail="解压失败："+task.detail+"；加密文件请检查密码，RAR5/部分分卷暂不支持";}
  finally{
   if(imported!=null)imported.delete();if(task.password!=null)Arrays.fill(task.password,'\0');task.password=null;
   save();submitted.remove(task.id);stops.remove(task.id);
   int remaining=pending.decrementAndGet();main.post(()->{if(remaining==0&&pending.get()==0){if(wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(latestStart);}});
  }
 }
 private void update(Store.Task t,long done,long total,String detail){
  t.done=done;t.total=total;t.detail=detail;long now=SystemClock.elapsedRealtime();
  if(now-lastSave>2000){lastSave=now;save();}
  if(now-lastNotify>700){lastNotify=now;((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification(t,detail));}
 }
 private void save(){try{store.save();}catch(Exception ignored){}}
 private Notification notification(Store.Task task,String detail){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("tab",1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent pause=PendingIntent.getService(this,task.id.hashCode(),new Intent(this,TransferService.class).setAction("pause").putExtra("id",task.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.icon).setContentTitle(task.name).setContentText(detail).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"暂停",pause).build());
  if(task.total>0)b.setProgress(100,(int)Math.min(100,task.done*100/task.total),false);else b.setProgress(0,0,true);return b.build();
 }
 public void onTimeout(int startId,int type){for(AtomicBoolean b:stops.values())b.set(true);for(Store.Task t:store.all())if(t.status.equals("running")||t.status.equals("queued")){t.status="paused";t.detail="系统要求停止后台传输，请打开 App 继续";}save();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 public void onDestroy(){for(AtomicBoolean b:stops.values())b.set(true);worker.shutdownNow();for(String id:submitted){Store.Task t=store.task(id);if(t!=null&&(t.status.equals("running")||t.status.equals("queued")||t.status.equals("pausing"))){t.status="paused";t.detail="传输服务已停止，点击继续";}}save();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
