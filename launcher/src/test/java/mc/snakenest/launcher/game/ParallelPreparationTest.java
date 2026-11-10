package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static java.time.Duration.ofSeconds;

class ParallelPreparationTest {
    @Test void downloadsOverlapButApplicationWaitsForGame() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            CountDownLatch download=new CountDownLatch(1),gameStarted=new CountDownLatch(1);
            AtomicBoolean ready=new AtomicBoolean(),applied=new AtomicBoolean();
            ParallelPreparation.run(barrier->{assertTrue(gameStarted.await(1,TimeUnit.SECONDS));download.countDown();barrier.await();assertTrue(ready.get());applied.set(true);},
                    ()->{gameStarted.countDown();assertTrue(download.await(1,TimeUnit.SECONDS));assertFalse(applied.get());ready.set(true);});
            assertTrue(applied.get());
        });
    }
    @Test void gameFailureCancelsWaitingPackAndJoinsBeforeReturning() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            CountDownLatch waiting=new CountDownLatch(1);AtomicBoolean stopped=new AtomicBoolean(),applied=new AtomicBoolean();
            assertThrows(IOException.class,()->ParallelPreparation.run(barrier->{try{waiting.countDown();barrier.await();applied.set(true);}finally{stopped.set(true);}},
                    ()->{assertTrue(waiting.await(1,TimeUnit.SECONDS));throw new IOException("fixture game failure");}));
            assertTrue(stopped.get());assertFalse(applied.get());
        });
    }
    @Test void packFailureCancelsAndJoinsGameWorker() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            CountDownLatch started=new CountDownLatch(1);AtomicBoolean stopped=new AtomicBoolean();
            assertThrows(IOException.class,()->ParallelPreparation.run(barrier->{assertTrue(started.await(1,TimeUnit.SECONDS));throw new IOException("fixture pack failure");},
                    ()->{started.countDown();try{Thread.sleep(10000);}finally{stopped.set(true);}}));assertTrue(stopped.get());
        });
    }
    @Test void cancellationStopsBothBranchesBeforeReturning() throws Exception {
        CountDownLatch started=new CountDownLatch(2);AtomicInteger active=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();
        Thread caller=new Thread(()->{try{ParallelPreparation.run(barrier->{active.incrementAndGet();started.countDown();try{barrier.await();}finally{active.decrementAndGet();}},
                ()->{active.incrementAndGet();started.countDown();try{Thread.sleep(10000);}finally{active.decrementAndGet();}});}catch(InterruptedException expected){cancelled.set(true);}catch(Exception failure){throw new RuntimeException(failure);}});
        caller.start();try{assertTrue(started.await(1,TimeUnit.SECONDS));caller.interrupt();caller.join(2000);assertFalse(caller.isAlive());assertTrue(cancelled.get());assertEquals(0,active.get());}finally{caller.interrupt();caller.join(2000);}
    }
    @Test void gameOwnedPathsUseOriginalSequentialOrdering() {
        assertTrue(ParallelPreparation.canOverlap(List.of("mods/a.jar","config/options.json","options.txt",".filesmetas")));
        for(String path:List.of("client.jar","1.20.4.json","assets/x","libraries/x.jar","versions/a/a.jar","natives/lib.so","LIBRARIES\\x.jar"))
            assertFalse(ParallelPreparation.canOverlap(List.of(path)),path);
    }
}
