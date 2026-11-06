package cn.g.pan;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;

public final class FilesProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public static Uri uri(Context c,File file)throws IOException{
  Store s=Store.get(c);if(!s.inside(file))throw new IOException("文件不在 App 目录内");
  String relative=file.getCanonicalPath().substring(s.root.getCanonicalPath().length()+1);
  return new Uri.Builder().scheme("content").authority("cn.g.pan.files").appendPath(relative).build();
 }
 private File file(Uri uri)throws IOException{
  String relative=uri.getLastPathSegment();if(relative==null)throw new IOException("没有文件路径");
  Store s=Store.get(getContext());File f=new File(s.root,relative);
  if(!s.inside(f)||!f.isFile())throw new IOException("文件不存在");return f;
 }
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!mode.equals("r"))throw new FileNotFoundException("仅允许读取");
  try{return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException e){throw new FileNotFoundException(e.getMessage());}
 }
 public String getType(Uri uri){String name=uri.getLastPathSegment();int dot=name==null?-1:name.lastIndexOf('.');String type=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(java.util.Locale.ROOT));return type==null?"application/octet-stream":type;}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=columns[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():columns[i].equals(OpenableColumns.SIZE)?f.length():null;cursor.addRow(row);return cursor;}catch(Exception e){return null;}
 }
 public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
 public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}
