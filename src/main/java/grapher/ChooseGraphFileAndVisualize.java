package grapher;

import java.io.File;

import javax.swing.JFileChooser;

/**
 * 
 * Prompts you for a graph file and visualizes that file in 3D using JavaFX.
 * 
 * Run BuildGeneratedGraphs.java to visualize some synthetic generated graphs (choose the graphs at the bottom).
 * 
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 * See README.txt for more documentation. 
 */
public final class ChooseGraphFileAndVisualize {
	private static void test() {
		final JFileChooser fileChooser = // new JFileChooser("d:/tmp"); 
		   new JFileChooser("graphs");
		fileChooser.setSelectedFile(new File("Collatz.gml")); // COllatz  graph.gml
		fileChooser.showOpenDialog(null);
		File file = fileChooser.getSelectedFile();
		if (file==null) {
			System.exit(0);
		} else {
			System.out.println("Selected file is " + file.getAbsolutePath());
			ReadGraphAndVisualize read = new ReadGraphAndVisualize(file.getAbsolutePath());
			try {
				read.readGraphAndVisualize(Visualizer.Layout.Stochastic);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String [] args) {
		try {
			while (true) {test();}}
		catch (Throwable thr) {
			thr.printStackTrace();
			System.exit(1);
		}
	}
}