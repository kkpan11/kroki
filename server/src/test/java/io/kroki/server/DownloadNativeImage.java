package io.kroki.server;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DownloadNativeImage {

  public static Future<String> download(Vertx vertx, String downloadUrl, String commandName, String downloadFileName, String nativeImageName) {
    String binPathOptionName = "KROKI_" + commandName.toUpperCase() + "_BIN_PATH";
    String binPath = System.getenv(binPathOptionName);
    if (binPath != null && !binPath.isBlank()) {
      return Future.succeededFuture(binPath);
    }
    String buildDirectory = System.getProperty("buildDirectory");
    if (buildDirectory == null) {
      return Future.failedFuture(new RuntimeException("buildDirectory system property must be defined by Maven, aborting!"));
    }
    Path nativePath = Paths.get(buildDirectory, nativeImageName);
    File file = nativePath.toFile();
    if (file.exists() && file.canExecute()) {
      String forceDownloadOptionName = "KROKI_FORCE_DOWNLOAD_" + commandName.toUpperCase();
      String forceDownload = System.getenv(forceDownloadOptionName);
      if (forceDownload == null || forceDownload.equalsIgnoreCase("false")) {
        System.out.println(commandName + " native image found at " + nativePath.toAbsolutePath() + "; skipping download.");
        System.out.println("TIP: You can force download using " + forceDownloadOptionName + " environment variable.");
        return Future.succeededFuture(nativePath.toString());
      }
      System.out.println(commandName + " native image found at " + nativePath.toAbsolutePath() + "; downloading again since " + forceDownloadOptionName + " is set...");
    } else {
      System.out.println(commandName + " native image not found in " + buildDirectory + "; downloading...");
    }
    WebClient client = WebClient.create(vertx);
    HttpRequest<Buffer> bufferHttpRequest = client
      .getAbs(downloadUrl)
      .followRedirects(true);
    Future<HttpResponse<Buffer>> send = bufferHttpRequest.send();
    System.out.println("Downloading " + downloadUrl + "...");
    return send.transform(httpResponseAsyncResult -> {
      if (httpResponseAsyncResult.failed()) {
        // nothing to do...
        return Future.failedFuture(httpResponseAsyncResult.cause());
      } else {
        HttpResponse<Buffer> bufferHttpResponse = httpResponseAsyncResult.result();
        int statusCode = bufferHttpResponse.statusCode();
        if (statusCode < 200 || statusCode >= 400) {
          return Future.failedFuture(new IllegalAccessError("Request failed with status code " + statusCode + " and message " + bufferHttpResponse.statusMessage()));
        }
        try {
          Path downloadFilePath = Paths.get(buildDirectory, downloadFileName);
          try (FileOutputStream fos = new FileOutputStream(downloadFilePath.toFile())) {
            fos.write(bufferHttpResponse.body().getBytes());
          } catch (IOException e) {
            return Future.failedFuture(e);
          }
          if (downloadFileName.endsWith(".zip")) {
            unzipFile(downloadFilePath, Paths.get(buildDirectory));
          }
          File nativeFile = nativePath.toFile();
          boolean result = nativeFile.setExecutable(true);
          if (!result) {
            return Future.failedFuture(new IllegalAccessError("Unable to make " + nativeFile + " executable!"));
          }
          return Future.succeededFuture(nativePath.toAbsolutePath().toString());
        } catch (Exception e) {
          return Future.failedFuture(e);
        }
      }
    });
  }

  private static void unzipFile(Path zipFile, Path targetDirectory) throws IOException {
    byte[] buffer = new byte[1024];
    ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()));
    ZipEntry zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      File newFile = newFile(targetDirectory.toFile(), zipEntry);
      if (zipEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        // fix for Windows-created archives
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }

        // write file content
        FileOutputStream fos = new FileOutputStream(newFile);
        int len;
        while ((len = zis.read(buffer)) > 0) {
          fos.write(buffer, 0, len);
        }
        fos.close();
      }
      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
    zis.close();
  }

  private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
    File destFile = new File(destinationDir, zipEntry.getName());

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }
}
