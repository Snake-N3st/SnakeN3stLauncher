package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.*;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.game.*;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.net.LogoCache;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.*;
import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Run alone under Xvfb: real login components and cold cache, no account or remote network. */
@EnabledIfSystemProperty(named="sn3.test.gui",matches="true")
class LauncherBrandingUiTest {
    @TempDir Path root;
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void invoke(LauncherApp app,String name)throws Exception{Method method=LauncherApp.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(app);}
    @Test void firstDownloadedLogoReplacesLoginPlaceholderAndWindowIcon()throws Exception {
        BufferedImage source=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);source.setRGB(8,8,0xff169442);
        Path image=root.resolve("fixture.png");ImageIO.write(source,"png",image.toFile());
        AppDirs dirs=new AppDirs(root.resolve("fresh"));ConfigStore config=new ConfigStore(dirs);
        LauncherAuthApiClient auth=mock(LauncherAuthApiClient.class);
        when(auth.fetchClientInfo("client")).thenReturn(new ClientInfo("Snake N3st",image.toUri().toString(),null,null));
        LauncherApp app=new LauncherApp(dirs,URI.create("https://example.test"),"client",config,mock(ThemeController.class),auth,mock(ModpackApiClient.class),mock(NewsApiClient.class),mock(KeyStorage.class),mock(GameInstallService.class),mock(GameLaunchService.class),mock(PinProtection.class),null,mock(BugReportApiClient.class));
        AtomicReference<LoginFrame> frame=new AtomicReference<>();
        try {
            assertNull(new LogoCache(dirs).cached());invoke(app,"fetchClientBrandingBlocking");
            SwingUtilities.invokeAndWait(()->{LoginFrame login=new LoginFrame(mock(DeviceAuthService.class),ignored->{});login.setClientInfo("Snake N3st",null);frame.set(login);});
            field(LauncherApp.class,"loginFrame").set(app,frame.get());
            Object panel=field(LoginFrame.class,"logoPanel").get(frame.get());Field panelImage=field(panel.getClass(),"image");
            assertNull(panelImage.get(panel));invoke(app,"refreshLogoInBackground");
            AtomicReference<BufferedImage> displayed=new AtomicReference<>();long deadline=System.nanoTime()+3_000_000_000L;
            while(displayed.get()==null&&System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->{try{displayed.set((BufferedImage)panelImage.get(panel));}catch(Exception failure){throw new RuntimeException(failure);}});if(displayed.get()==null)Thread.sleep(10);}
            assertNotNull(displayed.get(),"Cold-start logo must update the actual login panel, not just the frame icon");
            assertEquals(source.getRGB(8,8),displayed.get().getRGB(8,8));
            SwingUtilities.invokeAndWait(()->{assertNotNull(frame.get().getIconImage());assertEquals("Snake N3st - Connexion",frame.get().getTitle());});
            assertNotNull(new LogoCache(dirs).cached());
        }finally {
            if(frame.get()!=null)SwingUtilities.invokeAndWait(()->frame.get().dispose());
            ((ExecutorService)field(LauncherApp.class,"backgroundExecutor").get(app)).shutdownNow();
        }
    }
}
