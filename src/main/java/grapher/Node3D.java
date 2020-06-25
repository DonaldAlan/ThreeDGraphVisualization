package grapher;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import edu.uci.ics.jung.algorithms.importance.BetweennessCentrality;
import edu.uci.ics.jung.algorithms.importance.KStepMarkov;
import edu.uci.ics.jung.algorithms.scoring.ClosenessCentrality;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import javafx.geometry.Point3D;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;

//-----------
public class Node3D implements Comparable<Node3D> {
	public static final int maxAllowedFocusDistance =800000;
	private int xIndex,yIndex,zIndex;
	private static final Random random = new Random();
	public static final NumberFormat numberFormat = NumberFormat.getInstance();
	private static double importanceDelta = 0.1;
	public static int maxDegree=0;
	private static final Double ONE = new Double(1.0);
	//------------------------
	private final Map<Node3D,Edge> edges = new HashMap<>(); // maps node to Edge, but "this" Node could be either node1 or node2
	private final Map<String,Object> properties= new TreeMap<>();
	private double x,y,z;
	private ConnectedComponent connectedComponentForDisplay;
	private double distance = Double.MAX_VALUE;
	private double importance=1.0;
	private int indexInImportanceOrder;
	private Sphere sphere=null;
	private boolean isVisible = true;
	private static Set<String> allIds = new HashSet<>();
	//-----------------------------------
	static {
		numberFormat.setMaximumFractionDigits(1);
	}
	public Node3D(String id) {
		this(id,id);
		assert(allIds.add(id));
	}
	public Node3D(String id, String description) {
		properties.put("id", id);
	}
	@Override
	public String toString() {
		return properties.get("id").toString();
	}

	public static void computeConnectedComponentsForDisplay(Node3D[] nodesToDisplay) {
		for (Node3D node : nodesToDisplay) {
			node.connectedComponentForDisplay = new ConnectedComponent(node);
		}
		for (Node3D node : nodesToDisplay) {
			assert(node.isVisible());
			for (Node3D otherNode : node.getNeighbors()) {
				if (node==otherNode) {
					//System.err.println("Warning node " + node + " == " + otherNode);
					continue;
				}
				if (!otherNode.isVisible()) {
					continue;
				}
				if (node.connectedComponentForDisplay != otherNode.connectedComponentForDisplay) {
					final Node3D nodeFirstOther = otherNode.connectedComponentForDisplay.getFirst();
					int comp = node.connectedComponentForDisplay.getFirst().compareTo(nodeFirstOther);
					if (comp == 0) {
						System.err.println("node = " + node + ", otherNode = " + otherNode);
						throw new IllegalStateException();
					}
					// Tricky code below.
					if (comp < 0) {
						// Merge otherNode into this node
						node.connectedComponentForDisplay.merge(otherNode.connectedComponentForDisplay);
						if (ConnectedComponent.totalCount == 0) {
							System.err.println("totalCount==0 after adding edge " + otherNode + " to " + node);
						}
						otherNode.connectedComponentForDisplay = node.connectedComponentForDisplay;
						for (Node3D n : otherNode.connectedComponentForDisplay.getNodes()) {
							n.connectedComponentForDisplay = node.connectedComponentForDisplay;
						}
					} else {
						otherNode.connectedComponentForDisplay.merge(node.connectedComponentForDisplay);
						node.connectedComponentForDisplay = otherNode.connectedComponentForDisplay;
						for (Node3D n : node.connectedComponentForDisplay.getNodes()) {
							n.connectedComponentForDisplay = otherNode.connectedComponentForDisplay;
						}
					}
				}
			}
		}
	}
	
