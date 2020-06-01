package util;

import java.io.IOException;

import org.graphstream.algorithm.generator.BaseGenerator;
import org.graphstream.algorithm.generator.DorogovtsevMendesGenerator;
import org.graphstream.algorithm.generator.FlowerSnarkGenerator;
import org.graphstream.algorithm.generator.PetersenGraphGenerator;
import org.graphstream.algorithm.generator.ChvatalGenerator;
import org.graphstream.algorithm.generator.WattsStrogatzGenerator;
import org.graphstream.algorithm.generator.lcf.HarriesGraphGenerator;
import org.graphstream.algorithm.generator.lcf.PappusGraphGenerator;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;

public class GraphStreamTest {
	private static void generate(BaseGenerator gen, int n) throws IOException {
		Graph graph = new SingleGraph(gen.getClass().getName());
		gen.addSink(graph);
		gen.begin();
		for(int i=0; i<n; i++) {
			gen.nextEvents();
		}
		gen.end();
		System.out.println(graph.getNodeCount() + " nodes");
		System.out.println(graph.getEdgeCount() + " edges");
		graph.write("d:/tmp/" + gen.getClass().getSimpleName() + "-" + n + ".gml");

		graph.display();
	}
	private static void testWattsStrogatzGenerator() throws IOException {
		int n=500;
		int k=4;
		double prob=0.1;
		Graph graph = new SingleGraph("WattsStrogatzGenerator");
		WattsStrogatzGenerator gen = new WattsStrogatzGenerator(n,k,prob);
		gen.addSink(graph);
		gen.begin();
		for(int i=0; i<n; i++) {
			gen.nextEvents();
		}
		gen.end();
		System.out.println(graph.getNodeCount() + " nodes");
		System.out.println(graph.getEdgeCount() + " edges");
		graph.write("/tmp/WattsStrogatzGenerator-" + n + "-" + k + "-"+ prob + ".gml");

		graph.display();
	}
	private static void testDorogovtsevMendesGenerator() throws IOException {
		Graph graph = new SingleGraph("Dorogovtsevmendes");
		DorogovtsevMendesGenerator gen = new DorogovtsevMendesGenerator();
		gen.addSink(graph);
		gen.begin();
		int n=5000;
		for(int i=0; i<n; i++) {
			gen.nextEvents();
		}
		gen.end();
		System.out.println(graph.getNodeCount() + " nodes");
		System.out.println(graph.getEdgeCount() + " edges");
		graph.write("/tmp/DorogovtsevMendesGenerator" + n + ".gml");

		graph.display();
	}
	public static void main(String[] args) {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		try {
			//BaseGenerator gen = new HarriesGraphGenerator();generate(gen,1);
			testWattsStrogatzGenerator();
		} catch (Throwable thr) {
			thr.printStackTrace();
		}
	}

}
