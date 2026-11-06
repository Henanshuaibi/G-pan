import cn.g.pan.*;
import java.io.*;
import java.util.*;
import java.security.*;
import java.util.zip.*;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.sevenz.*;
public final class RuntimeTest {
 private static int count;
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;}
 private static byte[] read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);root.mkdirs();byte[] content="Android ART 解压测试\n".getBytes("UTF-8");
  try{
   File source=new File(root,"中文.txt");try(OutputStream out=new FileOutputStream(source)){out.write(content);}
   File zip=new File(root,"encrypted.zip");ZipParameters p=new ZipParameters();p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);
   try(net.lingala.zip4j.ZipFile z=new net.lingala.zip4j.ZipFile(zip,"secret".toCharArray())){z.addFile(source,p);}
   File unzipped=new File(root,"zip-result");new ArchiveEngine().extract(zip,unzipped,"secret".toCharArray(),(a,b,c)->{},()->false);
   check(Arrays.equals(read(new File(unzipped,"中文.txt")),content),"encrypted ZIP on ART");
   File seven=new File(root,"test.7z");
   try(SevenZOutputFile z=new SevenZOutputFile(seven)){SevenZArchiveEntry e=z.createArchiveEntry(source,"folder/test.txt");z.putArchiveEntry(e);z.write(content);z.closeArchiveEntry();}
   File sevenOut=new File(root,"7z-result");new ArchiveEngine().extract(seven,sevenOut,null,(a,b,c)->{},()->false);
   check(Arrays.equals(read(new File(sevenOut,"folder/test.txt")),content),"7z on ART");
   File rarOut=new File(root,"rar-result");new ArchiveEngine().extract(new File(args[3]),rarOut,null,(a,b,c)->{},()->false);
   check(new String(read(new File(rarOut,"rar-test.txt")),"UTF-8").equals("RAR4 local test\n"),"RAR4 on ART");
   check(Links.parse("https://pan.baidu.com/s/x?pwd=abcd").provider.equals("百度网盘"),"recognition on ART");
   if(args.length>1){
    File download=new DownloadEngine(new Net()).download(args[1],Collections.<String,String>emptyMap(),new File(root,"live.jar"),4,(a,b,c)->{},()->false);
    MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(read(download));StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
    check(hex.toString().equals(args[2]),"live HTTPS download digest on ART");System.out.println("Live HTTPS bytes="+download.length()+" SHA-256="+hex);
   }
   System.out.println("ART PASS "+count+" checks; APK classes loaded directly. Installed Activity UI not exercised.");
  }finally{DownloadEngine.clean(root);}
  System.exit(0);
 }
}