	public void setIndices(int xIndex,int yIndex, int zIndex) {
		this.xIndex=xIndex;
		this.yIndex=yIndex;
		this.zIndex=zIndex;
	}
	public int getXIndex() {
		return xIndex;
	}
	public int getYIndex() {
		return yIndex;
	}
	public int getZIndex() {
		return zIndex;
	}
	public void setDescription(String descr) {
		properties.put("description", descr);
	}
	public Map<String,Object> getProperties() {
		return properties;
	}
	public int getIndexInImportanceOrder() {
		return indexInImportanceOrder;
	}
	public void setIndexInImportanceOrder(int index) {
		this.indexInImportanceOrder=index;
	}
	
	public Map<Node3D,Edge> getEdges() {return edges;}

	public ConnectedComponent getConnectedComponent() {return connectedComponentForDisplay;}
	public double getX() {return x;}
	public double getY() {return y;}
	public double getZ() {return z;}
	public void addEdge(Node3D other, Edge edge) { 
		edges.put(other, edge);
		if (edges.size()>maxDegree) {
			maxDegree = edges.size();
		}	
	}
	public Edge addEdge(Node3D node) {return addEdge(node,1.0);}
	public Edge addEdge(Node3D otherNode, double weight) {
		Edge edge = new Edge(this,otherNode);
		if (weight!=1.0) {
			edge.addProperty("weight", ONE);
		}
		addEdge(otherNode,edge);
		return edge;
	}
	private static double edgeDistanceTo(PriorityQueue<Node3D> queue, Node3D target) {
		while (!queue.isEmpty()) {
			Node3D next = queue.remove();
			if (next.equals(target)) {
				return next.distance;
			}
			for(Map.Entry<Node3D,Edge> entry: next.edges.entrySet()) {
				Node3D node = entry.getKey();
				double weight = entry.getValue().getWeight();
				double newDistance = next.distance + weight;
				if (newDistance< node.distance) {
					node.distance=newDistance;
					queue.add(node);
				}
			}
		}
		return Double.MAX_VALUE;
	}
	public static double distanceSquared(double x, double y, double z, Node3D other) {
		return square(x-other.x) + square(y-other.y) + square(z-other.z);
	}
	public static double distance(double x, double y, double z, Node3D other) {
		return Math.sqrt(square(x-other.x) + square(y-other.y) + square(z-other.z));
	}
	public double distance(Node3D other) {
		return distance(x,y,z, other);
	}

	private static double distanceIndex(int xIndex, int yIndex, int zIndex, Node3D other) {
		return Math.sqrt(square(xIndex-other.xIndex) + square(yIndex-other.yIndex) + square(zIndex-other.zIndex));
	}
	private static double distanceIndexSquared(int xIndex, int yIndex, int zIndex, Node3D other) {
		return square(xIndex-other.xIndex) + square(yIndex-other.yIndex) + square(zIndex-other.zIndex);
	}
	public double distanceIndex(Node3D other) {
		return distanceIndex(xIndex,yIndex,zIndex,other);
	}
	public double getCost(final ConnectedComponent component) {
		return getCost(component, x,y,z);
	}

