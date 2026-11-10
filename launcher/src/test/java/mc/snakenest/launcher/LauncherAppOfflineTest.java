package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.*;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.game.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LauncherAppOfflineTest {
    @TempDir Path root;
    private static void field(Object object, String name, Object value) throws Exception {
        Field field = LauncherApp.class.getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    @Test void restoresAnExpiredSessionsStoredKeyAndPlayerScopedUsernameWithoutRevalidation() throws Exception {
        AppDirs dirs = new AppDirs(root);
        ConfigStore config = new ConfigStore(dirs); var saved = config.load(); saved.setPlayerId(42L); config.save(saved);
        KeyStorage keys = mock(KeyStorage.class); Ed25519KeyPair key = Ed25519KeyPair.fromSeed(new byte[32]);
        when(keys.load()).thenReturn(Optional.of(key));
        LauncherAuthApiClient auth = mock(LauncherAuthApiClient.class); ModpackApiClient api = mock(ModpackApiClient.class);
        LauncherApp app = new LauncherApp(dirs,URI.create("https://example.test"),"client",config,mock(ThemeController.class),auth,
                api,mock(NewsApiClient.class),keys,mock(GameInstallService.class),mock(GameLaunchService.class),mock(PinProtection.class),null,mock(BugReportApiClient.class));
        new LocalPackCatalogStore(dirs.root().resolve("local-pack-catalog.json"),"https://example.test").cacheUsername(42,"CachedPlayer");
        Field executor = LauncherApp.class.getDeclaredField("backgroundExecutor"); executor.setAccessible(true);
        try {
            Method suspend = LauncherApp.class.getDeclaredMethod("suspendRejectedSession"); suspend.setAccessible(true); suspend.invoke(app);
            Method restore = LauncherApp.class.getDeclaredMethod("restoreOfflineIdentity"); restore.setAccessible(true); restore.invoke(app);
            Field session = LauncherApp.class.getDeclaredField("session"); session.setAccessible(true);
            assertEquals(key.publicKeyHex(),((PlayerSession)session.get(app)).key().publicKeyHex());
            assertEquals(42,((PlayerSession)session.get(app)).playerId());
            Field mode = LauncherApp.class.getDeclaredField("offlineMode"); mode.setAccessible(true); assertEquals(true,mode.get(app));
            verifyNoInteractions(auth,api); verify(keys,never()).delete();
        } finally { ((ExecutorService)executor.get(app)).shutdownNow(); }
    }

    @Test void offlineAndRemovedPacksLaunchWithoutSyncInstallerUsernameOrBlacklistNetworkCalls() throws Exception {
        for (boolean removed : new boolean[]{false,true}) {
            AppDirs dirs = new AppDirs(root.resolve(removed?"removed":"offline"));
            ConfigStore config = new ConfigStore(dirs);
            var saved = config.load(); saved.setPlayerId(42L); config.save(saved);
            LauncherAuthApiClient auth = mock(LauncherAuthApiClient.class);
            ModpackApiClient api = mock(ModpackApiClient.class);
            NewsApiClient news = mock(NewsApiClient.class);
            GameInstallService installer = mock(GameInstallService.class);
            GameLaunchService launcher = mock(GameLaunchService.class);
            KeyStorage keys = mock(KeyStorage.class);
            Ed25519KeyPair key = Ed25519KeyPair.fromSeed(new byte[32]);
            LauncherApp app = new LauncherApp(dirs,URI.create("https://example.test"),"client",config,mock(ThemeController.class),
                    auth,api,news,keys,installer,launcher,mock(PinProtection.class),null,mock(BugReportApiClient.class));
            field(app,"session",new PlayerSession(key,42L,"client"));
            field(app,"offlineMode",!removed); field(app,"removedPackSlugs",removed?Set.of("pack"):Set.of());
            LocalPackCatalogStore cache = new LocalPackCatalogStore(dirs.root().resolve("local-pack-catalog.json"),"https://example.test");
            cache.cacheUsername(42,"CachedPlayer");
            Process process = mock(Process.class); when(process.onExit()).thenReturn(new CompletableFuture<>());
            when(launcher.launch(any())).thenReturn(process);
            ModpackSummary pack = new ModpackSummary("pack","Local pack",null,null,false,"1",0);
            ModpackManifest manifest = new ModpackManifest("1",null,null,0,"1.20.1","vanilla",null,List.of(),null,null);
            Method launch = LauncherApp.class.getDeclaredMethod("doInstallAndLaunch",ModpackSummary.class,ModpackManifest.class); launch.setAccessible(true);
            Field executor = LauncherApp.class.getDeclaredField("backgroundExecutor"); executor.setAccessible(true);
            try {
                launch.invoke(app,pack,manifest);
                ArgumentCaptor<LaunchRequest> request = ArgumentCaptor.forClass(LaunchRequest.class);
                verify(launcher).launch(request.capture());
                assertTrue(request.getValue().localOnly()); assertEquals("CachedPlayer",request.getValue().username());
                assertEquals(key.seedHex(),request.getValue().sn3TokenHex());
                verifyNoInteractions(auth,api,news,installer);
            } finally { ((ExecutorService)executor.get(app)).shutdownNow(); }
        }
    }
    @Test void gameExitReplacesPreparationStatus() throws Exception {
        AppDirs dirs=new AppDirs(root);ConfigStore config=new ConfigStore(dirs);
        GameLaunchService launcher=mock(GameLaunchService.class);
        LauncherApp app=new LauncherApp(dirs,URI.create("https://example.test"),"client",config,mock(ThemeController.class),
                mock(LauncherAuthApiClient.class),mock(ModpackApiClient.class),mock(NewsApiClient.class),mock(KeyStorage.class),
                mock(GameInstallService.class),launcher,mock(PinProtection.class),null,mock(BugReportApiClient.class));
        field(app,"session",new PlayerSession(Ed25519KeyPair.fromSeed(new byte[32]),42L,"client"));field(app,"offlineMode",true);
        new LocalPackCatalogStore(dirs.root().resolve("local-pack-catalog.json"),"https://example.test").cacheUsername(42,"FixturePlayer");
        var page=mock(mc.snakenest.launcher.ui.modpack.ModpackDetailPage.class);when(page.slug()).thenReturn("pack");field(app,"currentDetailPage",page);
        Process process=mock(Process.class);CompletableFuture<Process> exit=new CompletableFuture<>();when(process.onExit()).thenReturn(exit);when(launcher.launch(any())).thenReturn(process);
        Method launch=LauncherApp.class.getDeclaredMethod("doInstallAndLaunch",ModpackSummary.class,ModpackManifest.class);launch.setAccessible(true);
        try {
            launch.invoke(app,new ModpackSummary("pack","Pack",null,null,false,"1",0),new ModpackManifest("1",null,null,0,"1.20.1","vanilla",null,List.of(),null,null));
            verify(page).setStatus("Minecraft en cours.");exit.complete(process);verify(page).setStatus("Jeu fermé.");
        }finally{var executor=LauncherApp.class.getDeclaredField("backgroundExecutor");executor.setAccessible(true);((ExecutorService)executor.get(app)).shutdownNow();}
    }

}
