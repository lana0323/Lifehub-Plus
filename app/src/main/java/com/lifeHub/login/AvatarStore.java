package com.lifeHub.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

/** Own a bounded, resized copy so external photo permissions are never needed after selection. */
public final class AvatarStore {
    public static Uri importAvatar(Context context,String username,Uri source) throws IOException {
        if(username==null || username.isEmpty())throw new IOException("No local profile");
        File directory=new File(context.getFilesDir(),"avatars");
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Cannot create avatar directory");
        File input=File.createTempFile("source-",".tmp",directory);
        File output=new File(directory,UUID.randomUUID()+".jpg");
        Bitmap bitmap=null,scaled=null;boolean saved=false;
        try {
            try(InputStream in=context.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(input)) {
                if(in==null)throw new IOException("Photo unavailable");
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=in.read(buffer))!=-1){total+=count;if(total>20*1024*1024)throw new IOException("Photo exceeds 20 MB");out.write(buffer,0,count);}
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(input.getPath(),bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0)throw new IOException("Invalid image");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
            bitmap=BitmapFactory.decodeFile(input.getPath(),options);
            if(bitmap==null)throw new IOException("Image cannot be decoded");
            float ratio=Math.min(1f,512f/Math.max(bitmap.getWidth(),bitmap.getHeight()));
            scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*ratio)),Math.max(1,Math.round(bitmap.getHeight()*ratio)),true);
            try(FileOutputStream out=new FileOutputStream(output)) {
                if(!scaled.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Cannot encode avatar");
                out.getFD().sync();
            }
            Uri old=LoginManager.getAvatarUri(context,username), value=Uri.fromFile(output);
            LoginManager.saveAvatarUri(context,username,value);
            saved=true;
            // Only remove a previous file owned by this avatar directory.
            if(old!=null && "file".equals(old.getScheme()) && old.getPath()!=null) {
                File previous=new File(old.getPath());
                if(directory.getCanonicalFile().equals(previous.getCanonicalFile().getParentFile()) && !previous.equals(output))previous.delete();
            }
            return value;
        } finally {
            input.delete();if(!saved)output.delete();
            if(scaled!=null && scaled!=bitmap)scaled.recycle();if(bitmap!=null)bitmap.recycle();
        }
    }
    public static Uri restore(Context context,String username) throws IOException {
        Uri uri=LoginManager.getAvatarUri(context,username);
        if(uri==null)return null;
        File directory=new File(context.getFilesDir(),"avatars").getCanonicalFile();
        if("file".equals(uri.getScheme()) && uri.getPath()!=null) {
            File file=new File(uri.getPath()).getCanonicalFile();
            if(directory.equals(file.getParentFile()) && file.isFile())return uri;
        }
        return importAvatar(context,username,uri); // Migrate an accessible legacy external URI.
    }
}
