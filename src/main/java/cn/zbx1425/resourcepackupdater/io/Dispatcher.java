package cn.zbx1425.resourcepackupdater.io;

import cn.zbx1425.resourcepackupdater.Config;
import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import cn.zbx1425.resourcepackupdater.gui.gl.GlHelper;
import cn.zbx1425.resourcepackupdater.gui.GlProgressScreen;
import cn.zbx1425.resourcepackupdater.io.network.DownloadDispatcher;
import cn.zbx1425.resourcepackupdater.io.network.DownloadTask;
import cn.zbx1425.resourcepackupdater.io.network.PackOutputStream;
import cn.zbx1425.resourcepackupdater.io.network.RemoteMetadata;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Dispatcher {

    private RemoteMetadata remoteMetadata;
    private LocalMetadata localMetadata;

    public boolean runSync(String baseDir, Config.SourceProperty source, ProgressReceiver cb) throws Exception {
        try {
            if (source.baseUrl.isEmpty()) {
                throw new IOException("没有下载源匹配，请联系技术支持！!");
            }

            cb.printLog("Resource Pack Updater v" + ResourcePackUpdater.MOD_VERSION + " (C) Zbx1425, www.zbx1425.cn");
            cb.printLog("Server: " + source.baseUrl);
            cb.printLog("Target: " + baseDir);
            cb.printLog("");

            localMetadata = new LocalMetadata(baseDir);
            remoteMetadata = new RemoteMetadata(source.baseUrl);

            byte[] remoteChecksum = null;

            if (source.hasDirHash) {
                cb.printLog("下载远程服务器验证数据");
                remoteChecksum = remoteMetadata.fetchDirChecksum(cb);
                cb.amendLastLog("完成");
                cb.printLog("远程服务器验证数据为 " + Hex.encodeHexString(remoteChecksum));
            } else {
                cb.printLog("服务器没有验证数据");
                cb.printLog("下载远程元数据 ...");
                remoteMetadata.fetch(cb);
                cb.amendLastLog("完成");
                cb.setProgress(0, 0);
            }
            // Now, either checksum or full metadata is fetched, with the encryption switch.

            cb.printLog("扫描本地文件 ...");
            localMetadata.scanDir(remoteMetadata.encrypt, cb);
            cb.amendLastLog("完成");
            byte[] localChecksum = localMetadata.getDirChecksum();
            cb.printLog("本地验证内容为 " + Hex.encodeHexString(localChecksum));

            if (localMetadata.files.size() < 1) {
                cb.printLog("本服务器专有资源正在被下载");
                cb.printLog("可能需要一些时间，坐下休息一下吧！");
            }
            if (remoteChecksum != null) {
                if (Arrays.equals(localChecksum, remoteChecksum)) {
                    cb.printLog("所有文件都已最新");
                    cb.setProgress(1, 1);
                    cb.printLog("");
                    cb.printLog("完成！感谢您的等待");
                    return true;
                } else {
                    // We haven't fetched the full metadata yet, do it now.
                    cb.printLog("下载远程元数据 ...");
                    remoteMetadata.fetch(cb);
                    cb.amendLastLog("完成");
                    cb.setProgress(0, 0);
                }
            }

            List<String> dirsToCreate = localMetadata.getDirsToCreate(remoteMetadata);
            List<String> dirsToDelete = localMetadata.getDirsToDelete(remoteMetadata);
            List<String> filesToCreate = localMetadata.getFilesToCreate(remoteMetadata);
            List<String> filesToUpdate = localMetadata.getFilesToUpdate(remoteMetadata);
            List<String> filesToDelete = localMetadata.getFilesToDelete(remoteMetadata);
            cb.printLog(String.format("Found %-3d new directories, %-3d to delete.",
                    dirsToCreate.size(), dirsToDelete.size()));
            cb.printLog(String.format("Found %-3d new files, %-3d to update, %-3d to delete.",
                    filesToCreate.size(), filesToUpdate.size(), filesToDelete.size()));

            cb.printLog("Creating & deleting directories and files ...");
            for (String dir : dirsToCreate) {
                Files.createDirectories(Paths.get(baseDir, dir));
            }
            for (String file : filesToDelete) {
                Files.deleteIfExists(Paths.get(baseDir, file));
            }
            for (String dir : dirsToDelete) {
                Path dirPath = Paths.get(baseDir, dir);
                if (Files.isDirectory(dirPath)) FileUtils.deleteDirectory(dirPath.toFile());
            }
            cb.amendLastLog("Done");

            remoteMetadata.beginDownloads(cb);
            cb.printLog("正在下载资源文件中 ...");
            DownloadDispatcher downloadDispatcher = new DownloadDispatcher(cb);
            for (String file : Stream.concat(filesToCreate.stream(), filesToUpdate.stream()).toList()) {
                DownloadTask task = new DownloadTask(downloadDispatcher,
                        remoteMetadata.baseUrl + "/dist/" + file, file, remoteMetadata.files.get(file).size);
                downloadDispatcher.dispatch(task, () -> new PackOutputStream(Paths.get(baseDir, file),
                        remoteMetadata.encrypt, localMetadata.hashCache, remoteMetadata.files.get(file).hash));
            }
            while (!downloadDispatcher.tasksFinished()) {
                downloadDispatcher.updateSummary();
                ((GlProgressScreen)cb).redrawScreen(true);
                Thread.sleep(1000 / 30);
            }
            remoteMetadata.downloadedBytes += downloadDispatcher.downloadedBytes;
            downloadDispatcher.close();
            localMetadata.saveHashCache();

            cb.setInfo("", "");
            cb.setProgress(1, 1);
            cb.printLog("");
            remoteMetadata.endDownloads(cb);
            cb.printLog("完成！感谢您的等待");
            return true;
        } catch (GlHelper.MinecraftStoppingException ex) {
            throw ex;
        } catch (Exception ex) {
            cb.setException(ex);
            return false;
        }
    }
}
