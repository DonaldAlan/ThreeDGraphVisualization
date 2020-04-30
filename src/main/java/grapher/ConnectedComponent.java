package grapher;

import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.text.NumberFormat;
import java.util.Random;
import java.util.Set;

import javafx.geometry.Point3D;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

/**
 * 
 * @author Don Smith, ThinkerFeeler@gmail.com
 *
 * See README.txt for more documentation. 
 *  
 */
public class ConnectedComponent {
	public static int secondsToWaitPerIteration = 10;
	public static int stochasticMovesReps=14;  // Raising this will result in prettier graphs, at the cost of slower execution. TODO: slider, etc.
	public static int numberOfSamplesForApproximation=1000; // The higher, the more approximate the forces using ApproximateForces.
	public static boolean approximateForces=false; // Renders much faster with this true, but graphs don't look as good.
	public static boolean repulsiveDenonimatorIsSquared=true;
	public static double decayFactor=0.9;
	public static boolean trace=false;
	public int springRep=100;
	public static int SEPARATION=5; // minimum separation between components in the 3D space
	public static int totalCount=0;
	//......
	private static final Random random = new Random();
	private final TreeSet<Node3D> nodes = new TreeSet<>();
	private Node3D[] nodesToDisplayThisCC=null;
	private double extentOfInitialGridPlacement;
	private static final long startMilliseconds = System.currentTimeMillis();
	// minX==Double.MAX_VALUE when we need to recompute
	double minX=Double.MAX_VALUE,maxX=Double.NEGATIVE_INFINITY;
	double minY=Double.MAX_VALUE,maxY=Double.NEGATIVE_INFINITY;
	double minZ=Double.MAX_VALUE,maxZ=Double.NEGATIVE_INFINITY;
	private static final NumberFormat numberFormat = NumberFormat.getInstance();
	static {
		numberFormat.setMinimumFractionDigits(1);
		numberFormat.setMaximumFractionDigits(1);
	}
	public ConnectedComponent(Node3D node) {
		nodes.add(node);
		totalCount++;
	}
	public Node3D getFirst() {
		return nodes.first();
	}
	private void debug(String message) {
		System.out.println(numberFormat.format(0.001*(System.currentTimeMillis() - startMilliseconds)) + ": " + message);
	}
	public Point3D computeCentroid() {
		double x=0;
		double y=0;
		double z=0;
		for(Node3D node: nodes) {
			x+= node.getX();
			y+= node.getY();
			z+= node.getZ();
		}
		int n=nodes.size();
		return new Point3D(x/n,y/n,z/n);
	}
	public TreeSet<Node3D> getNodes() {
		return nodes;
	}
	@Override
	public String toString() {
		return nodes.toString() + " with minX=" + minX + ", maxX="+ maxX 
				+ ", minY=" + minY + ", maxY="+maxY
				+ ", minZ=" + minZ + ", maxZ=" + maxZ;
	}
	public void merge(ConnectedComponent other) {
		if (other.equals(this)) {
			throw new IllegalStateException();
		}
		if (totalCount==1) {
			System.err.println("totalCount== " + totalCount + ", merging \n  " + nodes + "\n and\n  " + other.nodes);
		}
		nodes.addAll(other.nodes);
		totalCount--;
		minX=Double.MAX_VALUE; // Signal that we need to recompute this
	}
	public int size() {
		return nodes.size();
	}
	// Return cost of using point3D for node i
	private double getCost(int i, Point3D point3d, Node3D nodeI) {
		return approximateForces? getCostNeighborhoodOnly(i,point3d, nodeI) : getCostExpensive(i, point3d, nodeI);
	}
	// Each run takes O(n*d*d) where n is the number of vertices in the connected component and d is the max degree of the vertex and its neighbors.
	private double getCostExpensive(int i, Point3D point3d, Node3D nodeI) {
		double sumCosts = 0;
		for (int j = 0; j < nodesToDisplayThisCC.length; j++) {
			if (i != j) {
				Node3D nodeJ = nodesToDisplayThisCC[j];
				if (!nodeJ.isVisible()) { // When focusing on the neighborhood of a single vertex, ignore invisible vertices (not in the neighborhood).
					continue;
				}
				double distance = point3d.distance(nodeJ.getPoint3D());
				if (distance<Visualizer.distanceForOneEdge) { // Huge repulsive force prevents crowding: edges closer than Visualizer.distanceForOneEdge in Euclidean space. 
					return Double.MAX_VALUE;
				}
				sumCosts+= 1/(repulsiveDenonimatorIsSquared ? (distance*distance) : distance); // repulsive force between all pairs of vertices
				if (nodeI.getEdges().containsKey(nodeJ) || nodeJ.getEdges().containsKey(nodeI)) {
					sumCosts+= /*nodeI.getImportance()*/ square(distance-Visualizer.distanceForOneEdge);   // Spring force between edge-connected vertices (attractive or repuslive). 
					// Consider neighbors "third" at graph distance 2.
					for(Node3D third: nodeJ.getNeighbors()) {
						if (third!=nodeI && third.isVisible()) {
							distance=point3d.distance(third.getPoint3D());
							if (distance<Visualizer.distanceForOneEdge) { // Prevent crowding at graph-distance 2.
								return Double.MAX_VALUE;
							}
							sumCosts+= square(distance-Math.sqrt(2)*Visualizer.distanceForOneEdge)/2; // Spring force between vertices at graph-distance 2.
						}
					}
				}
			}
		}
		return sumCosts;
	}
	// Return cost of using point3D for node i, using attractive and repulsive forces only between neighbors and neighbors of neighbors.
	// O(d*d) where d is the max degree of the vertex and its neighbors.
	// It also adds repulsive forces for a sample of all nodes.
	private double getCostNeighborhoodOnly(int i, Point3D point3d, Node3D nodeI) {
		double sumCosts=0;
		for(Node3D nodeJ: nodeI.getNeighbors()) {
			if (!nodeJ.isVisible()) {
				continue;
			}
			double distance = point3d.distance(nodeJ.getPoint3D());
			if (distance<Visualizer.distanceForOneEdge) { // Prevent crowding
				return Double.MAX_VALUE;
			}
			// Spring for direct neighbor
			sumCosts+= /*nodeI.getImportance()*/ square(distance-Visualizer.distanceForOneEdge);
			// Now look at neighbors "third" of that neighbor
			for(Node3D third: nodeJ.getNeighbors()) {
				if (third!=nodeI && third.isVisible()) {
					distance=point3d.distance(third.getPoint3D());
					if (distance<Visualizer.distanceForOneEdge) { // Prevent crowding
						return Double.MAX_VALUE;
					}
					// Spring for neighbor at graph distance 2
					sumCosts+= square(distance-Math.sqrt(2)*Visualizer.distanceForOneEdge)/2;
				}
			}
		}
		int increment=nodesToDisplayThisCC.length/numberOfSamplesForApproximation;
		if (increment==0) {
			increment=1;
		}
		// Because of increment, we only sample nodes
		for (int j = 0; j < nodesToDisplayThisCC.length; j+=increment) {
			if (i != j  && j==-1) {
				Node3D nodeJ = nodesToDisplayThisCC[j];
				if (!nodeJ.isVisible()) { // When focusing on the neighborhood of a single vertex, ignore invisible vertices (not in the neighborhood).
					continue;
				}
				if (!nodeI.getNeighbors().contains(nodeJ) && ! nodeJ.getNeighbors().contains(nodeI)) {
					double distance = point3d.distance(nodeJ.getPoint3D());
					if (distance<Visualizer.distanceForOneEdge) { // Huge repulsive force prevents crowding: edges closer than Visualizer.distanceForOneEdge in Euclidean space. 
						return Double.MAX_VALUE;
					}
					sumCosts+= 1/(repulsiveDenonimatorIsSquared ? (distance*distance) : distance); // repulsive force between all pairs of vertices
				}
			}
		}
		return sumCosts;
	}
	private static double square(final double d) {return d*d;}
	
