package grapher;
/**
 * 
 * Displays visualizations of some generated (synthetic) graphs. See main(String[]) at the bottom of this file for options.
 * 
 * Run ChooseGraphFileAndVisualize to get prompted for graph files to visualize.
 * 
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 * Visualize graphs in 3D using JavaFX.  You can navigate with the arrow keys or with the mouse.
 * Lots of features: click 'h' to get a help screen.   
 * 
 * Graphs are laid out using stochastic relaxation of spring tensions and minimum separation repulsive forces. 
 * Vertex positions are randomly moved from their current positions, using a decreasing temperature to control the distance moved.
 * After each move, the cost is recalculated. Moves that lower the cost are preferred.
 * 
 * There are three components to the cost:  
 * 1. a large, constant repulsive force that prevents vertices from being placed more than a specified minimum distance from each other 
 * (to prevent overcrowding).
 * 2. a repulsive force that varies as the inverse of the Euclidean distance between vertices.
 * 3. a spring force (either attractive or repulsive) between nodes. The spring constant (the force of the spring) depends on the graph 
 * theoretic distance between the nodes. 
 *
 * There are two related layout algorithms: UnrestrictedForces and ApproximateForces.
 *     
 * UnrestrictedForces is appropriate for smaller graphs, as its running time is O(n*n*d*d), where n is number of vertices and d is 
 * the (max) degree of vertices. UnrestrictedForces sets up repulsive forces (type 1 and 2 above) between each pair of vertices, 
 * as well as spring forces between vertices at graph-theoretic distance 1 or 2 (neighbors, or neighbors of neighbors).
 *  The resulting visualizations are more informative and attractive but are slow to build for large graphs.
 * 
 * ApproximateForces running time is O(n*d*d), as it mainly forces only between pairs of vertices at graph theoretic distance 1 or 2
 *  (neighbors or neighbors of neighbors). In addition, it adds repulsive forces of a <em>sample</em> of nodes not in the neighborhood. 
 *   The sampling represents an approximation to UnrestrictedForces. 
 * 
 * A UI button allows the user to choose between the layout algorithms.  For small graphs the layout defaults to UnrestrictedForces. 
 * For large graphs it defaults to ApproximateForces.
 *
 * If you increase ConnectedComponents.stochasticMovesReps layouts will improve but will take longer.
 * 
 * The layout algorithm takes advantage of multi-threading if you have multiple cores.
 * 
 * Another trick:  closely connected subgraphs tend to have distinct colors. 
 * 
 * TODO 1: First layout the most important nodes. Then fix their positions and layout the less important nodes, in stages.
 * TODO 2: Divide the (3D) Euclidean space into regions and restrict forces to local regions, to optimize UnrestrictedForces. 
 */
public class BuildGeneratedGraphs {

	private static void processNodes(Node3D[] nodes, String title) {
		ReadGraphAndVisualize.processNodes(nodes, title); 
	}
	// -------------------------------------------
		public static void main(String[] args) {
			ConnectedComponent.approximateForces=false;
			ConnectedComponent.trace=false;
			ConnectedComponent.repulsiveDenonimatorIsSquared=true;
			try {
				//processNodes(GraphGenerators.makeGraphWithChokePoint(15, 15, 1.5), "ChokePoint Graph"); System.exit(0);
				//processNodes(GraphGenerators.makeCube(), "Cube"); System.exit(0);
				//processNodes(GraphGenerators.makeHyperCube1(), "HyperCube"); System.exit(0);
				//processNodes(GraphGenerators.makeSphere(4,64),"Sphere Graph"); System.exit(0);
				//processNodes(GraphGenerators.createBarabasiAlbertViaJung(220,2),"BarboseAlbertViaJung"); System.exit(0);
				//processNodes(GraphGenerators.createKleinbergSmallWorldGenerator(20,20,0.1,true),"KleinbergSmallWorldGenerator"); System.exit(0);
				processNodes(GraphGenerators.makeGraph(300,Math.sqrt(0.9),true),"Local"); System.exit(0);
			} catch (Throwable e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
}
