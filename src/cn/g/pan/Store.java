package cn.g.pan;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Store {
 private static Store instance;
 public static synchronized Store get(Context c){if(instance==null)instance=new Store(c.getApplicationContext());return instance;}
 public final Context context;public final File root,downloads,unpacked;private final File state;
 private final LinkedHashMap<String,Task> tasks=new LinkedHashMap<>();
 public static final class Task {
  public String id=UUID.randomUUID().toString(),type="download",url="",name="",source="",referrer="",cookie="",ua="",input="",uri="",exportUri="";
  public volatile String status="queued",detail="等待开始";
  public volatile long done=0,total=-1;public long created=System.currentTimeMillis();public int connections=4;
  public transient char[] password;
  JSONObject json()throws Exception{
   JSONObject o=new JSONObject();o.put("id",id);o.put("type",type);o.put("url",url);o.put("name",name);o.put("source",source);o.put("referrer",referrer);o.put("input",input);o.put("uri",uri);o.put("exportUri",exportUri);o.put("status",status);o.put("detail",detail);o.put("done",done);o.put("total",total);o.put("created",created);o.put("connections",connections);return o;
  }
  static Task from(JSONObject o){
   Task t=new Task();t.id=o.optString("id",t.id);t.type=o.optString("type","download");t.url=o.optString("url");t.name=o.optString("name");t.source=o.optString("source");t.referrer=o.optString("referrer");t.input=o.optString("input");t.uri=o.optString("uri");t.exportUri=o.optString("exportUri");t.status=o.optString("status");t.detail=o.optString("detail");t.done=o.optLong("done");t.total=o.optLong("total",-1);t.created=o.optLong("created");t.connections=o.optInt("connections",4);return t;
  }
 }
 private Store(Context c){
  context=c;File external=c.getExternalFilesDir(null);root=new File(external==null?c.getFilesDir():external,"G-pan");
  downloads=new File(root,"Downloads");unpacked=new File(root,"Unpacked");downloads.mkdirs();unpacked.mkdirs();state=new File(c.getFilesDir(),"tasks.json");
  if(state.exists()||new File(state.getPath()+".bak").exists())try{
   ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream in=new android.util.AtomicFile(state).openRead()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(data.size()+n>8*1024*1024)throw new IOException("任务记录过大");data.write(b,0,n);}}
   JSONArray array=new JSONArray(data.toString("UTF-8"));
   for(int i=0;i<array.length();i++){Task t=Task.from(array.getJSONObject(i));if(t.status.equals("running")||t.status.equals("queued")||t.status.equals("pausing")){t.status="paused";t.detail="上次任务被中断，点击继续";}tasks.put(t.id,t);}
  }catch(Exception ignored){}
 }
 public synchronized List<Task> all(){ArrayList<Task> list=new ArrayList<>(tasks.values());Collections.reverse(list);return list;}
 public synchronized Task task(String id){return tasks.get(id);}
 public synchronized void add(Task t)throws Exception{if(tasks.size()>=500)throw new IOException("任务记录已达 500 条，请先移除旧记录");tasks.put(t.id,t);save();}
 public synchronized void remove(String id)throws Exception{tasks.remove(id);save();}
 public synchronized void save()throws Exception{
  JSONArray list=new JSONArray();for(Task t:tasks.values())list.put(t.json());
  android.util.AtomicFile file=new android.util.AtomicFile(state);FileOutputStream out=null;
  try{out=file.startWrite();out.write(list.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
 }
 public File output(Task t){return new File(t.type.equals("extract")?unpacked:downloads,Links.name(t.name));}
 public synchronized String unique(String name,boolean folder){
  name=Links.name(name);String base=name;int index=2;
  while(new File(folder?unpacked:downloads,name).exists()||reserved(name,folder)) {
   int dot=folder?-1:base.lastIndexOf('.');name=dot>0?base.substring(0,dot)+" ("+index+")"+base.substring(dot):base+" ("+index+")";index++;
  }return name;
 }
 private boolean reserved(String name,boolean folder){for(Task t:tasks.values())if(t.name.equals(name)&&t.type.equals(folder?"extract":"download"))return true;return false;}
 public boolean inside(File f)throws IOException{return f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator);}
}