	private static Color randomColor() {
		return Color.rgb(random.nextInt(256),random.nextInt(256), random.nextInt(256),1);
	}
	private void placeOne() {
		nodesToDisplayThisCC[0].setPoint3D(new Point3D(0,0,0));
		getMaxWidthHeightDepth();
	}
	private void placeTwo() {
		nodesToDisplayThisCC[0].setPoint3D(new Point3D(0,0,0));
		nodesToDisplayThisCC[1].setPoint3D(new Point3D(Visualizer.distanceForOneEdge,0,0));
		getMaxWidthHeightDepth();
	}
	private void placeThree() {
		nodesToDisplayThisCC[0].setPoint3D(new Point3D(0,0,0));
		nodesToDisplayThisCC[1].setPoint3D(new Point3D(Visualizer.distanceForOneEdge,0,0));
		if (random.nextBoolean()) {
			nodesToDisplayThisCC[2].setPoint3D(new Point3D(0,Visualizer.distanceForOneEdge,0));
		} else {
			nodesToDisplayThisCC[2].setPoint3D(new Point3D(0,0,Visualizer.distanceForOneEdge));
		}
		getMaxWidthHeightDepth();
	}
	// Uses less memory than placeRandomMovesSequential but requires higher count, because it doesn't remember the deltaVector.
	private void placeRandomMovesSequentialNew(double distance, int count) {	// 11.533
		for(int i=0;i<nodesToDisplayThisCC.length;i++) {
			Node3D node = nodesToDisplayThisCC[i];
			Point3D best=node.getPoint3D();
			double bestCost=getCost(i, node.getPoint3D(), node);
			//long start=System.currentTimeMillis();
			for(int j=0;j<count;j++) {
				Point3D p = randomVectorOfLengthDistancePlusPoint(distance,node.getPoint3D()); //randomUnitVector().multiply(distance);
				double cost=getCost(i,p, node);
				if (cost<bestCost) {
					bestCost=cost;
					best=p;
				}
			}
			node.setPoint3D(best);
			// Takes 1 mls on average when you use UnrestrictedForces
			//if (i==0) {System.out.println(System.currentTimeMillis()-start + " mls");}
		}
	}
	private void placeRandomMoves(double distance, int count) {
		debug("Before placeRandomMovesInParallel");
	    placeRandomMovesInParallel(distance, count);
	    debug("After placeRandomMovesInParallel");
	    placeRandomMovesSequential(distance,count/4); // Do it sequentially since the parallel version may err due to two connected nodes moving at the same time.
	}
	// Uses more memory, but needs smaller count, because it multiplies deltaVector by -1
	private void placeRandomMovesSequential(double distance, int count) {	//20.84
		for(int i=0;i<nodesToDisplayThisCC.length;i++) {
			Node3D node = nodesToDisplayThisCC[i];
			if (!node.isVisible()) {
				continue;
			}
			Point3D best=node.getPoint3D();
			double bestCost=getCost(i, node.getPoint3D(), node);
			//long start=System.currentTimeMillis();
			for(int j=0;j<count;j++) {
				Point3D deltaVector = randomUnitVector().multiply(distance);
				Point3D p  = node.getPoint3D().add(deltaVector);
				double cost=getCost(i,p, node);
				if (cost<bestCost) {
					bestCost=cost;
					best=p;
				} else {
					p=node.getPoint3D().add(deltaVector.multiply(-1)); 
					cost=getCost(i,p,node);
					if (cost<bestCost) {
						bestCost=cost;
						best=p;
					} 
				}
			}
			node.setPoint3D(best);
			// Takes 1 mls on average when you use UnrestrictedForces
			//if (i==0) {System.out.println(System.currentTimeMillis()-start + " mls");}
		}
	}

