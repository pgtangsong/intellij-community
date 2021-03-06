package org.jetbrains.io;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class LocalFileFinder {
  // if java.io.File.exists() takes more time than this timeout we assume that this is network drive and do not ping it any more
  private static final int FILE_EXISTS_MAX_TIMEOUT_MILLIS = 10;

  private static final Cache<Character, Boolean> myWindowsDrivesMap = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

  private LocalFileFinder() {
  }

  @Nullable
  public static VirtualFile findFile(@NotNull String path) {
    if (!SystemInfo.isWindows || windowsDriveExists(path)) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return null;
  }

  private static boolean windowsDriveExists(@NotNull String path) {
    if (path.length() > 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
      final char driveLetter = Character.toUpperCase(path.charAt(0));
      final Boolean driveExists = myWindowsDrivesMap.getIfPresent(driveLetter);
      if (driveExists != null) {
        return driveExists;
      }
      else {
        final long t0 = System.currentTimeMillis();
        boolean exists = new File(driveLetter + ":" + File.separator).exists();
        if (System.currentTimeMillis() - t0 > FILE_EXISTS_MAX_TIMEOUT_MILLIS) {
          exists = false; // may be a slow network drive
        }

        myWindowsDrivesMap.put(driveLetter, exists);
        return exists;
      }
    }

    return false;
  }
}