	public double getCost(final ConnectedComponent component, double x, double y, double z) {
		double cost = 0.0;
		for (Node3D neighbor : getNeighbors()) {
			if (neighbor.isVisible()) {
				cost += distance(x, y, z, neighbor);
			}
		}
		if (Visualizer.maxRepulsiveNodesToInclude > 0 && Visualizer.repulsionFactor>0.0) {
			final int n = component.getNodes().size();
			final int countOfNonNeighbors = n - edges.size() -1;
			int countRepulsive = 0;
			double repulsiveCost = 0.0;
			if (countOfNonNeighbors <= Visualizer.maxRepulsiveNodesToInclude) {
				for(Node3D nonNeighbor:component.getNodes()) {
					if (nonNeighbor!=this && nonNeighbor.isVisible() && !edges.containsKey(nonNeighbor)) {
						repulsiveCost -= distanceSquared(x, y, z, nonNeighbor);
						countRepulsive++;
					}
				}
			} else {
				random.setSeed(indexInImportanceOrder); 
				for (int i = 0; i < Visualizer.maxRepulsiveNodesToInclude; i++) {
					Node3D nonNeighbor = component.getNodes().get(random.nextInt(n));
					if (nonNeighbor!=this && nonNeighbor.isVisible() && !edges.containsKey(nonNeighbor)) {
						repulsiveCost += 1.0/distanceSquared(x, y, z, nonNeighbor);
						countRepulsive++;
					}
				}
			}
			if (countRepulsive > 0) {
				cost += Visualizer.repulsionFactor * repulsiveCost / countRepulsive;
			}
		}
		return cost;
	}
	public int getDegree() {
		return edges.size();
	}
	public double getCostIndex(final ConnectedComponent component) {
		return getCostIndex(component, xIndex, yIndex, zIndex);
	}
	public double getCostIndex(final ConnectedComponent component, int xIndex, int yIndex, int zIndex) {
		double cost=0.0;
		for(Node3D neighbor: getNeighbors()) {
			if (neighbor.isVisible()) {
				cost+= distanceIndex(xIndex,yIndex,zIndex,neighbor);
			}
		}
		if (Visualizer.maxRepulsiveNodesToInclude > 0 && Visualizer.repulsionFactor>0.0) {
			final int n=component.getNodes().size();
			final int countOfNonNeighbors = n-edges.size();
			final int countOfNonNeighborsToInclude = Math.min(Visualizer.maxRepulsiveNodesToInclude, countOfNonNeighbors);
			double repulsiveCost = 0.0;
			int countRepulsive=0;
			if (countOfNonNeighbors <= Visualizer.maxRepulsiveNodesToInclude) {
				for(Node3D nonNeighbor: component.getNodes()) {
					if (nonNeighbor!=this && nonNeighbor.isVisible() && !edges.containsKey(nonNeighbor)) {
						repulsiveCost = 1.0/distanceIndexSquared(xIndex,yIndex,zIndex,nonNeighbor);
						countRepulsive++;
					}
				}
			} else {
				random.setSeed(indexInImportanceOrder); 
				for(int i=0;i<countOfNonNeighborsToInclude;i++) {
					Node3D nonNeighbor = component.getNodes().get(random.nextInt(n));
					if (nonNeighbor!=this && nonNeighbor.isVisible() && !edges.containsKey(nonNeighbor)) {
						repulsiveCost = 1.0/distanceIndexSquared(xIndex,yIndex,zIndex,nonNeighbor);
						countRepulsive++;
					}
				}
			}
			//double ratio = Visualizer.repulsionFactor * ((1.0 + edges.size())/countRepulsive);
			if (countRepulsive>0) {
				cost += Visualizer.repulsionFactor * repulsiveCost/ countRepulsive;
			}
		}
		return cost;
	}
	public double getCostIfWeWereAtXYZ(ConnectedComponent component,double x,double y, double z) {
		return getCost(component,x,y,z);
	}
	
	public double getCostIfWeWereAtIndex(ConnectedComponent component,int x,int y, int z) {
		return getCostIndex(component, x,y,z);
	}
	
	private static double square(double x) {return x*x;}
	private static double square(int x) {return x*x;}
	public double xyzDistanceTo(Node3D other) {
		return Math.abs(x-other.x)+Math.abs(y-other.y)+ Math.abs(z-other.z); 
	}
	public double meanXYZDistanceToNeighbors() {
		if (edges.isEmpty()) {
			return 0.0;
		}
		double sum=0.0;
		for(Node3D other: edges.keySet()) {
			sum+= xyzDistanceTo(other);
		}
		return sum/edges.size();
		
	}
	public double meanXYZDistanceToNonNeighbors() {
		Set<Node3D> neighbors = edges.keySet();
		int count=0;
		double sum=0.0;
		for(Node3D other: connectedComponentForDisplay.getNodes()) {
			if (!neighbors.contains(other)) {
				sum+= xyzDistanceTo(other);
				count++;
			}
		}
		return sum/count;
	}
	/**
	 * 
	 * @return set of neighbors regardless of visibility
	 */
	public Set<Node3D> getNeighbors() {return edges.keySet();}
	