	private static Point3D randomVectorOfLengthDistancePlusPoint(double distance, Point3D point) {
		double x=random.nextDouble()-0.5;
		double y=random.nextDouble()-0.5;
		double z=random.nextDouble()-0.5;
		double factor=distance/Math.sqrt(x*x+y*y+z*z);
		return new Point3D(x*factor + point.getX(),y*factor+point.getY(),z*factor+point.getZ());
	}
	private static Point3D randomVectorOfLengthDistance(double distance) {
		double x=random.nextDouble()-0.5;
		double y=random.nextDouble()-0.5;
		double z=random.nextDouble()-0.5;
		double factor=distance/Math.sqrt(x*x+y*y+z*z);
		return new Point3D(x*factor,y*factor,z*factor);
	}

	private void placeRandomMovesInParallel(final double distance, final int count) {	
		final ExecutorService executorService=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()); 
		final CountDownLatch latch = new CountDownLatch(nodesToDisplayThisCC.length);
		for(int i=0;i<nodesToDisplayThisCC.length;i++) {
			final Node3D node = nodesToDisplayThisCC[i];
			if (!node.isVisible()) {
				continue;
			}
			final Point3D startPoint=node.getPoint3D();
			final Point3D[] best={node.getPoint3D()};
			final double [] bestCost={getCost(i, node.getPoint3D(), node)};
			final int ii=i;
			Runnable runnable = new Runnable(){
				@Override
				public void run() {
					for(int j=0;j<count;j++) {
						Point3D deltaVector = randomUnitVector().multiply(distance);
						Point3D newPoint  = startPoint.add(deltaVector);
						double cost=getCost(ii,newPoint, node);
						synchronized (startPoint) {
							if (cost<bestCost[0]) {
								bestCost[0]=cost;
								best[0]=newPoint;
							}
						}
						// TODO: synchronize? It seems to work without synchronizing.
						node.setPoint3D(best[0]);
						latch.countDown();
					}};
			};
			executorService.execute(runnable);
		}
		try {
			latch.await(secondsToWaitPerIteration, TimeUnit.SECONDS);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		executorService.shutdown();
		try {
			executorService.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.err.println("Warning: interrupted executorService");
		}
	}
	//--------------------------------------------
	private void permuteNodes() {
		for(int i=0;i<nodesToDisplayThisCC.length;i++) {
			int index=random.nextInt(nodesToDisplayThisCC.length);
			if (i!=index) {
				Node3D node1=nodesToDisplayThisCC[i];
				Node3D node2=nodesToDisplayThisCC[index];
				nodesToDisplayThisCC[i]=node2;
				nodesToDisplayThisCC[index]=node1;
			}
		}
	}
	//--------------------------------------------
	public void randomizeColors(int mergeCount) {
		for(Node3D node:nodes) {
			if (!node.isVisible()) {
				continue;
			}
			PhongMaterial material = new PhongMaterial(randomColor());
			node.setMaterial(material);
		}
		for(int i=0;i<mergeCount;i++) {mergeNearbyColors();}
	}
	//--------------------------------------------
	public void mergeNearbyColors() {
		for(Node3D node:nodes) {
			if (!node.isVisible()) {
				continue;
			}
			Color oldColor=node.getMaterial().getDiffuseColor();
			double components[]=new double[3];
			components[0]=oldColor.getRed();
			components[1]=oldColor.getGreen();
			components[2]=oldColor.getBlue();
			for(Node3D neighbor:node.getNeighbors()) {
				if (!neighbor.isVisible()) {
					continue;
				}
				Color color=neighbor.getMaterial().getDiffuseColor();
				components[0] += color.getRed();
				components[1] += color.getGreen();
				components[2] += color.getBlue();
			}
			int cnt=1+node.getNeighbors().size();
			components[0]/=cnt;
			components[1]/=cnt;
			components[2]/=cnt;
			Color newColor = Color.color(components[0],components[1],components[2]);
			PhongMaterial material=new PhongMaterial(newColor);
			node.setMaterial(material); 
		}
	}
	//--------------------------------------------
	private void placeInitiallyInGrid() {
		//double volumeNeeded = nodes.size()*Math.pow(PlotCylindersAndSpheres.distanceForOneEdge,3);
		int cubeRootOfSize = (int) (Math.pow(nodes.size(), 0.333333))+2;
		if (cubeRootOfSize<=1){
			cubeRootOfSize=2;
		}
		int x=0;
		int y=0;
		int z=0;
		double d=1.5*Visualizer.distanceForOneEdge;
		for(Node3D node:nodes) {
			node.setPoint3D(new Point3D(x*d,y*d,z*d));
			x++;
			if (x==cubeRootOfSize) {
				x=0;
				y++;
				if (y==cubeRootOfSize) {
					y=0;
					z++;
					if (z==cubeRootOfSize) {
						System.err.println("WARN: Unexpected: z==" + cubeRootOfSize);
					}
				}
			}
		}
		minX=Double.MAX_VALUE;
		extentOfInitialGridPlacement=cubeRootOfSize*Visualizer.distanceForOneEdge;
	}
	private static Point3D randomUnitVector() {
		return new Point3D(ThreadLocalRandom.current().nextDouble()-0.5,ThreadLocalRandom.current().nextDouble()-0.5,ThreadLocalRandom.current().nextDouble()-0.5).normalize();
	}
	// Returns  max of width, height, and depth,  and sets minX, minY, minY, maxY, and minZ and maxZ
	public double getMaxWidthHeightDepth() {
		minX=Double.MAX_VALUE; maxX=Double.NEGATIVE_INFINITY;
		minY=Double.MAX_VALUE; maxY=Double.NEGATIVE_INFINITY;
		minZ=Double.MAX_VALUE; maxZ=Double.NEGATIVE_INFINITY;
		for(Node3D node:nodes) {
			double x=node.getX();
			double y=node.getY();
			double z=node.getZ();
			if (x<minX) {minX=x;}
			if (x>maxX) {maxX=x;}
			if (y<minY) {minY=y;}
			if (y>maxY) {maxY=y;}
			if (z<minZ) {minZ=z;}
			if (z>maxZ) {maxZ=z;}
		}
		double width=maxX-minX;
		double height=maxY-minY;
		double depth=maxZ-minZ;
		return Math.max(width, Math.max(height, depth)); 
	}
	public void shift(double deltaX, double deltaY, double deltaZ) {
		Point3D centroid=computeCentroid();
		for(Node3D node: nodesToDisplayThisCC) {
			node.setPoint3D(new Point3D(
					node.getX()-centroid.getX()+deltaX, 
					node.getY()-centroid.getY()+deltaY,
					node.getZ()-centroid.getZ()+deltaZ));
		}
		getMaxWidthHeightDepth();
	}
	public void done() {
		nodesToDisplayThisCC=new Node3D[nodes.size()];
		nodes.toArray(nodesToDisplayThisCC);
		//debug(nodes.size() + " nodes in "+ this.getFirst());
		placeInitiallyInGrid();
	}

//	@Deprecated // doesn't work
//	public void placeUsingBarrycenterDeprecated() {
////		final Set<Node3D> fixed = chooseEightPairwiseNodesWithNoEdgesBetween();
////		System.out.println("Chose " + fixed.size() + " fixed nodes");
////		if (fixed.size() < 3) {
////			placeOnePassUsingSpringModel();
////			return;
////		}
//		if (nodesToDisplay.length <= 8) {
//			placeOnePassUsingSpringModel();
//		return;
//		}
//		final Set<Node3D> fixed = chooseEightRandomNodes();
//		placeEdgesAtCornersOfHyperCube(fixed);
//		Point3D center = new Point3D(extentOfInitialGridPlacement/2,extentOfInitialGridPlacement/2,extentOfInitialGridPlacement/2);
//		for(Node3D node:nodesToDisplay) {
//			if (!fixed.contains(node)) {
//				node.setPoint3D(center);
//			}
//		}
//		int reps = 0;
//		boolean changed = true;
//		while (changed) {
//			changed = false;
//			reps++;
//			for (Node3D node : nodesToDisplay) {
//				if (!fixed.contains(node) && node.getNeighbors().size() > 0) {
//					int n = node.getNeighbors().size();
//					double sumX = 0.0;
//					double sumY = 0.0;
//					double sumZ = 0.0;
//					for (Node3D neighbor : node.getNeighbors()) {
//						sumX += neighbor.getX();
//						sumY += neighbor.getY();
//						sumZ += neighbor.getZ();
//					}
//					double newX = sumX / n;
//					double newY = sumY / n;
//					double newZ = sumZ / n;
//					if (differ(node.getX(), newX) || differ(node.getY(), newY) || differ(node.getZ(), newZ)) {
//						node.setPoint3D(new Point3D(newX, newY, newZ));
//						changed = true;
//					}
//				}
//			}
//		} //
//		System.out.println(reps + " reps to place with Barrycenter");
//	}
	
	
	private static boolean differ(double x, double y) {
		return Math.abs(x-y)>0.1;
	}
	public void placeOnePassUsingSpringModel() {	
		double maxXYZ = Math.pow(nodesToDisplayThisCC.length,1.0/3.0)*Visualizer.distanceForOneEdge;
		for(int i=0;i<springRep;i++) {
			for(Node3D node:nodesToDisplayThisCC) {
				if (node.isVisible()) {
					node.computeSpringForceAndMove(maxXYZ);
				}
			}
		}
	}
	//--------------
	private static final double attractive(double d, double k) {
		return (d*d)/k;
	}
	//--------------
	private static final double repulsive(double d, double k) {
		//return (k*k)/(d*(d<1?1: Math.log(d)));
		return (k*k)/(d);  // d*d?    d?
	}

