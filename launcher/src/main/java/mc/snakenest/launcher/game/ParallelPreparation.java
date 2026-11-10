package mc.snakenest.launcher.game;

import mc.snakenest.launcher.util.ParallelTasks;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/** Network transfers overlap; modpack writes wait for Minecraft's installer to finish. */
public final class ParallelPreparation {
    private ParallelPreparation() { }
    @FunctionalInterface public interface Pack { void run(Barrier beforeApplication) throws Exception; }
    @FunctionalInterface public interface Barrier { void await() throws InterruptedException; }
    public static void run(Pack pack,ParallelTasks.Work game) throws Exception {
        CountDownLatch ready=new CountDownLatch(1);
        ParallelTasks.run(List.of(()->pack.run(ready::await),()->{game.run();ready.countDown();}),2,()->{});
    }
    /** Preserve the original ordering for packs that replace or delete Minecraft-owned files. */
    public static boolean canOverlap(Collection<String> paths) {
        for(String path:paths) {
            if(path==null)return false;
            String normalized=path.replace('\\','/').toLowerCase(Locale.ROOT);
            String first=normalized.split("/",2)[0];
            if(first.isEmpty()||first.equals(".")||first.equals("..")||first.contains(":"))return false;
            if(List.of("assets","libraries","versions","natives","runtime","runtimes","bin").contains(first))return false;
            if(!normalized.contains("/")&&(normalized.endsWith(".jar")||normalized.endsWith(".json")))return false;
        }
        return true;
    }
}
