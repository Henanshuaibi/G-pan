package cn.g.pan;
import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public final class BrowserActivity extends Activity {
 private WebView web;private TextView address;private String original;private Store store;
 public void onCreate(Bundle b){
  super.onCreate(b);store=Store.get(this);original=getIntent().getStringExtra("url");
  try{Links.checked(original);}catch(Exception e){finish();return;}
  LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(root);
  LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
  top.addView(Ui.button(this,"返回",false,v->finish()));
  address=Ui.text(this,"原站网页",13,Ui.MUTED);address.setSingleLine(true);address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);top.addView(address,new LinearLayout.LayoutParams(0,-2,1));
  Switch desktop=new Switch(this);desktop.setText("电脑");top.addView(desktop);root.addView(top);
  TextView note=Ui.text(this,"在原站登录或输入提取码后，点击文件下载；App 会接管可用的下载链接。",12,Ui.MUTED);note.setPadding(Ui.dp(this,12),Ui.dp(this,5),Ui.dp(this,12),Ui.dp(this,8));root.addView(note);
  ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,3)));
  web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSafeBrowsingEnabled(true);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  String mobile=s.getUserAgentString();desktop.setOnCheckedChangeListener((button,checked)->{s.setUserAgentString(checked?"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36":mobile);web.reload();});
  web.setWebChromeClient(new WebChromeClient(){public void onProgressChanged(WebView view,int value){progress.setProgress(value);progress.setVisibility(value==100?View.GONE:View.VISIBLE);}});
  web.setWebViewClient(new WebViewClient(){
   public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    String url=request.getUrl().toString();try{Links.checked(url);return false;}catch(Exception e){if(request.isForMainFrame())Ui.toast(BrowserActivity.this,"暂不支持应用跳转或 blob 下载，请尝试电脑网页");return true;}
   }
   public void onPageFinished(WebView view,String url){address.setText(url);CookieManager.getInstance().flush();}
   public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())Ui.toast(BrowserActivity.this,"网页加载失败："+e.getDescription());}
  });
  web.setDownloadListener((url,ua,disposition,mime,length)->{
   try{
    Links.checked(url);Store.Task task=new Store.Task();task.url=url;task.name=store.unique(Links.fileName(url,disposition),false);task.source=original;task.referrer=web.getUrl();task.ua=ua;
    String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)task.cookie=cookie;
    task.connections=store.context.getSharedPreferences("settings",0).getInt("connections",4);
    store.add(task);TransferService.start(this,task);Ui.toast(this,"已加入下载："+task.name);startActivity(new Intent(this,MainActivity.class).putExtra("tab",1).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
   }catch(Exception e){Ui.toast(this,"无法接管此下载："+e.getMessage());}
  });
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);web.loadUrl(original);
 }
 public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 protected void onPause(){super.onPause();if(web!=null)web.onPause();CookieManager.getInstance().flush();}
 protected void onResume(){super.onResume();if(web!=null)web.onResume();}
 protected void onDestroy(){if(web!=null){((android.view.ViewGroup)web.getParent()).removeView(web);web.stopLoading();web.destroy();}super.onDestroy();}
}