	public Iterable<Node3D> getVisibleNeighborsIterable() {
		return new Iterable<Node3D>() {
			@Override
			public Iterator<Node3D> iterator() {
				return new Iterator<Node3D>(){
					final Iterator<Node3D> iteratorAux = edges.keySet().iterator();
					Node3D nextNodeCached=null;
					@Override
					public boolean hasNext() {
						if (nextNodeCached!=null) {
							return true;
						}
						while (iteratorAux.hasNext()) {
							Node3D node=iteratorAux.next();
							if (node.isVisible()) {
								nextNodeCached=node;
								return true;
							}
						}
						nextNodeCached=null;
						return false;
					}

					@Override
					public Node3D next() {
						if (nextNodeCached==null) {
							throw new RuntimeException("No more nodes");
						} else {
							Node3D answer=nextNodeCached;
							nextNodeCached=null;
							hasNext(); // skip invisible nodes
							return answer;
						}
					}};
			}};
	}

	/**
	 * 
	 * @author Don Smith
	 *
	 */
	public class NeighborAtGraphDistance {
		final Node3D neighbor;
		final int graphDistance;
		public NeighborAtGraphDistance(Node3D node, int graphDistance) {
			this.neighbor=node;
			this.graphDistance=graphDistance;
		}
		public int getGraphDistance() {
			return graphDistance;
		}
		public Node3D getNeighbor() {
			return neighbor;
		}
		@Override
		public boolean equals(Object obj) {
			NeighborAtGraphDistance other=(NeighborAtGraphDistance) obj;
			return neighbor.equals(other.neighbor);
		}
		@Override
		public int hashCode() {
			return neighbor.hashCode();
		}
	}
	
	
	/**
	 * 
	 * @param graphDistance -- graph theoretic distance. Max graphDistance allowed is 7.  
	 * If graphDistance is above 7 it is treated as if it's 7. 
	 * If graphDistance is 0, the empty set is returned. If graphDistance is 1, the set of neighbors is returned.
	 * @param onlyIncludeVisible
	 * @return the set all of nodes (with their distances) within distance graphDistance of this node, excluding this node
	 */
	public Set<Node3D> getNeighborhood(int graphDistance, boolean onlyIncludeVisible) {
		Set<Node3D> set=new HashSet<>();
		addNeighborsAtDistance(set,graphDistance, onlyIncludeVisible);
		return set;
	}
	
	// Max distance considered is maxAllowedFocusDistance
	private void addNeighborsAtDistance(final Set<Node3D> set, final int distance, final boolean onlyIncludeVisible) {
		Set<Node3D> lastAdded= new HashSet<>();
		lastAdded.add(this);
		for(int i=0;i<distance;i++) {
			Set<Node3D> newLastAdded=new HashSet<>();
			for(Node3D n:lastAdded) {
				for(Node3D neighbor:n.getNeighbors()) {
					if (!onlyIncludeVisible || neighbor.isVisible()) {
						if (set.add(neighbor)) {
							newLastAdded.add(neighbor);
						}
					}
				}
			} // for Node3D n:lastAdded
			lastAdded=newLastAdded;
		} // for i
	}
	@Override
	public boolean equals(Object obj) {
		Node3D other= (Node3D)obj;
		return other.getId().equals(getId());
	}
	@Override
	public int hashCode() {return getId().hashCode();}
	public String getId() {return properties.get("id").toString();}
	
	public double edgeDistanceTo(Node3D other, Node3D[] nodes) {
		for(Node3D node: nodes) {
			node.distance=Double.MAX_VALUE;
		}
		this.distance=0.0;
		final PriorityQueue<Node3D> queue = new PriorityQueue<Node3D>();
		queue.add(this);
		return Node3D.edgeDistanceTo(queue, other);
	}

