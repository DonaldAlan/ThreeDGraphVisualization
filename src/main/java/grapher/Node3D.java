package grapher;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import edu.uci.ics.jung.algorithms.importance.BetweennessCentrality;
import edu.uci.ics.jung.algorithms.importance.MarkovCentrality;
import edu.uci.ics.jung.algorithms.importance.RandomWalkBetweenness;
import edu.uci.ics.jung.algorithms.scoring.ClosenessCentrality;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import javafx.geometry.Point3D;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;

//-----------
public class Node3D implements Comparable<Node3D> {
	public static int windowSize=1000;
	private static final Random random = new Random();
	public static final NumberFormat numberFormat = NumberFormat.getInstance();
	private static double importanceDelta = 0.1;
	public static int maxDegree=0;
	//------------------------
	private Vector3 displacement= new Vector3(0,0,0); // Used in fruchtermanAndReingold() in ConnectedComponent.java
	private final String id;
	private final Map<Node3D,Double> edges = new HashMap<>(); // maps node to edge weight
	private final Map<String,Object> attributes= new TreeMap<>();
	private Point3D position;
	private ConnectedComponent connectedComponent;
	private double distance = Double.MAX_VALUE;
	private double importance=1.0;
	private int index;
	private boolean isVisible=true;
	private Sphere sphere=null;
	//-----------------------------------
	static {
		numberFormat.setMaximumFractionDigits(1);
	}
	public Node3D(String id) {
		this(id,id);
	}
	public Node3D(String id, String description) {
		this.id=id;
		attributes.put("description",description);
		connectedComponent=new ConnectedComponent(this);
		position = new Point3D(random.nextDouble()*windowSize,random.nextDouble()*windowSize,random.nextDouble()*windowSize);
	}
	
	public String getIdAndDescription() {
		String descr = getDescription();
		if (descr.equals(id)) {
			return id;
		} else {
			return id + ":"+ descr;
		}
	}
	public void setDescription(String descr) {
		attributes.put("description", descr);
	}
	public String getDescription() {
		String value= attributes.get("description").toString();
		if (value!=null) {
			return value;
		}
		value= attributes.get("label").toString();
		if (value!=null) {
			return value;
		}
		return id;
	}
	public Map<String,Object> getAttributes() {
		return attributes;
	}
	public int getIndexInImportanceOrder() {
		return index;
	}
	public void setIndexInImportanceOrder(int index) {
		this.index=index;
	}
	
	public Map<Node3D,Double> getEdges() {return edges;}
	public double distance(Node3D other) {
		return position.distance(other.position);
	}
	public ConnectedComponent getConnectedComponent() {return connectedComponent;}
	public Point3D getPoint3D() {return position;}
	public double getX() {return position.getX();}
	public double getY() {return position.getY();}
	public double getZ() {return position.getZ();}
	public void setPoint3D(Point3D p) {
		position=p;
	}
	public void addEdge(Node3D node) {addEdge(node,1.0);}
	public void addEdge(Node3D otherNode, double weight) {
		edges.put(otherNode,weight);
		if (edges.size()>maxDegree) {
			maxDegree = edges.size();
		}
		if (connectedComponent!=otherNode.connectedComponent) {
			int comp=connectedComponent.getFirst().compareTo(otherNode.connectedComponent.getFirst());
			if (comp==0) {
				throw new IllegalStateException();
			}
			// Tricky code below.
			if (comp <0) {
				// Merge otherNode into this node
				connectedComponent.merge(otherNode.connectedComponent);
				if (ConnectedComponent.totalCount==0) {
					System.err.println("totalCount==0 after adding edge " + otherNode + " to " + this);
				}
				for(Node3D n: otherNode.connectedComponent.getNodes()) {
					n.connectedComponent=connectedComponent;
				}
			} else {
				otherNode.connectedComponent.merge(connectedComponent);
				for(Node3D n: connectedComponent.getNodes()) {
					n.connectedComponent=otherNode.connectedComponent;
				}
			}
		}
	}
	private static double edgeDistanceTo(PriorityQueue<Node3D> queue, Node3D target) {
		while (!queue.isEmpty()) {
			Node3D next = queue.remove();
			if (next.equals(target)) {
				return next.distance;
			}
			for(Map.Entry<Node3D,Double> entry: next.edges.entrySet()) {
				Node3D node = entry.getKey();
				double weight = entry.getValue();
				double newDistance = next.distance + weight;
				if (newDistance< node.distance) {
					node.distance=newDistance;
					queue.add(node);
				}
			}
		}
		return Double.MAX_VALUE;
	}

