package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.benchmark.BenchmarkResult;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Locale;

final class BenchmarkDashboard extends JPanel {
    BenchmarkDashboard(BenchmarkResult result) {
        super(new BorderLayout(0,12));setBorder(new EmptyBorder(14,14,14,14));
        JPanel summary=new JPanel();summary.setLayout(new BoxLayout(summary,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Mesures CPU + GPU");title.setFont(title.getFont().deriveFont(Font.BOLD,20f));summary.add(title);
        summary.add(Box.createVerticalStrut(4));summary.add(new JLabel("Minecraft, modpacks lourds et shaders : performances non validées par ce test."));
        summary.add(Box.createVerticalStrut(12));
        JPanel cards=new JPanel(new GridLayout(1,2,12,0));cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        cards.add(card("CPU — un cœur",result.cpuValid()?String.format(Locale.FRANCE,"%.1f M itérations/s",result.cpuMillionIterations()):"Indisponible","Mélange d’entiers · moyenne sur 3 s"));
        double millis=result.gpuFrameMillis();
        cards.add(card("GPU — image du test",result.gpuValid()&&Double.isFinite(millis)&&millis>0?String.format(Locale.FRANCE,"%.3f ms",millis):"Indisponible",result.gpuValid()?String.format(Locale.FRANCE,"%.1f M pixels/s · moyenne sur 4 s",result.gpuMillionPixels()):"Pas de mesure matérielle valide"));
        summary.add(cards);summary.add(Box.createVerticalStrut(8));
        JLabel renderer=new JLabel("Rendu : "+(result.renderer()==null||result.renderer().isBlank()?"non identifié":result.renderer()));renderer.setToolTipText(renderer.getText());summary.add(renderer);
        add(summary,BorderLayout.NORTH);
        JPanel center=new JPanel();center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));
        JPanel charts=new JPanel(new GridLayout(1,2,12,0));
        charts.add(new BenchmarkChart("CPU · débit mesuré","M itérations/s · plus haut = plus rapide",result.cpuValid()?result.cpuSamples():List.of(),new Color(30,138,88)));
        charts.add(new BenchmarkChart("GPU · durée d’une image synthétique","ms / image du test · plus bas = plus rapide",result.gpuValid()?result.gpuSamples():List.of(),new Color(44,119,190)));
        center.add(charts);center.add(Box.createVerticalStrut(8));
        JLabel note=new JLabel("512 × 512 · shader 48 étapes. Ces courbes ne représentent pas les FPS de Minecraft.");note.setAlignmentX(Component.LEFT_ALIGNMENT);center.add(note);
        center.add(Box.createVerticalStrut(8));
        JToggleButton details=new JToggleButton("Afficher le protocole et les limites");details.setAlignmentX(Component.LEFT_ALIGNMENT);center.add(details);
        JTextArea text=new JTextArea(result.describe(),14,65);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setMargin(new Insets(10,10,10,10));
        JScrollPane scroll=new JScrollPane(text);scroll.setVisible(false);scroll.setAlignmentX(Component.LEFT_ALIGNMENT);center.add(scroll);
        details.addActionListener(e->{scroll.setVisible(details.isSelected());details.setText(details.isSelected()?"Masquer les détails":"Afficher le protocole et les limites");revalidate();repaint();});
        add(center,BorderLayout.CENTER);
    }
    private JPanel card(String name,String value,String subtitle) {
        JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),new EmptyBorder(10,12,10,12)));
        panel.add(new JLabel(name));JLabel metric=new JLabel(value);metric.setFont(metric.getFont().deriveFont(Font.BOLD,24f));panel.add(metric);panel.add(new JLabel(subtitle));return panel;
    }
}
