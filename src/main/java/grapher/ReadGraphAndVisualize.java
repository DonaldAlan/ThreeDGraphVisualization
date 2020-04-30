package grapher;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

import org.graphstream.stream.Sink;
import org.graphstream.stream.SinkAdapter;
import org.graphstream.stream.file.FileSource;
import org.graphstream.stream.file.FileSourceFactory;
import grapher.Visualizer.Layout;
import javafx.application.Application;
import javafx.geometry.Point3D;

/**
 * 
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 * Run ChooseGraphFileAndVisualize to get prompted for graph files to visualize.
 * 
 * See README.txt for more documentation. 
 * 
 */
public class ReadGraphAndVisualize {
	public static boolean readXYZ=false;
	private static NumberFormat numberFormat = NumberFormat.getInstance();
	// ----------
	static {
		numberFormat.setMaximumFractionDigits(2);
		numberFormat.setMinimumFractionDigits(2);
	}

	// ---------------
	private final Map<String, Node3D> mapFromNodeIdToNode = new HashMap<>();
	private final String path;

	public ReadGraphAndVisualize(String path) {
		this.path = path;
	}
	private void readInGraph() throws Exception {
		if (!new File(path).exists()) {
			System.err.println(path + " does not exist");
			System.exit(1);
		}
		if (path.endsWith("graphml")) {
			GraphMLReader.parseWithSax(path,mapFromNodeIdToNode);
			return;
		}
		FileSource source = FileSourceFactory.sourceFor(path);
		if (source==null) {
			System.err.println("Couldn't get source");
			System.exit(1);
		}
		System.out.println(source);
		Sink sink = new SinkAdapter() {
			@Override
			public void nodeAdded(String sourceId, long timeId, String nodeId) {
				//sourceId=<GML stream 1497198761276>, timeId = 1, nodeId= 1
				Node3D node = new Node3D(nodeId, nodeId);
				Node3D old = mapFromNodeIdToNode.put(nodeId, node);
				if (old != null) {
					throw new IllegalStateException("Multiple nodes for " + nodeId + ": " + old + " and " + node);
				}
			}

			@Override
			public void nodeAttributeAdded(String sourceId, long timeId, String nodeId, String attribute,
					Object value) {
				Node3D node = mapFromNodeIdToNode.get(nodeId);
				if (node!=null && value!=null) {
					if (readXYZ && attribute.equalsIgnoreCase("x")) {
						double x = getValueDouble(value);	
						if (x != Double.NaN) {
							Point3D point3D = node.getPoint3D();
							if (point3D == null) {
								point3D = new Point3D(x, Double.NaN,Double.NaN);
							} else {
								double y=point3D.getY();
								double z=point3D.getZ();
								node.setPoint3D(new Point3D(x,y,z));
							}
						}
					} else if (readXYZ && attribute.equalsIgnoreCase("y")) {
						double y = getValueDouble(value);	
						if (y != Double.NaN) {
							Point3D point3D = node.getPoint3D();
							if (point3D == null) {
								point3D = new Point3D(Double.NaN,y,Double.NaN);
							} else {
								double x=point3D.getX();
								double z=point3D.getZ();
								node.setPoint3D(new Point3D(x,y,z));
							}
						}
					} else if (readXYZ && attribute.equalsIgnoreCase("z")) {
						double z = getValueDouble(value);	
						if (z != Double.NaN) {
							Point3D point3D = node.getPoint3D();
							if (point3D == null) {
								point3D = new Point3D(Double.NaN,Double.NaN,z);
							} else {
								double x=point3D.getX();
								double y=point3D.getY();
								node.setPoint3D(new Point3D(x,y,z));
							}
						}
					} else {
						node.getAttributes().put(attribute, value);
					}
					if (node.getDescription().equals(node.getId())) {
						if (attribute.contains("label") || attribute.contains("descr")) {
							node.getAttributes().put("description", value);
						}
					}
				}
/*				
				// attribute is "ui.label"
				// System.out.println("Node attribute added, nodeId = " + nodeId
				// + ", value = " + value);
				String name = value.toString();
				System.out.println("new Node3D(" + nodeId + "," + name + ")");
				Node3D node = new Node3D(nodeId, name);
				Node3D old = mapFromNodeIdToNode.put(nodeId, node);
				if (old != null) {
					throw new IllegalStateException("Multiple nodes for " + nodeId + ": " + old + " and " + node);
				}
*/				
			}

			private double getValueDouble(Object value) {
				if (value instanceof Double) {
					Double d=(Double) value;
					return d.doubleValue();
				} else try {
					return Double.parseDouble(value.toString());
				} catch (NumberFormatException exc) {
				}
				return Double.NaN;
			}

			private Node3D lastFromNode;
			private Node3D lastToNode;

			@Override
			public void edgeAdded(String sourceId, long timeId, String edgeId, String fromNodeId, String toNodeId,
					boolean directed) {
				// System.out.println("Adding edge " + edgeId + " from " +
				// fromNodeId + " to " + toNodeId); // + ", steps = " +
				// springBox.getSteps()
				// Adding edge 1_0_0 from 1 to 0
				Node3D fromNode = mapFromNodeIdToNode.get(fromNodeId);
				if (fromNode == null) {
					throw new IllegalStateException("No node for " + fromNodeId);
				}
				lastFromNode = fromNode;
				Node3D toNode = mapFromNodeIdToNode.get(toNodeId);
				if (toNode == null) {
					throw new IllegalStateException("No node for " + toNodeId);
				}
				lastToNode = toNode;
				fromNode.addEdge(toNode, 1.0);
				toNode.addEdge(fromNode, 1.0);
			}

			@Override
			public void edgeAttributeAdded(String sourceId, long timeId, String edgeId, String attribute,
					Object value) {
				if (attribute.equals("value")) {
					// System.out.println("For edgeId " + edgeId + ", value = "
					// + value);
					double weight = Double.parseDouble(value.toString());
					lastFromNode.addEdge(lastToNode, weight);
					lastToNode.addEdge(lastFromNode, weight);
				}
			}
		};
		source.addSink(sink);
		source.readAll(path);
	}

