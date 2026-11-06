package cn.g.pan;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class MainActivity extends Activity {
 private Store store;private LinearLayout root,body,tabs;private EditText input,code;private LinearLayout results;
 private TextView recognized,parseState;private Button parseButton;private int tab=0;private File folder;
 private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final AtomicInteger generation=new AtomicInteger();private final Resolver resolver=new Resolver(new Net());
 private Links.Share current;private File pendingExport;private String draft="",draftCode="";private final Map<String,long[]> speeds=new HashMap<>();
 private final Runnable ticker=new Runnable(){public void run(){if(tab==1)renderTasks();handler.postDelayed(this,1000);}};
 public void onCreate(Bundle b){
  super.onCreate(b);store=Store.get(this);
  if(b!=null){draft=b.getString("draft","");draftCode=b.getString("code","");tab=b.getInt("tab",0);}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
  createRoot();acceptIntent(getIntent());handler.post(ticker);
 }
 protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);acceptIntent(i);}
 private void acceptIntent(Intent i){
  if(i.hasExtra("tab"))tab=i.getIntExtra("tab",0);
  if(Intent.ACTION_SEND.equals(i.getAction())){draft=i.getStringExtra(Intent.EXTRA_TEXT);if(draft==null)draft="";tab=0;showTab();handler.post(()->parse(null));}else showTab();
 }
 private void createRoot(){
  root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(root);
  LinearLayout header=Ui.row(this);header.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,8));
  LinearLayout brand=Ui.column(this);TextView title=Ui.text(this,"G 盘",28,Ui.TEXT);Ui.bold(title);brand.addView(title);brand.addView(Ui.text(this,"链接下载 · 本地解压",12,Ui.MUTED));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  header.addView(Ui.button(this,"设置",false,v->settings()));root.addView(header);
  tabs=Ui.row(this);tabs.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,6));root.addView(tabs);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=Ui.column(this);body.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
 }
 private void remember(){if(input!=null){draft=input.getText().toString();draftCode=code.getText().toString();}}
 private void chooseTab(int next){remember();tab=next;showTab();}
 private void showTab(){
  tabs.removeAllViews();String[] labels={"链接下载","传输任务","本地文件"};
  for(int i=0;i<labels.length;i++){final int index=i;Button b=Ui.button(this,labels[i],i==tab,v->chooseTab(index));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);tabs.addView(b,p);}
  body.removeAllViews();input=null;code=null;
  if(tab==0)renderLink();else if(tab==1)renderTasks();else renderFiles();
 }
 private void renderLink(){
  LinearLayout card=Ui.card(this);TextView heading=Ui.text(this,"粘贴链接，开始下载",22,Ui.TEXT);Ui.bold(heading);card.addView(heading);Ui.gap(card,5);
  card.addView(Ui.text(this,"整段分享文字也能识别，提取码会自动填入。",13,Ui.MUTED));Ui.gap(card,12);
  input=new EditText(this);input.setHint("网盘分享链接 / HTTP 文件地址");input.setTextSize(15);input.setMinLines(3);input.setMaxLines(6);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setText(draft);card.addView(input,new LinearLayout.LayoutParams(-1,-2));
  recognized=Ui.text(this,"等待输入链接",13,Ui.BLUE);card.addView(recognized);Ui.gap(card,10);
  code=new EditText(this);code.setHint("提取码（有则填写）");code.setTextSize(14);code.setSingleLine(true);code.setText(draftCode);card.addView(code);
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){generation.incrementAndGet();if(parseButton!=null)parseButton.setEnabled(true);recognize();}public void afterTextChanged(Editable e){}});
  recognize();Ui.gap(card,8);
  LinearLayout row=Ui.row(this);row.addView(Ui.button(this,"粘贴并开始",false,v->paste()),new LinearLayout.LayoutParams(0,-2,1));parseButton=Ui.button(this,"识别并下载",true,v->parse(null));row.addView(parseButton,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);body.addView(card);
  results=Ui.column(this);body.addView(results);parseState=Ui.text(this,"",14,Ui.MUTED);results.addView(parseState);
  LinearLayout local=Ui.card(this);TextView localTitle=Ui.text(this,"免费本地解压",18,Ui.TEXT);Ui.bold(localTitle);local.addView(localTitle);local.addView(Ui.text(this,"ZIP / 7z / RAR4，支持输入压缩密码。选择手机里的压缩包，无需网盘会员。",13,Ui.MUTED));Ui.gap(local,8);local.addView(Ui.button(this,"选择压缩包",false,v->pickArchive()));body.addView(local);
  LinearLayout info=Ui.card(this);info.addView(Ui.text(this,"按原站实际权限下载",15,Ui.TEXT));Ui.gap(info,4);info.addView(Ui.text(this,"App 不设下载限速；实际速度取决于网络和网盘服务器。蓝奏 / 123 尝试自动解析，其他网盘可打开原站后接管下载。",13,Ui.MUTED));info.addView(Ui.button(this,"查看网盘支持情况",false,v->support()));body.addView(info);
 }
 private void recognize(){
  try{Links.Share share=Links.parse(input.getText().toString());recognized.setText("已识别："+share.provider);if(code!=null&&!share.password.isEmpty()&&code.getText().length()==0)code.setText(share.password);}
  catch(Exception e){recognized.setText("等待有效链接");}
 }
 private void paste(){
  android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(!cm.hasPrimaryClip()||cm.getPrimaryClip()==null){Ui.toast(this,"剪贴板没有文字");return;}
  input.setText(cm.getPrimaryClip().getItemAt(0).coerceToText(this));code.setText("");recognize();parse(null);
 }
 private void parse(Resolver.Entry selected){
  if(input==null)return;
  final Links.Share share;final String password;
  try{share=Links.parse(input.getText().toString());password=code.getText().toString().trim().isEmpty()?share.password:code.getText().toString().trim();current=share;remember();}
  catch(Exception e){Ui.toast(this,e.getMessage());return;}
  int run=generation.incrementAndGet();results.removeAllViews();parseState=Ui.text(this,"正在连接 "+share.provider+"…",14,Ui.MUTED);results.addView(parseState);parseButton.setEnabled(false);
  io.submit(()->{
   try{Resolver.Result result=resolver.resolve(share,password,selected);handler.post(()->{if(run!=generation.get()||isFinishing()||tab!=0||input==null)return;parseButton.setEnabled(true);showResult(share,result);});}
   catch(Exception e){String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();handler.post(()->{if(run!=generation.get()||isFinishing()||tab!=0||input==null)return;parseButton.setEnabled(true);parseState.setText("暂时无法自动下载："+message);results.addView(Ui.button(this,"打开原站继续下载",false,v->browser(share.url)));});}
  });
 }
 private void showResult(Links.Share share,Resolver.Result result){
  results.removeAllViews();LinearLayout card=Ui.card(this);card.addView(Ui.text(this,result.message,15,Ui.TEXT));
  if(result.browser){Ui.gap(card,8);card.addView(Ui.button(this,"打开原站并接管下载",true,v->browser(share.url)));}
  else if(result.files.size()==1&&result.files.get(0).kind.equals("direct")){try{startDownload(result.files.get(0),share.url);card.addView(Ui.text(this,"已加入传输任务",14,Ui.BLUE));card.addView(Ui.button(this,"查看下载进度",true,v->chooseTab(1)));}catch(Exception e){card.addView(Ui.text(this,e.getMessage(),14,Ui.MUTED));}}
  else {
   if(share.provider.equals("123 云盘"))card.addView(Ui.button(this,"返回分享根目录",false,v->parse(null)));
   for(Resolver.Entry entry:result.files){
    Button item=Ui.button(this,entry.name,false,v->{if(entry.kind.equals("direct")){try{startDownload(entry,share.url);Ui.toast(this,"已加入下载任务");}catch(Exception e){Ui.toast(this,e.getMessage());}}else parse(entry);});
    item.setAllCaps(false);item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);card.addView(item,new LinearLayout.LayoutParams(-1,-2));
   }
  }
  card.addView(Ui.button(this,"在原站查看",false,v->browser(share.url)));results.addView(card);
 }
 private void startDownload(Resolver.Entry e,String source)throws Exception{
  Links.checked(e.url);Store.Task t=new Store.Task();t.url=e.url;t.source=source;t.name=store.unique(Links.name(e.name),false);t.cookie=e.cookie;t.referrer=e.referrer;t.connections=getSharedPreferences("settings",0).getInt("connections",4);store.add(t);TransferService.start(this,t);
 }
 private void browser(String url){try{Links.checked(url);startActivity(new Intent(this,BrowserActivity.class).putExtra("url",url));}catch(Exception e){Ui.toast(this,e.getMessage());}}
 private void renderTasks(){
  if(tab!=1)return;body.removeAllViews();List<Store.Task> list=store.all();
  if(list.isEmpty()){empty("还没有传输任务","粘贴一个链接，或选择本地压缩包开始。");return;}
  for(Store.Task t:list){
   LinearLayout card=Ui.card(this);TextView name=Ui.text(this,(t.type.equals("extract")?"解压 · ":"下载 · ")+t.name,17,Ui.TEXT);Ui.bold(name);card.addView(name);
   String status=t.status.equals("done")?"已完成":t.status.equals("running")?"进行中":t.status.equals("queued")?"排队中":t.status.equals("paused")?"已暂停":"需要处理";
   card.addView(Ui.text(this,status+" · "+t.detail,12,Ui.MUTED));
   if(t.status.equals("running")){
    long now=SystemClock.elapsedRealtime();long[] previous=speeds.get(t.id);String speed="";
    if(previous!=null&&now>previous[1]&&t.done>=previous[0])speed=" · "+Links.size((t.done-previous[0])*1000/(now-previous[1]))+"/s";
    speeds.put(t.id,new long[]{t.done,now});
    card.addView(Ui.text(this,Links.size(t.done)+(t.total>=0?" / "+Links.size(t.total):"")+speed,13,Ui.BLUE));
   }else if(t.done>0)card.addView(Ui.text(this,Links.size(t.done)+(t.total>0?" / "+Links.size(t.total):""),13,Ui.BLUE));
   ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setIndeterminate(t.status.equals("running")&&t.total<0);progress.setProgress(t.status.equals("done")?100:t.total>0?(int)Math.min(100,t.done*100/t.total):0);card.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,6)));Ui.gap(card,8);
   LinearLayout row=Ui.column(this);
   if(t.status.equals("pausing"))row.addView(Ui.text(this,"正在停止传输…",13,Ui.MUTED));
   else if(t.status.equals("running")||t.status.equals("queued"))row.addView(Ui.button(this,"暂停",false,v->{TransferService.pause(this,t.id);renderTasks();}));
   else if(t.status.equals("done"))row.addView(Ui.button(this,t.type.equals("extract")?"查看文件":"文件操作",true,v->{File f=store.output(t);if(f.isDirectory()){folder=f;chooseTab(2);}else fileMenu(f);}));
   else row.addView(Ui.button(this,"继续 / 重试",true,v->{if(t.type.equals("extract"))password(t,null);else{try{TransferService.start(this,t);}catch(Exception e){Ui.toast(this,e.getMessage());}}}));
   if(!t.source.isEmpty())row.addView(Ui.button(this,"重新获取链接",false,v->{draft=t.source;draftCode="";chooseTab(0);}));
   if(!t.status.equals("running")&&!t.status.equals("queued")&&!t.status.equals("pausing"))row.addView(Ui.button(this,"移除记录",false,v->new AlertDialog.Builder(this).setTitle("移除此任务记录？").setMessage("保留已完成文件；同时清理此任务未完成的分段。").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->io.submit(()->{try{if(!t.type.equals("extract"))DownloadEngine.clean(new File(store.downloads,"."+Links.name(t.name)+".transfer"));store.remove(t.id);handler.post(this::renderTasks);}catch(Exception e){handler.post(()->Ui.toast(this,e.getMessage()));}})).show()));
   card.addView(row);body.addView(card);
  }
 }
 private void empty(String title,String detail){LinearLayout c=Ui.card(this);c.addView(Ui.text(this,title,20,Ui.TEXT));Ui.gap(c,8);c.addView(Ui.text(this,detail,14,Ui.MUTED));body.addView(c);}
 private void renderFiles(){
  body.removeAllViews();LinearLayout intro=Ui.card(this);intro.addView(Ui.text(this,"文件与解压",20,Ui.TEXT));intro.addView(Ui.text(this,"文件先保存在 App 内。需要时可保存到系统下载目录；卸载 App 会移除尚未导出的文件。",13,Ui.MUTED));
  intro.addView(Ui.button(this,"选择手机里的压缩包",true,v->pickArchive()));body.addView(intro);
  if(folder==null){folderCard("已下载文件",store.downloads);folderCard("已解压文件",store.unpacked);return;}
  try{if(!store.inside(folder)){folder=null;renderFiles();return;}}catch(Exception e){folder=null;return;}
  LinearLayout path=Ui.card(this);path.addView(Ui.text(this,folder.getName(),17,Ui.TEXT));path.addView(Ui.button(this,"返回上一级",false,v->{File parent=folder.getParentFile();folder=parent.equals(store.root)?null:parent;renderFiles();}));body.addView(path);
  File[] files=folder.listFiles(f->!f.getName().startsWith("."));if(files==null||files.length==0){empty("这里还没有文件","完成下载或解压后，文件会显示在这里。");return;}
  Arrays.sort(files,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));
  for(File file:files){LinearLayout card=Ui.card(this);TextView name=Ui.text(this,(file.isDirectory()?"📁 ":"")+file.getName(),16,Ui.TEXT);card.addView(name);card.addView(Ui.text(this,file.isDirectory()?"文件夹":Links.size(file.length()),12,Ui.MUTED));card.setOnClickListener(v->{if(file.isDirectory()){folder=file;renderFiles();}else fileMenu(file);});body.addView(card);}
 }
 private void folderCard(String title,File file){LinearLayout c=Ui.card(this);c.addView(Ui.text(this,"📁 "+title,18,Ui.TEXT));c.addView(Ui.text(this,"点击查看",13,Ui.BLUE));c.setOnClickListener(v->{folder=file;renderFiles();});body.addView(c);}
 private void pickArchive(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,10);}catch(Exception e){Ui.toast(this,"无法打开系统文件选择器");}}
 private void password(Store.Task task,File file){
  EditText p=new EditText(this);p.setHint("压缩密码（无密码则留空）");p.setSingleLine();p.setInputType(129);
  LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);box.addView(Ui.text(this,"仅在手机本地解压，不会上传文件。",13,Ui.MUTED));box.addView(p);
  new AlertDialog.Builder(this).setTitle("免费解压").setView(box).setNegativeButton("取消",null).setPositiveButton("开始解压",(d,w)->{
   try{Store.Task t=task;if(t==null){t=new Store.Task();t.type="extract";t.input=file.getCanonicalPath();t.name=store.unique(file.getName().replaceAll("(?i)\\.(zip|rar|7z)$",""),true);store.add(t);}t.password=p.getText().toString().toCharArray();TransferService.start(this,t);chooseTab(1);}catch(Exception e){Ui.toast(this,e.getMessage());}
  }).show();
 }
 private void fileMenu(File file){
  ArrayList<String> labels=new ArrayList<>(Arrays.asList("打开文件","保存到系统下载目录","分享文件"));if(Links.archive(file.getName()))labels.add("免费解压");
  new AlertDialog.Builder(this).setTitle(file.getName()).setItems(labels.toArray(new String[0]),(d,index)->{
   try{
    if(index==1){export(file);return;}if(index==3){password(null,file);return;}
    Uri uri=FilesProvider.uri(this,file);String type=getContentResolver().getType(uri);
    Intent i=index==0?new Intent(Intent.ACTION_VIEW).setDataAndType(uri,type):new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri(file.getName(),uri));startActivity(Intent.createChooser(i,index==0?"打开文件":"分享文件"));
   }catch(Exception e){Ui.toast(this,"无法打开："+e.getMessage());}
  }).show();
 }
 private void export(File file){
  if(Build.VERSION.SDK_INT<29){pendingExport=file;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,file.getName()),11);return;}
  Ui.toast(this,"正在保存到 Download/G盘…");
  io.submit(()->{
   Uri uri=null;try{
    ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,file.getName());values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/G盘");values.put(MediaStore.MediaColumns.IS_PENDING,1);
    uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建下载目录文件");copyTo(file,uri);
    values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
    handler.post(()->Ui.toast(this,"已保存到系统 Download/G盘"));
   }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);handler.post(()->Ui.toast(this,"保存失败："+e.getMessage()));}
  });
 }
 private void copyTo(File file,Uri uri)throws Exception{try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法写入");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
  if(request==10){
   try{
    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
    String name="压缩包";try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
    Store.Task t=new Store.Task();t.type="extract";t.uri=uri.toString();t.name=store.unique(name.replaceAll("(?i)\\.(zip|rar|7z)$",""),true);store.add(t);t.status="paused";password(t,null);
   }catch(Exception e){Ui.toast(this,e.getMessage());}
  }else if(request==11&&pendingExport!=null){File file=pendingExport;pendingExport=null;io.submit(()->{try{copyTo(file,uri);handler.post(()->Ui.toast(this,"保存完成"));}catch(Exception e){handler.post(()->Ui.toast(this,"保存失败："+e.getMessage()));}});}
 }
 private void settings(){
  String[] options={"并行连接：1（兼容优先）","并行连接：2","并行连接：4（默认）","清除原站登录状态","关于 G 盘"};
  new AlertDialog.Builder(this).setTitle("设置").setItems(options,(d,index)->{
   if(index<3){int n=index==0?1:index==1?2:4;getSharedPreferences("settings",0).edit().putInt("connections",n).apply();Ui.toast(this,"新下载任务将使用最多 "+n+" 个连接");}
   else if(index==3)new AlertDialog.Builder(this).setTitle("清除各网盘的网页登录状态？").setNegativeButton("取消",null).setPositiveButton("清除",(dialog,w)->{android.webkit.CookieManager.getInstance().removeAllCookies(done->Ui.toast(this,"已清除网页登录状态"));android.webkit.WebStorage.getInstance().deleteAllData();}).show();
   else new AlertDialog.Builder(this).setTitle("G 盘 0.1").setMessage("网盘链接下载与免费本地解压工具。\n\n没有广告、收费入口或第三方解析服务器。网盘登录在原站网页完成。下载地址和任务记录保存在 App 本地。\n\n不承诺解除网盘服务器限速；需会员的下载权限仍由原站决定。部分网盘的 blob 下载、RAR5 和部分分卷压缩包暂不支持。").setPositiveButton("知道了",null).show();
  }).show();
 }
 private void support(){new AlertDialog.Builder(this).setTitle("网盘支持情况").setMessage("自动尝试：蓝奏云公开分享、123 云盘公开分享、HTTP 文件直链、静态网页下载链接、部分 Dropbox / Google Drive 分享。\n\n原站网页接管：其他网盘在登录、验证并点击网页下载后，接管 HTTP/HTTPS 文件下载。无法保证每家网盘都提供可接管的地址。\n\n可识别："+Links.providers()+"。\n\n识别出名称不代表已经解析成功；接口变化可能导致自动解析失效。").setPositiveButton("知道了",null).show();}
 protected void onSaveInstanceState(Bundle b){remember();b.putString("draft",draft);b.putString("code",draftCode);b.putInt("tab",tab);super.onSaveInstanceState(b);}
 protected void onDestroy(){generation.incrementAndGet();handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
