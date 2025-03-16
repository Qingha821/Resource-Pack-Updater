package cn.zbx1425.resourcepackupdater.drm;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import cn.zbx1425.resourcepackupdater.mappings.Text;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ServerLockRegistry {

    public static boolean lockAllSyncedPacks = true;

    private static String localServerLock;

    private static String remoteServerLock;
    private static String packAppliedServerLock;

    private static boolean serverLockPrefetched = false;

    public static void updateLocalServerLock(File rpFolder) {
        if (lockAllSyncedPacks) {
            localServerLock = null; // So that when no longer lockAllSyncedPacks, the pack will reload
            return;
        }
        try {
            JsonObject metaObj = ResourcePackUpdater.JSON_PARSER.parse(IOUtils.toString(
                    AssetEncryption.wrapInputStream(new FileInputStream(rpFolder.toPath().resolve("pack.mcmeta").toFile()))
                    , StandardCharsets.UTF_8)).getAsJsonObject();
            if (metaObj.has("zbx_rpu_server_lock")) {
                localServerLock = metaObj.get("zbx_rpu_server_lock").getAsString();
                if (!serverLockPrefetched) {
                    remoteServerLock = localServerLock;
                    packAppliedServerLock = remoteServerLock;
                    ResourcePackUpdater.LOGGER.info("从本地包中预取服务器信息");
                    serverLockPrefetched = true;
                }
            } else {
                localServerLock = null;
            }
        } catch (Exception ignored) {
            localServerLock = null;
        }
    }

    public static boolean shouldRefuseProvidingFile(String resourcePath) {
        if (Objects.equals(resourcePath, "pack.mcmeta") || Objects.equals(resourcePath, "pack.png")) return false;
        if (lockAllSyncedPacks) return true;
        if (localServerLock == null) return false;
        return !Objects.equals(localServerLock, remoteServerLock);
    }

    public static void onLoginInitiated() {
        remoteServerLock = null;
    }

    public static void onSetServerLock(String serverLock) {
        remoteServerLock = serverLock;
    }

    public static void onAfterSetServerLock() {
        if (lockAllSyncedPacks) {
            Minecraft.getInstance().getToasts().addToast(new SystemToast(SystemToast.SystemToastIds.PACK_LOAD_FAILURE,
                    Text.literal("服务器转悠资源包文件不完整而未被采用"), Text.literal("您可按 F3+T 重新下载，如有任何问题请联系群主或管理")
            ));
        }

        if (localServerLock == null) {
            ResourcePackUpdater.LOGGER.info("不需要资源同步");
        } else if (remoteServerLock == null) {
            ResourcePackUpdater.LOGGER.info("资源同步没有有效链接");
        } else if (!remoteServerLock.equals(localServerLock)) {
            ResourcePackUpdater.LOGGER.info("资源同步存在差异");
        } else if (lockAllSyncedPacks) {
            ResourcePackUpdater.LOGGER.info("资源同步因不完整而不可用。");
        } else {
            ResourcePackUpdater.LOGGER.info("资源同步成功并可使用");
        }
        if (localServerLock != null && !Objects.equals(packAppliedServerLock, remoteServerLock)) {
            packAppliedServerLock = remoteServerLock;
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().reloadResourcePacks());
        }
    }
}
