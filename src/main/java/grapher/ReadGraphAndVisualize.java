package grapher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
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
	private String findSeparator(String path) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		int countEdges=0;
		int countSpace=0;
		int countComma=0;
		int countTab=0;
		while (countEdges<100) {
			String line=reader.readLine();
			if (line==null) {
				break;
			}
			if (line.startsWith("#") || line.startsWith("%")) {
				System.out.println(line);
			} else if (line.length()==0) {
			} else {
				countEdges++;
				int cTab = line.split("\t").length;
				if (cTab==2 || cTab==3) {
					countTab++;
				} else {
					int cComma = line.split(",").length;
					if (cComma==2 || cComma==3) {
						countComma++;
					} else {
						int cSpace = line.split(" ").length;
						if (cSpace==2 || cSpace == 3) {
							countSpace++;
						}
					}
				}
			}
		}
		reader.close();
		System.out.println("countSpace = " + countSpace + ", countTab = " + countTab + ", countComma = " + countComma);
		if (countSpace> countComma && countSpace>countTab) {
			return " ";
		} else if (countComma> countSpace && countComma> countTab) {
			return ",";
		} else if (countTab> countSpace && countTab> countComma) {
			return "\t";
		}
		throw new RuntimeException("Unable to find separator in file " + path);
	}
	private void readInGraphFromTextFileWithNodeNodeWeightForEdge(String path) throws IOException {
		final String separator = findSeparator(path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		int lineNumber=0;
		int countEdges=0;
		while (true) {
			String line=reader.readLine();
			if (line==null) {
				break;
			}
			lineNumber++;
			if (line.startsWith("#") || line.startsWith("%")) {
				System.out.println(line);
			} else if (line.length()==0) {
			} else {
				String parts[]=line.split(separator);
				if (parts.length<2 || parts.length>3) {
					parts = line.split(",");
					if (parts.length<2 || parts.length>3) {
						System.err.println("Ignoring line " + line);
						continue;
					}
				}
				String n1=parts[0];
				String n2=parts[1];
				double weight=1.0;
				if (parts.length>2) {
					try {
						weight=Double.parseDouble(parts[2]);
					} catch (NumberFormatException exc) {
						System.err.println("Couldn't parse weight at lineNumber " + lineNumber + " in " + line);
						System.err.println("(" + parts[2] + ")");
						System.exit(1);
					}
				}
				Node3D node1 = mapFromNodeIdToNode.get(n1);
				if (node1==null) {
					node1=new Node3D(n1);
					mapFromNodeIdToNode.put(n1, node1);
				}
				Node3D node2 = mapFromNodeIdToNode.get(n2);
				if (node2==null) {
					node2=new Node3D(n2);
					mapFromNodeIdToNode.put(n2, node2);
				}
				Edge edge= node1.addEdge(node2,weight);
				node2.addEdge(node1,edge);
				countEdges++;
			}
		}
		System.out.println("Processed " + lineNumber + " lines, generating "
				+ mapFromNodeIdToNode.size() + " nodes and " + countEdges + " edges");
		reader.close();
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
		if (path.endsWith("edges") || path.endsWith("mtx") || path.endsWith("txt")) {
			readInGraphFromTextFileWithNodeNodeWeightForEdge(path);
			return;
		}
		FileSource source = FileSourceFactory.sourceFor(path);
		if (source==null) {
			System.err.println("ReadGraphAndVisualize: Couldn't get source from " + path);
			System.exit(1);
		}
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
								node.setXYZ(x,y,z);
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
								node.setXYZ(x,y,z);
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
								node.setXYZ(x,y,z);
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
				Edge edge =fromNode.addEdge(toNode);
				toNode.addEdge(fromNode, edge);
			}

			@Override
			public void edgeAttributeAdded(String sourceId, long timeId, String edgeId, String attribute,
					Object value) {
				Edge edge = lastFromNode.getEdges().get(lastToNode);
				if (edge!=null) {
					edge.addProperty(attribute, value);
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
			double meanToNonNeighbors = node.meanXYZDistanceToNonNeighbors();
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
				nodes.length + " nodes, " + edgeCount + " edges, "
		+ Node3D.maxDegree + " maxDegree, clusteringCoefficent = " + computeClusteringCoefficient(nodes));
		//...
		Visualizer.setSavedAllNodes(nodes);
		Node3D.windowSize=(int)Math.max(100*Math.pow(nodes.length,0.333),200);
		Visualizer.distanceForOneEdge = 0.2*Math.pow(Math.pow(Node3D.windowSize,3)/nodes.length,1.0/3.0);
		Visualizer.sphereRadius = 1; //10/Math.log(nodes.length);
		Visualizer.cylinderRadius = 0.1 * Visualizer.sphereRadius;
		Visualizer.title=title;
		Application.launch(Visualizer.class, new String[] {});
	}
}
