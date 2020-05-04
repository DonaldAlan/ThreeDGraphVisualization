package grapher;

import com.sun.corba.se.impl.orbutil.graph.Graph;

/**
 * 
 * Displays visualizations of some generated (synthetic) graphs. See main(String[]) at the bottom of this file for options.
 * 
 * Run ChooseGraphFileAndVisualize to get prompted for graph files to visualize.
 * 
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 */
public class BuildGeneratedGraphs {

	private static void processNodes(Node3D[] nodes, String title) {
		ReadGraphAndVisualize.processNodes(nodes, title); 
	}
	// -------------------------------------------
		public static void main(String[] args) {
			ConnectedComponent.trace=false;
			ConnectedComponent.repulsiveDenonimatorIsSquared=true;
			try {
				//processNodes(GraphGenerators.makeGraphWithChokePoint(1,1,1.0), "Two");
				//processNodes(GraphGenerators.makeComponents(50,30,30.0,0.6), "Clusters");
				//processNodes(GraphGenerators.makeComponents(100,40,30.0,0.6), "Clusters");
				processNodes(GraphGenerators.makeComponents(20,20,15.0,0.5), "Clusters");
				//processNodes(GraphGenerators.makeGraphWithChokePoint(15, 15, 0.95), "ChokePoint Graph"); System.exit(0);
				//processNodes(GraphGenerators.makeCube(), "Cube"); System.exit(0);
				//processNodes(GraphGenerators.makeHyperCube1(), "HyperCube"); System.exit(0);
				//processNodes(GraphGenerators.makeSphere(4,64),"Sphere Graph"); System.exit(0);
				//processNodes(GraphGenerators.createBarabasiAlbertViaJung(820,2),"BarboseAlbertViaJung"); System.exit(0);
//				processNodes(GraphGenerators.createKleinbergSmallWorldGenerator(20,20,0.1,true),"KleinbergSmallWorldGenerator"); System.exit(0);
//				processNodes(GraphGenerators.createKleinbergSmallWorldGenerator(100,100,0.1,true),"KleinbergSmallWorldGenerator"); System.exit(0);
//				processNodes(GraphGenerators.makeGraph(300,Math.sqrt(0.95),true),"Local"); System.exit(0);
//				processNodes(GraphGenerators.makeGraph(3000,Math.sqrt(0.95),true),"Local"); System.exit(0);
			} catch (Throwable e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
}