	//---------------
		public void fruchtermanAndReingold() {
			if (nodesToDisplayThisCC.length<=1) {
				return;
			}
			final double volume=Math.pow(Node3D.windowSize,3);
			final double C=1.0;
			final double k = C*Math.pow(volume/nodesToDisplayThisCC.length, 0.33333) ;
			double maxXYZ = Node3D.windowSize;
			
			for(Node3D node: nodesToDisplayThisCC) {
				node.setPoint3D(new Point3D(maxXYZ*random.nextDouble(), maxXYZ*random.nextDouble(), maxXYZ*random.nextDouble()));
			}
		
			//System.out.println("area = " + area + ", k = " + k);
			Vector3 delta = new Vector3(0,0,0);
			final int n=nodesToDisplayThisCC.length;
			double startTemperature=0.5*maxXYZ;
			System.out.println("maxXYZ= " + maxXYZ + " for " + nodesToDisplayThisCC.length  + " stochasticDecayFactor = " + decayFactor
					+ " vertices, startTemperature = " + startTemperature + ", k = " + toString(k) + "\n"); 
			int iteration=0;
			for(double temperature=startTemperature;temperature>0.1;temperature*=decayFactor) {
				iteration++;
				// Attractive forces are between vertices connected by an edge.  f_a(d) = d*d/k.
				// All pairs of vertices have repulsive forces.                  f_r(d) = -k*k/d;
				for(int i=0;i<n;i++) {
					Node3D v = nodesToDisplayThisCC[i];
					if (!v.isVisible()) {
						continue;
					}
					v.setDisplacement(0,0,0);
					for(int j=0;j<n;j++) {
						if (i==j) {
							continue;
						}
						Node3D u=nodesToDisplayThisCC[j];
						//Calculate repulsive forces
						delta.setToMinus(v.getPoint3D(),u.getPoint3D());
						double lengthDelta = delta.length();
						boolean edge= u.getNeighbors().contains(v);
						if (trace) {
							System.out.print("v = " + toString(v.getPoint3D()) + v.getDisplacement() + ", u = " + toString(u.getPoint3D()) +
									", delta=" + delta + ", |delta| = " + toString(lengthDelta) + (edge? " (EDGE)":"(NO EDGE"));
						}
						if (lengthDelta==0.0) {
							double newDistance = //PlotCylindersAndSpheres.distanceForOneEdge*random.nextDouble() +
									(edge?  Visualizer.distanceForOneEdge:  4*Visualizer.distanceForOneEdge);
							Point3D directionVector=randomNormalizedDirectionVector();
							Point3D newPoint3D = u.getPoint3D().add(directionVector.multiply(newDistance));
							//System.out.println(" ZERO!!! Moving u : " + toString(u.getPoint3D()) + " --> " + toString(newPoint3D) + " at iteration " + iteration);
							System.out.print('z');
							u.setPoint3D(newPoint3D);
							continue;
						}
						delta.multiply(repulsive(lengthDelta,k)/lengthDelta);
						v.getDisplacement().add(delta);
						if (trace) {
							System.out.println(", repulsiveDelt= = " + delta + " with length " + toString(delta.length()) + ", new v displacement = " + v.getDisplacement());
						}
						// Calculate attractive forces
						if (i<j && edge) {
							delta.setToMinus(v.getPoint3D(),u.getPoint3D());
							delta.multiply(attractive(lengthDelta, k)/lengthDelta);
							v.getDisplacement().substract(delta);
							u.getDisplacement().add(delta);
							if (trace) {
								System.out.println("Due to edge between v and u delta2= " + delta + ", |delta| = " + toString(delta.length())
								+ ", new v displ = " + v.getDisplacement() + ", new u displ = " + u.getDisplacement() + "\n");
							}
						}
					} // for j
					if (trace) {System.out.println();}
				} // for i
				// Limit max displacement to temperature and prevent going outside frame
				for(Node3D v: nodesToDisplayThisCC) {
					if (!v.isVisible()) {
						continue;
					}
					Vector3 displ = v.getDisplacement();
					//System.out.print(displ + "  ");
					double length=displ.length();
					displ.multiply(Math.min(length, temperature)/length);
					displ.add(v.getX(),v.getY(),v.getZ());
					double x=Node3D.limit(displ.getX(),maxXYZ);
					double y=Node3D.limit(displ.getY(),maxXYZ);
					double z=Node3D.limit(displ.getZ(),maxXYZ);
					Point3D point = new Point3D(x,y,z);
					//System.out.println(point);
					v.setPoint3D(point);
				}
				//System.out.println();
				//break;
			}
			System.out.println("k = " + k + ", maxXYZ = " + toString(maxXYZ) + ", iterations = " + iteration);
		}
	private static String toString(Point3D p) {
		return "(" + toString(p.getX()) + ", " + toString(p.getY()) + ", " + toString(p.getZ()) + ")";
	}
		public static String toString(double d) {
			return Node3D.numberFormat.format(d);
		}
	private static Point3D randomNormalizedDirectionVector() {
		return new Point3D(random.nextDouble()-0.5,random.nextDouble()-0.5, random.nextDouble()-0.5).normalize(); 
	}
	public void placeOnePassUsingStocasticMoves() {		
		if (nodesToDisplayThisCC==null) {
			done();		
		}
		if (nodes.size()==1) {
			placeOne();
			return;
		}
		if (nodes.size()==2) {
			placeTwo();
			return;
		}
		if (nodes.size()==3) {
			placeThree();
			return;
		}
		debug("Starting placeOnePassUsingStocasticMoves");
		double start=getMaxWidthHeightDepth();
		//System.out.print("Placing with start = " + start + " and reps = " + reps + ": ");
		int iteration=0;
		for(double d=start;d>1; d=d*decayFactor) {
			permuteNodes();
			debug("d = " + d + ", stochasticMovesReps = " + stochasticMovesReps);
			placeRandomMoves(d,stochasticMovesReps);
			iteration++;
		}
		debug(iteration + " iterations");
		getMaxWidthHeightDepth();
	}
	public boolean intersects(ConnectedComponent other) {
		if (minX==Double.MAX_VALUE) {
			getMaxWidthHeightDepth();
		}
		if (  minX-SEPARATION>=other.maxX || other.minX-SEPARATION>=maxX // We add -SEPARATION so that components are separated from each other
			||minY-SEPARATION>=other.maxY || other.minY-SEPARATION>=maxY
			||minZ-SEPARATION>=other.maxZ || other.minZ-SEPARATION>=maxZ) {
			//System.out.println(this + " doesn't intersect \n" + other + "\n");
			return false;
		} else {
			return true;
		}
	}
	private Set<Node3D> chooseEightRandomNodes() {
		Set<Node3D> result = new TreeSet<>();
		while (result.size()<8) {
			Node3D node = nodesToDisplayThisCC[random.nextInt(nodesToDisplayThisCC.length)];
			result.add(node);
		}
		return result;
	}
	private Set<Node3D> chooseEightPairwiseNodesWithNoEdgesBetween() {
		Set<Node3D> result = new TreeSet<>();
		for(int i=0;i<8;i++) {
			Node3D node = chooseNodeWithNoEdgeTo(result);
			if (node==null) {
				break;
			}
			result.add(node);
		}
		return result;
	}
	private Node3D chooseNodeWithNoEdgeTo(Set<Node3D> result) {
		for(Node3D node:nodesToDisplayThisCC) {
			if (!result.contains(node)&& !hasEdgeTo(node,result)) {
				return node;
			}
		}
		return null;
	}
	private static boolean hasEdgeTo(Node3D node,Set<Node3D> nodes) {
		for(Node3D other:nodes) {
			if (node.getNeighbors().contains(other)) {
				return true;
			}
		}
		return false;
	}
	

