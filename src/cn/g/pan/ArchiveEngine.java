package cn.g.pan;
import java.io.*;
import java.util.*;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.compress.archivers.sevenz.*;
import com.github.junrar.Archive;

/** All formats extract through checked paths and a bounded output stream. */
public final class ArchiveEngine {
 public interface Progress{void update(int entries,long bytes,String name);}
 private static final int MAX_ENTRIES=100000;
 private static final long MAX_BYTES=20L*1024*1024*1024;
 private int entries;private long bytes,limit;
 private DownloadEngine.Cancel cancel;private Progress progress;
 public File extract(File input,File destination,char[] password,Progress listener,DownloadEngine.Cancel stopped)throws Exception{
  if(destination.exists())throw new IOException("解压目录已存在，请更换名称");
  File parent=destination.getParentFile();parent.mkdirs();
  File staging=new File(parent,".extract-"+UUID.randomUUID());if(!staging.mkdirs())throw new IOException("无法创建解压目录");
  cancel=stopped;progress=listener;entries=0;bytes=0;
  long free=parent.getUsableSpace();limit=Math.min(MAX_BYTES,free>0?Math.max(0,free-64L*1024*1024):MAX_BYTES);
  try{
   byte[] magic=new byte[8];try(InputStream in=new FileInputStream(input)){in.read(magic);}
   if(magic[0]=='P'&&magic[1]=='K')zip(input,staging,password);
   else if(magic[0]=='7'&&magic[1]=='z'&&(magic[2]&255)==188)seven(input,staging,password);
   else if(magic[0]=='R'&&magic[1]=='a'&&magic[2]=='r'){
    if(magic[6]==1)throw new IOException("此版本暂不支持 RAR5，请使用 ZIP、7z 或 RAR4");
    rar(input,staging,password);
   }else throw new IOException("暂不支持这种压缩格式，仅支持 ZIP、7z 和 RAR4");
   if(cancel.stopped())throw new DownloadEngine.Paused();
   if(destination.exists()||!staging.renameTo(destination))throw new IOException("无法保存解压结果");
   return destination;
  }catch(Exception e){try{DownloadEngine.clean(staging);}catch(Exception ignored){}throw e;}
  finally{if(password!=null)Arrays.fill(password,'\0');}
 }
 public static File safe(File root,String name)throws IOException{
  String normalized=name.replace('\\','/');
  if(normalized.startsWith("/")||normalized.matches("^[a-zA-Z]:.*")||normalized.indexOf('\0')>=0)throw new IOException("压缩包包含不安全路径");
  String[] parts=normalized.split("/");
  for(String p:parts)if(p.equals(".."))throw new IOException("压缩包包含越界路径");
  File target=new File(root,normalized);
  if(!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("压缩包包含越界路径");
  return target;
 }
 private File entry(File root,String name,boolean folder,long size)throws Exception{
  if(cancel.stopped())throw new DownloadEngine.Paused();
  if(++entries>MAX_ENTRIES)throw new IOException("压缩包文件过多，已停止解压");
  if(size>limit-bytes)throw new IOException("解压后空间不足或超过 20 GB 保护上限");
  File out=safe(root,name);
  if(folder){if(!out.isDirectory()&&!out.mkdirs())throw new IOException("无法创建目录");}
  else{if(!out.getParentFile().isDirectory()&&!out.getParentFile().mkdirs())throw new IOException("无法创建目录");if(out.exists())throw new IOException("压缩包包含重复路径，已停止解压");}
  progress.update(entries,bytes,name);return out;
 }
 private OutputStream output(File file)throws Exception{
  return new FilterOutputStream(new BufferedOutputStream(new FileOutputStream(file))){
   public void write(int b)throws IOException{byte[] a={(byte)b};write(a,0,1);}
   public void write(byte[] b,int off,int len)throws IOException{
    if(cancel.stopped())throw new DownloadEngine.Paused();
    if(len>limit-bytes)throw new IOException("空间不足或解压数据超出保护上限");
    out.write(b,off,len);bytes+=len;progress.update(entries,bytes,file.getName());
   }
  };
 }
 private void copy(InputStream in,OutputStream out)throws Exception{byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
 private void zip(File input,File root,char[] password)throws Exception{
  try(ZipFile zip=new ZipFile(input,password)){
   List<FileHeader> list=zip.getFileHeaders();
   if(list.size()>MAX_ENTRIES)throw new IOException("压缩包文件过多");
   // Validate every path before creating output.
   for(FileHeader h:list)safe(root,h.getFileName());
   for(FileHeader h:list){
    File out=entry(root,h.getFileName(),h.isDirectory(),h.getUncompressedSize());
    if(!h.isDirectory())try(InputStream in=zip.getInputStream(h);OutputStream stream=output(out)){copy(in,stream);}
   }
  }
 }
 private void seven(File input,File root,char[] password)throws Exception{
  SevenZFileOptions options=SevenZFileOptions.builder().withMaxMemoryLimitInKb(64*1024).build();
  try(SevenZFile seven=new SevenZFile(input,password,options)){
   SevenZArchiveEntry h;while((h=seven.getNextEntry())!=null){
    File out=entry(root,h.getName(),h.isDirectory(),h.getSize());
    if(!h.isDirectory())try(OutputStream stream=output(out)){
     byte[] b=new byte[64*1024];int n;while((n=seven.read(b))!=-1)stream.write(b,0,n);
    }
   }
  }
 }
 private void rar(File input,File root,char[] password)throws Exception{
  try(Archive rar=new Archive(input,password==null?null:new String(password))){
   List<com.github.junrar.rarfile.FileHeader> list=rar.getFileHeaders();
   if(list.size()>MAX_ENTRIES)throw new IOException("压缩包文件过多");
   for(com.github.junrar.rarfile.FileHeader h:list){
    String name=h.isUnicode()?h.getFileNameW():h.getFileNameString();safe(root,name);
   }
   for(com.github.junrar.rarfile.FileHeader h:list){
    String name=h.isUnicode()?h.getFileNameW():h.getFileNameString();
    File out=entry(root,name,h.isDirectory(),h.getFullUnpackSize());
    if(!h.isDirectory())try(OutputStream stream=output(out)){rar.extractFile(h,stream);}
   }
  }
 }
}
