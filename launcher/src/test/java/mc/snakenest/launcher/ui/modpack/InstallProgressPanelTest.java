package mc.snakenest.launcher.ui.modpack;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.Component;
import static org.junit.jupiter.api.Assertions.*;

class InstallProgressPanelTest {
    @org.junit.jupiter.api.BeforeAll static void headless(){System.setProperty("java.awt.headless","true");}
    private static JPanel row(InstallProgressPanel panel,String name){for(Component row:panel.getComponents())if(name.equals(row.getName()))return (JPanel)row;throw new AssertionError(name);}
    private static JProgressBar bar(JPanel row){for(Component item:row.getComponents())if(item instanceof JProgressBar bar)return bar;throw new AssertionError();}
    private static JLabel label(JPanel row){for(Component item:row.getComponents())if(item instanceof JLabel label)return label;throw new AssertionError();}
    @Test void independentBarsKeepTheirLabelsAndPercentages() throws Exception {
        InstallProgressPanel[] ui={null};SwingUtilities.invokeAndWait(()->ui[0]=new InstallProgressPanel());
        ui[0].update("modpack","Modpack",.2);ui[0].update("game","Minecraft",.7);ui[0].update("files","Application",Double.NaN);
        SwingUtilities.invokeAndWait(()->{assertEquals(20,bar(row(ui[0],"modpack")).getValue());assertEquals(70,bar(row(ui[0],"game")).getValue());assertTrue(bar(row(ui[0],"files")).isIndeterminate());});
        ui[0].update("modpack","Modpack 40 %",.4);ui[0].update("game",null,.8);
        SwingUtilities.invokeAndWait(()->{assertEquals(40,bar(row(ui[0],"modpack")).getValue());assertEquals(80,bar(row(ui[0],"game")).getValue());assertEquals("Minecraft",label(row(ui[0],"game")).getText());});
        ui[0].clear("modpack");SwingUtilities.invokeAndWait(()->{assertEquals(2,ui[0].getComponentCount());assertTrue(ui[0].isVisible());assertEquals(80,bar(row(ui[0],"game")).getValue());});
        ui[0].clearAll();SwingUtilities.invokeAndWait(()->{assertEquals(0,ui[0].getComponentCount());assertFalse(ui[0].isVisible());});
    }
}
