package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.benchmark.BenchmarkRunner;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class BenchmarkDialog {
    private BenchmarkDialog() {}
    static void show(Component parent) {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(parent),"Benchmark CPU + GPU",Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea output=new JTextArea("Mesure en cours (~10 secondes)…\n\nCPU monothread, puis GPU OpenGL.\nFerme les tâches lourdes pour un résultat représentatif.",17,70);
        output.setEditable(false);output.setLineWrap(true);output.setWrapStyleWord(true);output.setMargin(new Insets(12,12,12,12));
        JButton close=new JButton("Annuler");JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(close);
        dialog.add(new JScrollPane(output),BorderLayout.CENTER);dialog.add(actions,BorderLayout.SOUTH);
        BenchmarkRunner runner=new BenchmarkRunner();
        SwingWorker<String,Void> worker=new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception{return runner.run().describe();}
            @Override protected void done(){
                if(!dialog.isDisplayable())return;
                try{output.setText(get());}catch(Exception error){output.setText("Benchmark interrompu ou indisponible. Aucun résultat fiable pour cet essai.");}
                output.setCaretPosition(0);close.setText("Fermer");
            }
        };
        close.addActionListener(e->dialog.dispose());
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent event){runner.cancel();worker.cancel(true);}});
        dialog.pack();dialog.setLocationRelativeTo(parent);worker.execute();dialog.setVisible(true);
    }
}
