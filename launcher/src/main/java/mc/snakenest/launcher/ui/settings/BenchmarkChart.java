package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.benchmark.BenchmarkResult.Sample;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Locale;

/** Measured bins only: independent time/value axes, no interpolated synthetic scores. */
final class BenchmarkChart extends JPanel {
    private final List<Sample> samples;
    private final String title,unit;
    private final Color line;
    BenchmarkChart(String title,String unit,List<Sample> samples,Color line) {
        this.title=title;this.unit=unit;this.samples=samples;this.line=line;
        setPreferredSize(new Dimension(375,225));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),BorderFactory.createEmptyBorder(4,4,4,4)));
        getAccessibleContext().setAccessibleName(title+", "+unit+", "+samples.size()+" échantillons mesurés");
        if(!samples.isEmpty()){
            double min=samples.stream().mapToDouble(Sample::value).min().orElse(0),max=samples.stream().mapToDouble(Sample::value).max().orElse(0);
            getAccessibleContext().setAccessibleDescription(String.format(Locale.FRANCE,"Minimum %.3f ; maximum %.3f %s. Durée %.2f secondes.",min,max,unit,samples.get(samples.size()-1).seconds()));
        }
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color foreground=UIManager.getColor("Label.foreground");if(foreground==null)foreground=Color.DARK_GRAY;
            g.setColor(foreground);g.setFont(getFont().deriveFont(Font.BOLD,13f));g.drawString(title,12,21);
            g.setFont(getFont().deriveFont(11f));g.drawString(unit,12,39);
            if(samples.isEmpty()){g.drawString("Aucun échantillon matériel valide",18,getHeight()/2);return;}
            int left=62,top=54,right=getWidth()-18,bottom=getHeight()-34;
            if(right<=left||bottom<=top)return;
            double max=samples.stream().mapToDouble(Sample::value).max().orElse(1);
            double seconds=Math.max(1,Math.ceil(samples.get(samples.size()-1).seconds()));
            for(int i=0;i<=4;i++){
                int y=bottom-(bottom-top)*i/4;
                g.setColor(new Color(foreground.getRed(),foreground.getGreen(),foreground.getBlue(),32));g.drawLine(left,y,right,y);
                g.setColor(foreground);double value=max*(i/4d);
                String label=String.format(Locale.FRANCE,max>10000?"%.1e":"%.1f",value);g.drawString(label,4,y+4);
            }
            for(int i=0;i<=4;i++){
                int x=left+(right-left)*i/4;g.drawString(String.format(Locale.FRANCE,"%.1f",seconds*i/4),x-8,bottom+15);
            }
            g.drawString("Temps écoulé (s)",left+(right-left)/2-42,getHeight()-6);
            Path2D path=new Path2D.Double();boolean first=true;
            for(Sample sample:samples){double x=left+(right-left)*sample.seconds()/seconds,y=bottom-(bottom-top)*(sample.value()/max);if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);}
            g.setColor(line);g.setStroke(new BasicStroke(2f));g.draw(path);
            for(Sample sample:samples){int x=(int)(left+(right-left)*sample.seconds()/seconds),y=(int)(bottom-(bottom-top)*(sample.value()/max));g.fillOval(x-2,y-2,4,4);}
        }finally{g.dispose();}
    }
}
