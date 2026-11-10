package mc.snakenest.launcher.ui.modpack;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** Each transfer owns its own text and percentage; updates are marshalled onto Swing's EDT. */
public final class InstallProgressPanel extends JPanel {
    private final Map<String,Row> rows=new LinkedHashMap<>();
    public InstallProgressPanel(){setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setVisible(false);}
    public void update(String key,String message,double fraction){SwingUtilities.invokeLater(()->{
        Row row=rows.computeIfAbsent(key,ignored->{Row created=new Row();created.setName(key);add(created);return created;});
        if(message!=null)row.label.setText(message);
        row.bar.setIndeterminate(Double.isNaN(fraction));
        if(Double.isFinite(fraction))row.bar.setValue((int)Math.round(100*Math.max(0,Math.min(1,fraction))));
        setVisible(true);revalidate();repaint();
    });}
    public void clear(String key){SwingUtilities.invokeLater(()->{Row row=rows.remove(key);if(row!=null)remove(row);setVisible(!rows.isEmpty());revalidate();repaint();});}
    public void clearAll(){SwingUtilities.invokeLater(()->{rows.clear();removeAll();setVisible(false);revalidate();repaint();});}
    private static final class Row extends JPanel {
        final JLabel label=new JLabel();final JProgressBar bar=new JProgressBar(0,100);
        Row(){super(new BorderLayout(0,3));setOpaque(false);setBorder(BorderFactory.createEmptyBorder(3,0,3,0));add(label,BorderLayout.NORTH);add(bar,BorderLayout.SOUTH);}
    }
}