	@Override
	public int compareTo(Node3D other) {
		  return getId().compareTo(other.getId());
//		return Double.compare(other.importance, importance); // TODO
	}
	//-------------
	private static void show(Node3D ... nodes) {
		for(Node3D node: nodes) {
			System.out.print(node.getConnectedComponent() + "   ");
		}
		System.out.println();
	}
	
	public double getImportance() {
		return importance;
	}
	public void setImportance(double value) {
		importance=value;
	}
	
	private Node3D chooseNthNeighbor(int n) {
		int cnt=0;
		for(Node3D neighbor: edges.keySet()) {
			if (cnt==n) {
				return neighbor;
			}
			cnt++;
		}
		throw new IllegalStateException();
	}
	private Node3D chooseRandomNeighbor() {
		return chooseNthNeighbor(random.nextInt(edges.size()));
	}
	private static boolean contains(Node3D[] path,Node3D element, int length) {
		for(int i=0;i<length;i++) {
			if (path[i].equals(element)) {
				return true;
			}
		}
		return false;
	}
	
	private void randomWalk(int walkLengthLimit, int walkLengthSoFar, Node3D[] path) { 
		importance+=importanceDelta;
		if (edges.isEmpty()) {
			return;
		}
		if (contains(path,this,walkLengthSoFar)) {
			return;
		}
		if (walkLengthSoFar<walkLengthLimit) {
			path[walkLengthSoFar]=this;
			//path[walkLengthSoFar]=this;
			Node3D neighbor=chooseRandomNeighbor();
			neighbor.randomWalk(walkLengthLimit, 1+walkLengthSoFar, path);
		}
	}
	public static void computeImportanceViaDegree(Node3D[] nodes) {
		for(Node3D node:nodes) {
			node.setImportance(1+node.getEdges().size());
		}
	}
	public static void computeImportanceViaDegree(List<Node3D> nodes) {
		for(Node3D node:nodes) {
			node.setImportance(1+node.getEdges().size());
		}
	}
	public static void computeImportanceViaRandomWalks(final Node3D[] nodes,int walkLength, int numberOfWalksPerNode) {
		long startTime=System.currentTimeMillis();
		importanceDelta = 100.0/(nodes.length*numberOfWalksPerNode);
		System.out.println("importanceDelta = " + importanceDelta);
		final Node3D[] path=new Node3D[walkLength];
		for(Node3D node:nodes) {
			for(int walk=0;walk<numberOfWalksPerNode;walk++) {
				node.randomWalk(walkLength,0,path);
			}
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to computeImportanceViaRandomWalks");
	}
	
	public static void computeImportanceViaRandomWalks(List<Node3D> nodes,int walkLength, int numberOfWalksPerNode) {
		long startTime=System.currentTimeMillis();
		importanceDelta = 100.0/(nodes.size()*numberOfWalksPerNode);
		System.out.println("importanceDelta = " + importanceDelta);
		final Node3D[] path=new Node3D[walkLength];
		for(Node3D node:nodes) {
			for(int walk=0;walk<numberOfWalksPerNode;walk++) {
				node.randomWalk(walkLength,0,path);
			}
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to computeImportanceViaRandomWalks");
	}
	
	public static void computeImportanceViaJungBetweenessCentrality(List<Node3D> nodes) {
		long startTime=System.currentTimeMillis();
		Graph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		BetweennessCentrality<Node3D,Integer> ranker = new BetweennessCentrality<>(graph);
		for(Node3D node:nodes) {
			graph.addVertex(node);
		}
		int edgeCount=0;
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				graph.addEdge(new Integer(edgeCount),node,neighbor);
				edgeCount++;
			}
		}
		ranker.setRemoveRankScoresOnFinalize(false);
		ranker.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+ranker.getVertexRankScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's BetweennessCentrality");
	}
	public static void computeImportanceViaJungBetweenessCentrality(Node3D[] nodes) {
		long startTime=System.currentTimeMillis();
		Graph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		BetweennessCentrality<Node3D,Integer> ranker = new BetweennessCentrality<>(graph);
		for(Node3D node:nodes) {
			graph.addVertex(node);
		}
		int edgeCount=0;
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				graph.addEdge(new Integer(edgeCount),node,neighbor);
				edgeCount++;
			}
		}
		ranker.setRemoveRankScoresOnFinalize(false);
		ranker.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+ranker.getVertexRankScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's BetweennessCentrality");
	}
	
	

	public static void computeImportanceViaJungPageRank(List<Node3D> nodes) {
		long startTime=System.currentTimeMillis();
		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		PageRank<Node3D,Integer> scorer = new PageRank<>(graph,0.15);
		for(Node3D node:nodes) {
			graph.addVertex(node);
		}
		int edgeCount=0;
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				graph.addEdge(new Integer(edgeCount),node,neighbor);
				edgeCount++;
			}
		}
		scorer.initialize();
		scorer.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+scorer.getVertexScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's PageRank");
	}
	public static void computeImportanceViaJungPageRank(Node3D[] nodes) {
		long startTime=System.currentTimeMillis();
		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		PageRank<Node3D,Integer> scorer = new PageRank<>(graph,0.15);
		for(Node3D node:nodes) {
			graph.addVertex(node);
		}
		int edgeCount=0;
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				graph.addEdge(new Integer(edgeCount),node,neighbor);
				edgeCount++;
			}
		}
		scorer.initialize();
		scorer.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+scorer.getVertexScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's PageRank");
	}
	public static void computeImportanceViaJungMarkovCentrality(List<Node3D> nodes) {
//		long startTime=System.currentTimeMillis();
//		DirectedSparseGraph<Node3D,Integer> graph = new DirectedSparseGraph<>();
//		int k=5;
//		Set<Node3D> priors= new HashSet<>();
//		Map<Integer, Number> edgeWeights= new HashMap<>();
//				//new KStepMarkov<Nod3D,Integer>(graph,priors,k,edgeWeights);
//		for(Node3D node:nodes) {
//			graph.addVertex(node);
//		}
//		int edgeCount=0;
//		for(Node3D node:nodes) {
//			for(Node3D neighbor:node.getNeighbors()) {
//				graph.addEdge(new Integer(edgeCount),node,neighbor);
//				edgeCount++;
//			}
//		}
//		//markovCentralityScorer.setRemoveRankScoresOnFinalize(false);
//		markovCentralityScorer.evaluate(); // Causes NullPointerException
//	//	ranker.printRankings(true, true); 
//		for(Node3D node:nodes) {
//			node.setImportance(1+markovCentralityScorer.getVertexRankScore(node));
//			//System.out.println(node + " has importance " + node.getImportance());
//		}
//		double seconds = 0.001*(System.currentTimeMillis()-startTime);
//		System.out.println(seconds + " seconds to compute importance via Jung's MarkovCentrality");
	}
	public static void computeImportanceViaJungMarkovCentrality(Node3D[] nodes) {
		List<Node3D> list = new ArrayList<>();
		for(Node3D n:nodes) {
			list.add(n);
		}
		computeImportanceViaJungMarkovCentrality(list);
	}
