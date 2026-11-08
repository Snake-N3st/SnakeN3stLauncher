package mc.snakenest.launcher.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PackIconCacheTest {
    @TempDir Path root;
    private BufferedImage icon() { BufferedImage image=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB); image.setRGB(0,0,0xff52c030); return image; }
    @Test void survivesRestartAndReadsOfflineWithoutAnyNetworkEvenWhenTheUrlOrVersionChanged() {
        AtomicInteger requests=new AtomicInteger();
        var online=new PackIconCache(root,"https://example.test", url->{requests.incrementAndGet();return icon();});
        assertNotNull(online.load("pack:old-pack","https://example.test/icon","1",true));
        var offline=new PackIconCache(root,"https://example.test", url->{fail("Offline downloader invoked");return null;});
        BufferedImage cached=offline.load("pack:old-pack","https://example.test/new","2",false);
        assertNotNull(cached);assertEquals(256,cached.getWidth());assertEquals(1,requests.get());
    }
    @Test void unchangedIconsUseDiskButNewVersionsRefreshAndFailuresKeepTheOldImage() {
        AtomicInteger requests=new AtomicInteger();
        var cache=new PackIconCache(root,"https://example.test",url->{requests.incrementAndGet();return icon();});
        cache.load("pack:a","https://example.test/icon","1",true);
        cache.load("pack:a","https://example.test/icon","1",true);assertEquals(1,requests.get());
        cache.load("pack:a","https://example.test/icon","2",true);assertEquals(2,requests.get());
        var unavailable=new PackIconCache(root,"https://example.test",url->null);
        assertNotNull(unavailable.load("pack:a","https://example.test/new","3",true));
        assertNotNull(unavailable.cached("pack:a"));
    }
    @Test void avatarsAreScopedToTheSiteAndAccount() {
        var cache=new PackIconCache(root,"https://example.test/",url->icon());
        cache.load("avatar:42","https://example.test/avatar",null,true);
        assertNotNull(new PackIconCache(root,"https://example.test",url->null).cached("avatar:42"));
        assertNull(cache.cached("avatar:43"));
        assertNull(new PackIconCache(root,"https://other.test",url->null).cached("avatar:42"));
    }
    @Test void corruptOptionalCacheFallsBackWithoutNetwork() throws Exception {
        var cache=new PackIconCache(root,"https://example.test",url->icon());
        cache.load("pack:a","https://example.test/icon",null,true);
        try(var paths=Files.list(root)){for(Path p:paths.filter(p->p.toString().endsWith(".png")).toList())Files.writeString(p,"broken");}
        var offline=new PackIconCache(root,"https://example.test",url->{fail("Offline downloader invoked");return null;});
        assertNull(offline.load("pack:a","https://example.test/icon",null,false));
    }
}
