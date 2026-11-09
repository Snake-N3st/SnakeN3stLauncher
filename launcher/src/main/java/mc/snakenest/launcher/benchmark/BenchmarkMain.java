package mc.snakenest.launcher.benchmark;

import com.google.gson.Gson;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/** Separate JVM: a broken graphics driver cannot crash the launcher. */
public final class BenchmarkMain {
    private static volatile int sink;
    private static final Gson JSON = new Gson();
    private BenchmarkMain() {}
    public static double cpu(long durationNanos) {return cpu(durationNanos,null);}
    private static double cpu(long durationNanos,List<BenchmarkResult.Sample> samples) {
        int x=0x13579bdf, sum=0;long iterations=0, start=System.nanoTime();
        long binStart=start,binIterations=0;
        do {
            for(int i=0;i<32768;i++){x^=x<<13;x^=x>>>17;x^=x<<5;x=x*1664525+1013904223;sum^=x;}
            iterations+=32768;
            long now=System.nanoTime();
            if(samples!=null&&now-binStart>=250_000_000L){
                samples.add(new BenchmarkResult.Sample((now-start)/1e9,(iterations-binIterations)/((now-binStart)/1e9)/1e6));
                binStart=now;binIterations=iterations;
            }
        } while(System.nanoTime()-start<durationNanos);
        sink=sum;
        return iterations/((System.nanoTime()-start)/1_000_000_000d)/1_000_000d;
    }
    public static void main(String[] args) {
        cpu(500_000_000L);
        List<BenchmarkResult.Sample> samples=new ArrayList<>();
        double cpuScore=cpu(3_000_000_000L,samples);
        System.out.println(JSON.toJson(Map.of("cpu",cpuScore,"cpuSamples",samples)));System.out.flush();
        try { gpu(); }
        catch(Throwable failure) {
            System.out.println(JSON.toJson(Map.of("error","Contexte OpenGL indisponible ("+failure.getClass().getSimpleName()+")")));
        }
    }
    private static void gpu() {
        GLFWErrorCallback callback=GLFWErrorCallback.create((code,message)->{});callback.set();
        long window=0;int program=0,vertex=0,fragment=0,texture=0,fbo=0,vbo=0;
        try {
            if(!glfwInit())throw new IllegalStateException("GLFW initialization failed");
            glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,2);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
            window=glfwCreateWindow(32,32,"SN3 GPU benchmark",0,0);
            if(window==0)throw new IllegalStateException("OpenGL unavailable");
            glfwMakeContextCurrent(window);glfwSwapInterval(0);GL.createCapabilities();
            String renderer=glGetString(GL_RENDERER);
            System.out.println(JSON.toJson(Map.of("renderer",renderer==null ? "inconnu":renderer)));System.out.flush();
            if(renderer==null || BenchmarkResult.isSoftwareRenderer(renderer))throw new IllegalStateException("Software renderer");
            vertex=shader(GL_VERTEX_SHADER,"#version 150\nin vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.0,1.0);}");
            fragment=shader(GL_FRAGMENT_SHADER,"#version 150\nuniform float uSeed;out vec4 color;void main(){vec3 p=vec3(gl_FragCoord.xy/512.0,uSeed);for(int i=0;i<48;i++){p=fract(sin(p.yzx*1.37+vec3(0.13,0.37,0.73))*4.31);}color=vec4(p,1.0);}");
            program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glBindAttribLocation(program,0,"aPosition");glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException("Shader link failed");
            glUseProgram(program);
            texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,512,512,0,GL_RGBA,GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
            fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Framebuffer unavailable");
            int vao=glGenVertexArrays();glBindVertexArray(vao);
            vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
            FloatBuffer positions=MemoryUtil.memAllocFloat(6);
            try {positions.put(new float[]{-1,-1,3,-1,-1,3}).flip();glBufferData(GL_ARRAY_BUFFER,positions,GL_STATIC_DRAW);} finally{MemoryUtil.memFree(positions);}
            glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,0,0L);glViewport(0,0,512,512);
            int seed=glGetUniformLocation(program,"uSeed");
            frames(seed,500_000_000L,null);
            List<BenchmarkResult.Sample> samples=new ArrayList<>();
            double score=frames(seed,4_000_000_000L,samples);
            System.out.println(JSON.toJson(Map.of("gpu",score,"gpuSamples",samples)));System.out.flush();glDeleteVertexArrays(vao);
        } finally {
            if(window!=0){if(vbo!=0)glDeleteBuffers(vbo);if(fbo!=0)glDeleteFramebuffers(fbo);if(texture!=0)glDeleteTextures(texture);if(program!=0)glDeleteProgram(program);if(vertex!=0)glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);GL.setCapabilities(null);glfwDestroyWindow(window);}
            glfwTerminate();glfwSetErrorCallback(null);callback.free();
        }
    }
    private static int shader(int type,String source) {
        int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE){glDeleteShader(shader);throw new IllegalStateException("Shader compile failed");}
        return shader;
    }
    private static double frames(int seed,long duration,List<BenchmarkResult.Sample> samples) {
        long start=System.nanoTime(),frames=0,binStart=start,binFrames=0;
        do{
            glUniform1f(seed,(frames%997)/997f);glDrawArrays(GL_TRIANGLES,0,3);glFinish();frames++;
            long now=System.nanoTime();
            if(samples!=null&&now-binStart>=100_000_000L){
                samples.add(new BenchmarkResult.Sample((now-start)/1e9,(now-binStart)/(double)(frames-binFrames)/1e6));
                binStart=now;binFrames=frames;
            }
        }while(System.nanoTime()-start<duration);
        return frames*512d*512d/((System.nanoTime()-start)/1e9)/1e6;
    }
}
