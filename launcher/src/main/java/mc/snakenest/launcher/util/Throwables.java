package mc.snakenest.launcher.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Small formatting helper shared by anything that needs a full stack trace as plain text. */
public final class Throwables {

    private Throwables() {
    }

    public static String stackTraceOf(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
