package util;

import java.io.IOException;

import org.graphstream.algorithm.generator.DorogovtsevMendesGenerator;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;

public class GraphStreamTest {

	private static void test() throws IOException {
		Graph graph = new SingleGraph("Dorogovtsev mendes");
		DorogovtsevMendesGenerator gen = new DorogovtsevMendesGenerator();
		gen.addSink(graph);
		gen.begin();
		int n=5000;
		for(int i=0; i<n; i++) {
			gen.nextEvents();
		}
		System.out.println(graph.getNodeCount() + " nodes");
		System.out.println(graph.getEdgeCount() + " edges");
		graph.write("/tmp/DorogovtsevMendesGenerator" + n + ".gml");

		gen.end();
		graph.display();
	}
	public static void main(String[] args) {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		try {
			test();
		} catch (Throwable thr) {
			thr.printStackTrace();
		}
	}

}
