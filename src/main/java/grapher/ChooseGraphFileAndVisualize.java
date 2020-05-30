package grapher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

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
	private static final String lastFilePathPath = "lastFilePath.txt";
	private static String getLastPath() {
		File file = new File(lastFilePathPath);
		if (!file.exists()) {
			return null;
		}
		try {
			FileInputStream fis = new FileInputStream(file);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
			String line=reader.readLine();
			reader.close();
			return line;
		} catch (IOException exc) {
			System.err.println("Couldn't read from " + lastFilePathPath + " due to " + exc.getMessage());
			return null;
		}
	}
	private static void savePath(String path) {
		try {
			PrintWriter writer = new PrintWriter(lastFilePathPath);
			writer.println(path);
			writer.close();
		} catch (IOException exc) {
			exc.printStackTrace();
		}
	}
	private static void choose() {
		String dirPath = "graphs";
		String fileName = "graphs/collatz.gml";
		String filePath = getLastPath();
		if (filePath!=null) {
			File file = new File(filePath);
			dirPath = file.getParent();
			fileName = file.getName();
		}
		final JFileChooser fileChooser = // new JFileChooser("d:/tmp"); 
		   new JFileChooser(dirPath);
		fileChooser.setSelectedFile(new File(fileName)); 
		if (fileChooser.showOpenDialog(null) == JFileChooser.CANCEL_OPTION) {
			System.exit(0);
		}
		File file = fileChooser.getSelectedFile();
		if (file==null) {
			System.exit(0);
		} else {
			System.out.println("Selected file is " + file.getAbsolutePath());
			savePath(file.getAbsolutePath());
			ReadGraphAndVisualize read = new ReadGraphAndVisualize(file.getAbsolutePath());
			try {
				read.readGraphAndVisualize();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String [] args) {
		try {
			choose();
		}
		catch (Throwable thr) {
			thr.printStackTrace();
			System.exit(1);
		}
	}
}