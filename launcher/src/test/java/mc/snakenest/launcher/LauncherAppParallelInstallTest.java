package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.*;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.game.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static java.time.Duration.ofSeconds;

class LauncherAppParallelInstallTest {
    @TempDir Path root;
    @BeforeAll static void headless(){System.setProperty("java.awt.headless","true");}
    private final byte[] content="verified mod payload".getBytes(StandardCharsets.UTF_8);
    private final ModpackApiClient api=mock(ModpackApiClient.class);
    private final GameInstallService game=mock(GameInstallService.class);
    private byte[] frame()throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(("SN3B1\n"+Sha256.hex(content)+" "+content.length+"\n").getBytes(StandardCharsets.US_ASCII));out.write(content);return out.toByteArray();
    }
    private ModpackManifest manifest(){return new ModpackManifest("1",null,null,content.length,"1.20.4","vanilla",null,List.of(new ManifestFile("mods/demo.jar",Sha256.hex(content),content.length,null)),null,null);}
    private LauncherApp app()throws Exception {
        AppDirs dirs=new AppDirs(root);ConfigStore config=new ConfigStore(dirs);var settings=config.load();settings.setAssetStorageMode(AssetStorageMode.PER_MODPACK);config.save(settings);
        return new LauncherApp(dirs,URI.create("https://example.test"),"client",config,mock(ThemeController.class),mock(LauncherAuthApiClient.class),api,
                mock(NewsApiClient.class),mock(KeyStorage.class),game,mock(GameLaunchService.class),mock(PinProtection.class),null,mock(BugReportApiClient.class));
    }
    private void prepare(LauncherApp app)throws Exception {
        Method method=LauncherApp.class.getDeclaredMethod("prepareInstallation",ModpackSummary.class,ModpackManifest.class,boolean.class);method.setAccessible(true);
        try{method.invoke(app,new ModpackSummary("pack","Pack",null,null,false,"1",0),manifest(),false);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception exception)throw exception;throw failure;}
    }
    private void close(LauncherApp app)throws Exception {Field field=LauncherApp.class.getDeclaredField("backgroundExecutor");field.setAccessible(true);((ExecutorService)field.get(app)).shutdownNow();}
    @Test void actualControllerOverlapsTransfersAndAppliesAfterMinecraft() {
        assertTimeoutPreemptively(ofSeconds(5),()->{
            LauncherApp app=app();Path instance=new AppDirs(root).instance("pack");
            CountDownLatch gameStarted=new CountDownLatch(1),packStarted=new CountDownLatch(1);
            when(api.downloadBlobs("pack",List.of(Sha256.hex(content)),null)).thenAnswer(call->{assertTrue(gameStarted.await(2,TimeUnit.SECONDS));packStarted.countDown();return new RawResponse(200,new ByteArrayInputStream(frame()));});
            doAnswer(call->{gameStarted.countDown();assertTrue(packStarted.await(2,TimeUnit.SECONDS));assertFalse(Files.exists(instance.resolve("mods/demo.jar")));Files.createDirectories(instance);Files.writeString(instance.resolve("game-ready"),"ready");return null;}).when(game).install(any(),any());
            try{prepare(app);assertArrayEquals(content,Files.readAllBytes(instance.resolve("mods/demo.jar")));assertTrue(Files.exists(instance.resolve("game-ready")));assertEquals("1",new LocalManifestStore().load(instance).orElseThrow().version());}
            finally{close(app);}
        });
    }
    @Test void failedMinecraftDoesNotApplyPackOrPublishItsManifest() {
        assertTimeoutPreemptively(ofSeconds(5),()->{
            LauncherApp app=app();Path instance=new AppDirs(root).instance("pack");
            when(api.downloadBlobs("pack",List.of(Sha256.hex(content)),null)).thenAnswer(call->new RawResponse(200,new ByteArrayInputStream(frame())));
            doThrow(new GameInstallException("fixture failure",new IOException("fixture"))).when(game).install(any(),any());
            try{assertThrows(GameInstallException.class,()->prepare(app));assertFalse(Files.exists(instance.resolve("mods/demo.jar")));assertTrue(new LocalManifestStore().load(instance).isEmpty());
                if(Files.isDirectory(instance.getParent()))try(var children=Files.list(instance.getParent())){assertTrue(children.noneMatch(path->path.getFileName().toString().startsWith(".sn3-batch-")));}}
            finally{close(app);}
        });
    }
    @Test void previouslyManagedMinecraftFilesKeepSequentialOrdering() throws Exception {
        LauncherApp app=app();Path instance=new AppDirs(root).instance("pack");Files.createDirectories(instance);Files.writeString(instance.resolve("client.jar"),"old");
        new LocalManifestStore().save(instance,new StoredManifest("old",Map.of("client.jar",Sha256.hex("old".getBytes(StandardCharsets.UTF_8)))));
        when(api.downloadBlobs("pack",List.of(Sha256.hex(content)),null)).thenAnswer(call->new RawResponse(200,new ByteArrayInputStream(frame())));
        doAnswer(call->{assertArrayEquals(content,Files.readAllBytes(instance.resolve("mods/demo.jar")));return null;}).when(game).install(any(),any());
        try{prepare(app);verify(game).install(any(),any());}finally{close(app);}
    }
}