	// --------------------------
	private static void dumpMatrix(double[][] matrix, String path) throws IOException {
		PrintWriter printWriter = new PrintWriter(path);
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				printWriter.print(matrix[i][j] + " ");
			}
			printWriter.println();
		}
		printWriter.close();
	}

	private void showAverageDistances(Node3D[] nodes, String path) throws IOException {
		PrintWriter printWriter = new PrintWriter(path);
		for (Node3D node : nodes) {
			double meanToNeighbors = node.meanXYZDistanceToNeighbors();
			double meanToNonNeighbors = node.meanXYZDistanceToNonNeighbors(nodes);
			printWriter.println(
					node + ", meanToNeighbors = " + meanToNeighbors + ", meanToNonNeighbors = " + meanToNonNeighbors);
		}
		printWriter.close();
	}

	//---------------------
	private static void standardizeImportances(final Node3D[] nodes, double newRange) {
		double sum=0.0;
		double min=Double.MAX_VALUE;
		double max=Double.NEGATIVE_INFINITY;
		Node3D maxNode=null;
		for(Node3D node: nodes) {
			double imp=node.getImportance();
			if (imp>max) {
				max=imp;
				maxNode=node;
			}
			if (imp<min) {
				min=imp;
			}
			sum+= imp;
		}
		double average=sum/nodes.length;
		System.out.println("Importance: min = " + min + ", mean = " + numberFormat.format(average)+ ", max = " + max 
				+ " (" + maxNode.getId() + ")");
		for(Node3D node: nodes) {
			double imp=newRange*(node.getImportance()/max);
			node.setImportance(imp);
		}
	}
	//-----------
	// For each node n, consider the set of all pairs (n1, n2) of neighbors of that node and compute the global proportion of
	// pairs of neighbors having n1 and n2 being neighbors of one another.
	//       
	public static double computeClusteringCoefficient(final Node3D[] nodes) {
		int countPairs=0;
		int countNeighborsConnected=0;
		for(Node3D n:nodes) {
			for(Node3D n1:n.getNeighbors()) {
				for(Node3D n2: n.getNeighbors()) {
					if (n1!=n2) {
						countPairs++;
						if (n1.getNeighbors().contains(n2)) {
							countNeighborsConnected++;
						}
					}
				}				
			}
		}
		return (0.0+countNeighborsConnected)/countPairs;
	}
	// ----------------
	public void readGraphAndVisualize() throws Exception {
		readInGraph();
		Node3D nodes[] = new Node3D[mapFromNodeIdToNode.size()];
		// ystem.exit(0);
		mapFromNodeIdToNode.values().toArray(nodes);
		processNodes(nodes, new File(path).getName());
	}
	public static int countEdges(Node3D[] nodes) {
		int count=0;
		for(Node3D node:nodes) {
			count+= node.getEdges().size();
		}
		return count;
	}
	public static void processNodes(Node3D[] nodes, String title) {
		int edgeCount=countEdges(nodes);
		System.out.println(
				nodes.length + " nodes, " + edgeCount + " edges, " + ConnectedComponent.totalCount + " connected components, "
		+ Node3D.maxDegree + " maxDegree, clusteringCoefficent = " + computeClusteringCoefficient(nodes));
		//...
		//Node3D.computeImportanceViaDegree(nodes);
		//Node3D.computeImportanceViaRandomWalks(nodes, 20, 100);
		//Node3D.computeImportanceViaJungBetweenessCentrality(nodes);
		//Node3D.computeImportanceViaJungPageRank(nodes); 
		//...
		//standardizeImportances(nodes,100);
		Visualizer.layout=Layout.Stochastic;
		ConnectedComponent.decayFactor=Math.min(0.99,0.3+3.0/(Math.log(nodes.length)));
		Node3D.c1=2;
		Node3D.c2=2;
		Node3D.c3=8;
		Node3D.c4=4.0/nodes.length;
		Visualizer.nodesToDisplay = nodes;
		if (nodes.length>=1000) {
			ConnectedComponent.approximateForces=true;
		}
		Node3D.windowSize=(int)Math.max(100*Math.pow(nodes.length,0.333),200);
		Visualizer.distanceForOneEdge = 0.2*Math.pow(Math.pow(Node3D.windowSize,3)/nodes.length,1.0/3.0);
		System.out.println("windowSize = " + Node3D.windowSize + ", distanceForOneEdge = " + Visualizer.distanceForOneEdge
				+ ", decayFactor = " + ConnectedComponent.decayFactor + ", " + nodes.length + " nodes");
		Visualizer.sphereRadius = 1; //10/Math.log(nodes.length);
		Visualizer.cylinderRadius = 0.1 * Visualizer.sphereRadius;
		Visualizer.title=title;
		Application.launch(Visualizer.class, new String[] {});
	}
}