	private void placeEdgesAtCornersOfHyperCube(Set<Node3D> fixed) {
		double x=0;
		double y=0;
		double z=0;
		boolean reachedEnd=false;
		for(Node3D node3D: fixed) {
			node3D.setPoint3D(new Point3D(x,y,z));
			System.out.println(node3D.getPoint3D()); 
			if (z==0) {
				z=extentOfInitialGridPlacement;
			} else {
				z=0;
				if (y==0) {
					y=extentOfInitialGridPlacement;
				} else {
					y=0;
					if (x==0) {
						x=extentOfInitialGridPlacement;
					} else {
						if (reachedEnd) {
							throw new IllegalStateException();
						}
						reachedEnd=true;
					}
				}
			}
		}
	}
	//------------
	public static void main(String [] args) {
		Node3D.windowSize=100;
		Node3D n1=new Node3D("n1","n1");
		Node3D n2=new Node3D("n2","n2");
		Node3D n3=new Node3D("n3","n3");
		Node3D n4=new Node3D("n4","n4");
	//	Node3D n5=new Node3D("n5","n5");
		n1.addEdge(n2); n2.addEdge(n1);
		n2.addEdge(n3); n3.addEdge(n2);
		n3.addEdge(n4); n4.addEdge(n3);
		n4.addEdge(n1); n1.addEdge(n4);
		//n4.addEdge(n5); n5.addEdge(n4);
		//n5.addEdge(n1); n1.addEdge(n5);
		ConnectedComponent cc = n1.getConnectedComponent();
		cc.done();
		trace=false;
		cc.fruchtermanAndReingold();
	//	cc.placeOnePassUsingSpringModel();
		for(Node3D node1:cc.nodesToDisplayThisCC) {
			System.out.print(node1.getId() + " " + toString(node1.getPoint3D()) + ": ");
			for(Node3D node2:node1.getNeighbors()) {
				System.out.print(toString(node1.distance(node2))+ " ");
			}
			System.out.println();
		}
	}
}