	private static double square(double x) {return x*x;}
	public double xyzDistanceTo(Node3D other) {
		return position.distance(other.position);
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
	public double meanXYZDistanceToNonNeighbors(Node3D[] nodesToDisplay) {
		Set<Node3D> neighbors = edges.keySet();
		int count=0;
		double sum=0.0;
		for(Node3D other: nodesToDisplay) {
			if (!neighbors.contains(other)) {
				sum+= xyzDistanceTo(other);
				count++;
			}
		}
		return sum/count;
	}
	public Set<Node3D> getNeighbors() {return edges.keySet();}
	
	public Iterable<Node3D> getVisibileNeighbors() {
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
							if (node.isVisible) {
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
	 * @param graphDistance -- graph theoretic distance. Max graphDistance allowed is 5.  If graphDistance is above 4 it is treated as if it's 5. 
	 * If graphDistance is 0, the empty set is returned. If graphDistance is 1, the set of neighbors is returned.
	 * @return the set all of nodes (with their distances) within distance graphDistance of this node.
	 */
	public Set<NeighborAtGraphDistance> getNeighborhood(int graphDistance) {
		Set<NeighborAtGraphDistance> set=new HashSet<>();
		addNeighborsAtDistance(set,graphDistance);
		return set;
	}
	
	// Max distance considered is 5
	private void addNeighborsAtDistance(Set<NeighborAtGraphDistance> set, int distance) {
		if (distance>=1) {
			for(Node3D n1: getNeighbors()) {
				set.add(new NeighborAtGraphDistance(n1,1));
				if (distance>=2) {
					for(Node3D n2:n1.getNeighbors()) {
						set.add(new NeighborAtGraphDistance(n2, 2));
						if (distance>=3) {
							for(Node3D n3: n2.getNeighbors()) {
								set.add(new NeighborAtGraphDistance(n3, 3));
								if (distance>=4) {
									for(Node3D n4: n3.getNeighbors()) {
										set.add(new NeighborAtGraphDistance(n4, 4));
										if (distance>=5) {
											for(Node3D n5:n4.getNeighbors()) {
												set.add(new NeighborAtGraphDistance(n5, 5));
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	@Override
	public String toString() {
		return getIdAndDescription(); //":c" + connectedComponent.getFirst().id + "@" + this.position;
	}
	@Override
	public boolean equals(Object obj) {
		Node3D other= (Node3D)obj;
		return other.id.equals(id);
	}
	@Override
	public int hashCode() {return id.hashCode();}
	public String getId() {return id;}
	
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
		return id.compareTo(other.id);
	}
	//-------------
	private static void show(Node3D ... nodes) {
		for(Node3D node: nodes) {
			System.out.print(node.getConnectedComponent() + "   ");
		}
		System.out.println();
	}
	//--------------
	public static void main(String [] args) {
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
	public static void computeImportanceViaRandomWalks(Node3D[] nodes,int walkLength, int numberOfWalksPerNode) {
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
	public static void computeImportanceViaJungMarkovCentrality(Node3D[] nodes) {
		long startTime=System.currentTimeMillis();
		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		RandomWalkBetweenness<Node3D,Integer> scorer = new RandomWalkBetweenness<Node3D,Integer>(graph);
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
		scorer.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+scorer.getVertexRankScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's MarkovCentrality");
	}
	public static void computeImportanceViaJungRandomWalkBetweenness(Node3D[] nodes) {
		long startTime=System.currentTimeMillis();
		UndirectedSparseGraph<Node3D,Integer> graph = new UndirectedSparseGraph<>();
		RandomWalkBetweenness<Node3D,Integer> scorer = new RandomWalkBetweenness<Node3D,Integer>(graph);
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
		scorer.evaluate();
	//	ranker.printRankings(true, true); 
		for(Node3D node:nodes) {
			node.setImportance(1+scorer.getVertexRankScore(node));
			//System.out.println(node + " has importance " + node.getImportance());
		}
		double seconds = 0.001*(System.currentTimeMillis()-startTime);
		System.out.println(seconds + " seconds to compute importance via Jung's RandomWalkBetweenness");
	}
	
	//WeightedNIPaths  requires rootSet
	public static void computeImportanceViaJungClosenessCentrality(Node3D[] nodes) {
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
    public void computeSpringForceAndMove(double maxXYZ) { // From https://cs.brown.edu/~rt/gdhandbook/chapters/force-directed.pdf
            final double x=getX();
            final double y=getY();
            final double z=getZ();
            double xf=0;
            double yf=0;
            double zf=0;
            for(Node3D v:connectedComponent.getNodes()) {
                    if (v==this) {
                    	continue;
                    }
                    double deltaX=v.getX()-x;
                    double deltaY=v.getY()-y;
                    double deltaZ=v.getZ()-z;
                    double d = Math.sqrt(square(deltaX) + square(deltaY) + square(deltaZ));
                    if (d==0.0) {
                    		v.setPoint3D(new Point3D(x+random.nextDouble()-0.5, y+random.nextDouble()-0.5, z+random.nextDouble()-0.5));
                    		System.out.print("r");
                    		  deltaX=v.getX()-x;
                              deltaY=v.getY()-y;
                              deltaZ=v.getZ()-z;
                              d = Math.sqrt(square(deltaX) + square(deltaY) + square(deltaZ));
                    }
                    double force=0.0;
                    if (getNeighbors().contains(v)) {
                            force = c1*Math.log(d/c2);
                            //System.out.println("force1 = " + force + ", d = " + d);
                    } 
                    force += -c3/square(d);
                    //System.out.println("force2 = " + force + ", d = " + d);
                    xf +=  force*(deltaX);
                    yf +=  force*(deltaY);
                    zf +=  force*(deltaZ);
            }
            //System.out.println("xf = " + xf + ", yf = " + yf + ", zf = " + zf + "       x = " + x + ", y = " + y + ", z = " + z);
            setPoint3D(new Point3D(limit(x+c4*xf, maxXYZ),limit(y+c4*yf, maxXYZ),limit(z+c4*zf, maxXYZ)));
    }
    public static double limit(double d, double maxXYZ) {
    	if (d<0) {return 0;}
    	else if (d>maxXYZ) {return maxXYZ;}
    	return d;
    }
	public void setDisplacement(double x, double y, double z) {
		displacement.setX(x);
		displacement.setY(y);
		displacement.setZ(z);
	}
	public Vector3 getDisplacement() {
		return displacement; 
	}
	public void addNearbyNodes(final Set<Node3D> nearNodes,final int maxDistance) {
		nearNodes.add(this);
		if (maxDistance>0) {
			for(Node3D neighbor:getNeighbors()) {
				neighbor.addNearbyNodes(nearNodes, maxDistance-1);
			}
		}
	}
	public void setIsVisible(boolean b) {
		isVisible=b;
	}
	public boolean isVisible() {
		return isVisible;
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
	public void randomizePlacement() {
		position = new Point3D(random.nextDouble()*windowSize,random.nextDouble()*windowSize,random.nextDouble()*windowSize);
	}
} // class Node3D