package io.agora.common;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class FileUtil {
  private static final String TAG = "UtFileUtil";

  private static final int BUFFER_SIZE = 4096;

  public static boolean createDir(String dir) {
    try {
      File d = new File(dir);
      if (d.exists()) {
        Log.i(TAG, "createDir:" + d.getName() + " has existed, empty it first");
        FilenameFilter filter = new FilenameFilter() {
          public boolean accept(File dir, String name) {
            return true;
          }
        };
        rmFile(dir, filter);
        return true;
      } else {
        if (d.mkdirs()) {
          Log.i(TAG, "createDir:" + d.getName() + " create success!");
        } else {
          Log.e(TAG, "createDir:" + d.getName() + " create failed!");
        }
      }
    } catch (SecurityException e) {
      Log.e(TAG, "createDir:" + dir + " create fail! e=" + e);
      return false;
    }
    return true;
  }

  public static boolean rmFile(String dir, FilenameFilter filter) {
    try {
      File d = new File(dir);
      if (!d.exists()) {
        Log.e(TAG, "rmFile:" + d.getName() + " not existed!");
        return true;
      }
      for (File f : d.listFiles(filter)) {
        if (f.isDirectory()) {
          Log.i(TAG, "rmFile: " + f.getName() + " is directory.");
          rmFile(dir + File.separator + f.getName(), filter);
        } else {
          if (f.delete()) {
            Log.i(TAG, "rmFile: " + f.getName() + " success!");
          } else {
            Log.e(TAG, "rmFile: " + f.getName() + " fail!");
          }
        }
      }
    } catch (SecurityException e) {
      Log.e(TAG, "rmFile:" + dir + " fail! e=" + e);
      return false;
    }
    return true;
  }

  public static boolean setFileAllRw(String dir, FilenameFilter filter) {
    try {
      File d = new File(dir);
      if (!d.exists()) {
        Log.e(TAG, "setFileAllRw:" + d.getName() + " not existed!");
        return false;
      }
      for (File f : d.listFiles(filter)) {
        if (!f.setReadable(true, false)) {
          Log.e(TAG, "setFileAllRw: set " + f.getName() + " readable fail!");
        }
        if (!f.setWritable(true, false)) {
          Log.e(TAG, "setFileAllRw: set " + f.getName() + " writable fail!");
        }
      }
    } catch (SecurityException e) {
      Log.e(TAG, "setFileAllRw:" + dir + " fail! e=" + e);
      return false;
    }
    return true;
  }

  public static String baseName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      throw new IllegalArgumentException("fileName is empty!");
    }
    int index = fileName.lastIndexOf(".");
    if (index < 0) {
      Log.i(TAG, "can't find '.' in " + fileName);
      return fileName;
    } else {
      return fileName.substring(0, index);
    }
  }

  public static String extName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      throw new IllegalArgumentException("fileName is empty!");
    }
    int index = fileName.lastIndexOf(".");
    if (index < 0) {
      Log.i(TAG, "can't find '.' in " + fileName);
      return "";
    } else if (index == fileName.length() - 1) {
      return "";
    } else {
      return fileName.substring(index + 1);
    }
  }

}
