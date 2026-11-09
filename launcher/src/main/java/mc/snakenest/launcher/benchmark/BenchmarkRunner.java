package mc.snakenest.launcher.benchmark;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Bounded subprocess runtime; cancellation/driver faults never leave the launcher blocked. */
public final class BenchmarkRunner {
    private volatile Process process;
    private volatile boolean cancelled;
    public void cancel() {cancelled=true;Process p=process;if(p!=null)p.destroyForcibly();}
    public BenchmarkResult run() throws Exception {
        List<String> command=new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString());
        if(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac"))command.add("-XstartOnFirstThread");
        command.addAll(List.of("-Xmx128m","-cp",System.getProperty("java.class.path"),BenchmarkMain.class.getName()));
        process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(cancelled){process.destroyForcibly();throw new InterruptedException();}
        var watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"benchmark-timeout");t.setDaemon(true);return t;});
        watchdog.schedule(()->process.destroyForcibly(),18,TimeUnit.SECONDS);
        double cpu=0,gpu=0;String renderer="",error=null;int bytes=0;
        try(BufferedReader output=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
            String line;
            while((line=output.readLine())!=null){
                bytes+=line.length();if(bytes>16384)throw new java.io.IOException("Sortie du benchmark trop longue");
                try{
                    JsonObject item=JsonParser.parseString(line).getAsJsonObject();
                    if(item.has("cpu"))cpu=item.get("cpu").getAsDouble();
                    if(item.has("gpu"))gpu=item.get("gpu").getAsDouble();
                    if(item.has("renderer"))renderer=item.get("renderer").getAsString();
                    if(item.has("error"))error=item.get("error").getAsString();
                }catch(RuntimeException ignored){/* Ignore driver diagnostic lines. */}
            }
            if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(gpu<=0&&error==null)error="Pilote indisponible, interruption ou délai dépassé";
            return new BenchmarkResult(cpu,gpu,renderer,error);
        }finally{process.destroyForcibly();watchdog.shutdownNow();}
    }
}
