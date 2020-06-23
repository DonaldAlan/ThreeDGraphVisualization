package grapher;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

/*
 *    The two points at the North Pole and the South Pole are shared by all lines of longitude
 */
public class GraphGenerators {
	private static Random random = new Random();
	public static Node3D[] makeSphere(int numberOfLinesOfLongitude, int numberOfLinesOfLatitude) {
		final Node3D[] nodes= new Node3D[2+numberOfLinesOfLongitude*numberOfLinesOfLatitude];
		final Node3D northPoll = new Node3D("0","north poll");
		final Node3D southPoll = new Node3D("1","south poll");
		nodes[0]=northPoll;
		nodes[1]=southPoll;
		int index=2;
		final Node3D[][] matrix = new Node3D[numberOfLinesOfLongitude][numberOfLinesOfLatitude];
		for(int lon=0;lon<numberOfLinesOfLongitude;lon++) {
			for(int lat=0;lat<numberOfLinesOfLatitude;lat++) {
				Node3D node = nodes[index]= new Node3D(""+index,"n_lon=" +lon + "_lat=" + lat);
				matrix[lon][lat]=node;
				index++;
			}
		}
		for(int lon=0;lon<numberOfLinesOfLongitude;lon++) {
			addEdges(northPoll,matrix[lon][0]);
			addEdges(southPoll,matrix[lon][numberOfLinesOfLatitude-1]);
			for(int lat=1;lat<numberOfLinesOfLatitude;lat++) {
				addEdges(matrix[lon][lat-1],matrix[lon][lat]);
			}
		}
		for(int lat=0;lat<numberOfLinesOfLatitude;lat++) {
			for(int lon=0;lon<numberOfLinesOfLongitude;lon++) {
				addEdges(matrix[lon][lat],matrix[(lon+1)%numberOfLinesOfLongitude][lat]);
			}
		}
		return nodes;
	}
	

	//---------------------------------
	//MixedRandomGraphGenerator
	private static Node3D[] createKleinbergSmallWorldViaJung() {
		//KleinbergSmallWorldGenerator<String,String> gen= new KleinbergSmallWorldGenerator();
		return null;
	}
	//--------------
	public static String toGML(Node3D[] nodes) {
		StringBuilder sb = new StringBuilder();
		sb.append("graph\n");
		sb.append("[\n");
		sb.append("  directed 0\n");
		for(Node3D node:nodes) {
			sb.append("   node\n");
			sb.append("   [\n");
			sb.append("      id " + node.getId() + "\n");
			sb.append("   ]\n");
		}
		for(Node3D node:nodes) {
			for(Node3D neighbor:node.getNeighbors()) {
				sb.append(" edge\n");
				sb.append(" [\n");
				sb.append("    source " + node.getId() + "\n");
				sb.append("    target " + neighbor.getId() + "\n");
				sb.append(" ]\n");
			}
		}
		sb.append("]\n");
		return sb.toString();
	}
	public static void writeGMLGraphToFile(Node3D[] graph, String path) throws IOException {
		PrintWriter printWriter = new PrintWriter(path);
		printWriter.print(toGML(graph));
		printWriter.close();
	}

	//----------------------------------------
	public static Node3D[] makeGraphWithThreeNodes_TwoWithEdge() {
		Node3D n1 = new Node3D("one");
		Node3D n2 = new Node3D("two");
		n1.addEdge(n2);
		n2.addEdge(n1);
		Node3D n3 = new Node3D("three");
		return new Node3D[] {n1,n2,n3};
	}
	//-----------------------
	public static Node3D[] makeGraphWithChokePoint(int leftCount, int rightCount, double probabilityOfEdge) {
		int n=leftCount+rightCount;
		Node3D[] nodes = new Node3D[n];
		makeComponent(nodes,0,leftCount,probabilityOfEdge);
		makeComponent(nodes,leftCount,n,probabilityOfEdge);
		nodes[0].addEdge(nodes[leftCount]);
		nodes[leftCount].addEdge(nodes[0]);		
		return nodes;
	}
	private static void addSquare(Node3D[] nodes, int startIndex) {
		nodes[startIndex+0]=new Node3D(""+startIndex+0);
		nodes[startIndex+1]=new Node3D(""+startIndex+1);
		nodes[startIndex+2]=new Node3D(""+startIndex+2);
		nodes[startIndex+3]=new Node3D(""+startIndex+3);
		addEdges(nodes[startIndex+0],nodes[startIndex+1]);
		addEdges(nodes[startIndex+1],nodes[startIndex+2]);
		addEdges(nodes[startIndex+2],nodes[startIndex+3]);
		addEdges(nodes[startIndex+3],nodes[startIndex+0]);
	}
	private static void addCube(Node3D[] nodes, int startIndex) {
		addSquare(nodes,startIndex);
		addSquare(nodes,startIndex+4);
		for(int i=startIndex;i<startIndex+4;i++) {
			addEdges(nodes[i],nodes[i+4]);
		}
	}
	private static void addHyperCube1(Node3D[] nodes, int startIndex) {
		addCube(nodes,startIndex);
		addCube(nodes,startIndex+8);
		for(int i=startIndex;i<startIndex+8;i++) {
			addEdges(nodes[i],nodes[i+8]);
		}
	}
	public static Node3D [] makeCube() {
		Node3D[] nodes = new Node3D[8];
		addCube(nodes,0);
		return nodes;
	}
	public static Node3D [] makeHyperCube1() {
		Node3D[] nodes = new Node3D[16];
		addHyperCube1(nodes,0);
		return nodes;
	}
	public static void addEdges(Node3D node1, Node3D node2) {
		node1.addEdge(node2);
		node2.addEdge(node1);
	}
	