//	public static void computeImportanceViaJungRandomWalkBetweenness(List<Node3D> nodes) {
//		long startTime=System.currentTimeMillis();
//		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
//		RandomWalkBetweenness<Node3D,Integer> scorer = new RandomWalkBetweenness<Node3D,Integer>(graph);
//		for(Node3D node:nodes) {
//			graph.addVertex(node);
//		}
//		int edgeCount=0;
//		for(Node3D node:nodes) {
//			for(Node3D neighbor:node.getNeighbors()) {
//				graph.addEdge(new Integer(edgeCount),node,neighbor);
//				edgeCount++;
//			}
//		}
//		scorer.evaluate();
//	//	ranker.printRankings(true, true); 
//		for(Node3D node:nodes) {
//			node.setImportance(1+scorer.getVertexRankScore(node));
//			//System.out.println(node + " has importance " + node.getImportance());
//		}
//		double seconds = 0.001*(System.currentTimeMillis()-startTime);
//		System.out.println(seconds + " seconds to compute importance via Jung's RandomWalkBetweenness");
//	}
	
	
	//WeightedNIPaths  requires rootSet
	public static void computeImportanceViaJungClosenessCentrality(List<Node3D> nodesList) {
		final Node3D[] nodes = new Node3D[nodesList.size()];
		nodesList.toArray(nodes);
		computeImportanceViaJungClosenessCentrality(nodes);
	}
	public static void computeImportanceViaJungClosenessCentrality(final Node3D[] nodes) {
		long startTime=System.currentTimeMillis();
		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		ClosenessCentrality<Node3D,Integer> scorer = new ClosenessCentrality<Node3D,Integer>(graph);
		for(Node3D node:nodes) {
			graph.addVertex(node);
		}
		int edgeCount=0;
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				graph.addEdge(new Integer(edgeCount),node,neighbor);
				edgeCount++;
			}
		}
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+scorer.getVertexScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's ClosenessCentrality");
	}
	
	//------------
    public static double c1=2.0;
    public static double c2=20.0;
    public static double c3=1.0;
    public static double c4=0.01;
   
    public static double limit(double d, double maxXYZ) {
    	if (d<0) {return 0;}
    	else if (d>maxXYZ) {return maxXYZ;}
    	return d;
    }
	
	public boolean isVisible() {
		return isVisible && indexInImportanceOrder < Visualizer.countToShow;
	}
	public void setVisible(boolean visible) {
		this.isVisible=visible;
	}
	public void setSphere(Sphere sphere) {
		this.sphere=sphere;
	}
	public Sphere getSphere() {
		return sphere;
	}
	public PhongMaterial getMaterial() {
		return (PhongMaterial) sphere.getMaterial();
	}
	public void setMaterial(PhongMaterial material) {
		sphere.setMaterial(material);
	}
	public void setXYZ(double d, double e, double f) {
		this.x=d;
		this.y=e;
		this.z=f;
	}
	private Point3D point3D=null;

	public Point3D getPoint3D() {
		if (point3D!=null && point3D.getX()==x && point3D.getY()==y && point3D.getZ()==z) {
			return point3D;
		}
		point3D = new Point3D(x,y,z);
		return point3D;
	}
	//--------------
		public static void main1(String [] args) {
			Node3D n1 = new Node3D("1","1");
			Node3D n2 = new Node3D("2","2");
			Node3D n3 = new Node3D("3","3");

			Node3D n4 = new Node3D("4","4");
			Node3D n5 = new Node3D("5","5");
			Node3D n6 = new Node3D("6","6");
			
			n1.addEdge(n2); show(n1,n2,n3, n4, n5, n6);
			n2.addEdge(n3); show(n1,n2,n3, n4, n5, n6);
			
			n4.addEdge(n5); show(n1,n2,n3, n4, n5, n6);
			n6.addEdge(n5); show(n1,n2,n3, n4, n5, n6);
			
			n1.addEdge(n6); show(n1,n2,n3, n4, n5, n6);
			
			System.out.println(ConnectedComponent.totalCount);
		}
		private String getProperty(String ... keys) {
			for(String key:keys) {
				Object value = properties.get(key);
				if (value!=null) {
					return value.toString();
				}
			}
			return null;
		}
		public String getDescription() {
			return getProperty("description","desc","descr","label");
		}


} // class Node3D