	// All edges will be within [leftCount,...,rightCount)
	private static void makeComponent(Node3D[] nodes, int leftCount, int end, double probabilityOfEdge) {
		for(int i=leftCount;i<end;i++) {
			Node3D nodeI=new Node3D(""+i);
			nodes[i]=nodeI;
		}
		for(int i=leftCount+1;i<end;i++) {
			Node3D nodeI=nodes[i];
			for(int j=leftCount;j<i;j++) {
				if (Math.random()<probabilityOfEdge) {
					nodeI.addEdge(nodes[j]);
					nodes[j].addEdge(nodeI);
				}
			}
		}
	}
	public static Node3D[] makeComponents(int countOfClusters, int meanSize, double std, double probabilityOfEdge) {
		final List<Node3D> nodes= new ArrayList<>();
		for(int i=0;i<countOfClusters;i++) {
			final int size= Math.max(5,(int) Math.round(random.nextGaussian()*std + meanSize));
			int start=nodes.size();
			for(int j=0;j<size;j++) {
				Node3D node = new Node3D(""+ (start+j));
				nodes.add(node);
			}
			if (start>0) {
				nodes.get(start-1).addEdge(nodes.get(start));
				nodes.get(start).addEdge(nodes.get(start-1));
			}
			for(int j=start+1;j<start+size;j++) {
				for(int k=start;k<j;k++) {
					if (random.nextDouble()<probabilityOfEdge) {
						nodes.get(j).addEdge(nodes.get(k));
						nodes.get(k).addEdge(nodes.get(j));
					}
				}
			}
		}
		Node3D[] array = new Node3D[nodes.size()];
		nodes.toArray(array);
		return array;
	}
	public static Node3D[] makeGraph(final int n, final double probabilityOfEdgeAtDistanceOne, final boolean squareDistance) {
		final Node3D[] nodes = new Node3D[n];
		for(int i=0;i<n;i++) {
			nodes[i] = new Node3D("n"+i);
		}
		int max=1;
		for(int i=1;i<n;i++) {
			for(int j=0;j<i;j++) {
				int distance=i-j;
				double cutoff= squareDistance?  probabilityOfEdgeAtDistanceOne/(distance*distance) : probabilityOfEdgeAtDistanceOne/distance;
				if (random.nextDouble()<cutoff) {
					addEdges(nodes[i], nodes[j]);
					if (distance>max) {
						max=distance;
					}
				}
			}
		}
		System.out.println("Max diff = " + max);
		return nodes;
	}
	public static void writeToFile() {
		try {
			Node3D[] nodes =createKleinbergSmallWorldViaJung(); // makeSphere(5, 5);
			writeGMLGraphToFile(nodes, "d:/tmp/test.gml"); 
		} catch (Throwable thr) {
			thr.printStackTrace();
			System.exit(1);
		}
	}
	public static void main(String [] args) {
		for(double p=0.1;p<=3.9;p+= 0.3) {
			Node3D[] nodes = makeGraph(20,p,false);
					//createKleinbergSmallWorldGenerator(20, 20, p, false);
			System.out.println("Generated graph has " + nodes.length + " vertices and " + ReadGraphAndVisualize.countEdges(nodes) + " edges, clusteringCoefficient = " + 
			  ReadGraphAndVisualize.computeClusteringCoefficient(nodes));
//			nodes = makeGraph(20,p,true);
//			System.out.println("   " + ReadGraphAndVisualize.computeClusteringCoefficient(nodes));
		}
	}
}
