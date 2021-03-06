package grapher;

import java.util.TreeSet;

import java.util.concurrent.ThreadLocalRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
//import mdsj.MDSJ;

/**
 * 
 * @author Don Smith, ThinkerFeeler@gmail.com
 *
 *         To keep nodes separated, we place them ONLY on a 3d grid at regular
 *         intervals. During relaxation, move a node to a position if that
 *         lowers the cost.
 * 
 *         See README.txt for more documentation.
 * 
 */
public class ConnectedComponent {
	public double positionFactor = 20;
	public int innerReps = 20;
	private final List<Node3D> nodes = new ArrayList<>(); // These are always visible!
	private Node3D[][][] nodeMatrix;
	private int numberOfNodesToShow;
	public double simpleDecayFactor = 0.75;
	public static int secondsToWaitPerIterationPer1000 = 6;
	public static int stochasticMovesReps = 14; // Raising this will result in prettier graphs, at the cost of slower
												// execution. TODO: slider, etc.
	public static int numberOfSamplesForApproximation = 1000; // The higher, the more approximate the forces using
																// ApproximateForces.
	public static boolean repulsiveDenonimatorIsSquared = true;
	public static double decayFactorForFruchtermanAndReingold = 0.95; // TODO: make this controllable from UI
	public static boolean trace = false;

	public static int totalCount = 0;
	// ......
	private static final Random random = new Random();

	// minX==Double.MAX_VALUE when we need to recompute
	double minX = Double.MAX_VALUE, maxX = Double.NEGATIVE_INFINITY;
	double minY = Double.MAX_VALUE, maxY = Double.NEGATIVE_INFINITY;
	double minZ = Double.MAX_VALUE, maxZ = Double.NEGATIVE_INFINITY;
	private Group group = null;
	private static final NumberFormat numberFormat = NumberFormat.getInstance();
	static {
		numberFormat.setMinimumFractionDigits(1);
		numberFormat.setMaximumFractionDigits(1);
	}

	public ConnectedComponent(Node3D node) {
		nodes.add(node);
		totalCount++;
	}
	public void done() {
		int m = 4 * (int) Math.ceil(Math.pow(nodes.size(), 0.33333));
		if (nodes.size()>10) {
			System.out.println("m = " + m + ", m*m*m = " + (m*m*m) + ", nodes.size() = " + nodes.size());
		}
		nodeMatrix = new Node3D[m][m][m];
		numberOfNodesToShow = nodes.size();
		placeRandomlyInGrid(m);
	}
	public Node3D getFirst() {
		return nodes.get(0);
	}

	public double getCost() {
		double cost = 0.0;
		for (Node3D node : nodes) {
			cost += node.getCost(this);
		}
		return cost;
	}

	// --------------------------------------------
	private void placeRandomlyInGrid(final int m) {
		for (Node3D node : nodes) {
			while (true) {
				int x = random.nextInt(m);
				int y = random.nextInt(m);
				int z = random.nextInt(m);
				if (nodeMatrix[x][y][z] == null) {
					nodeMatrix[x][y][z] = node;
					node.setIndices(x, y, z);
					node.setXYZ(positionFactor * x, positionFactor * y, positionFactor * z);
					break;
				}
			}
		}
	}

	// --------------------------------------------
	private void placeInitiallyInGrid(final int m) {
		// double volumeNeeded =
		// nodes.size()*Math.pow(PlotCylindersAndSpheres.distanceForOneEdge,3);
		int cubeRootOfSize = (int) (Math.pow(nodes.size(), 0.333333)) + 1;
		if (cubeRootOfSize <= 1) {
			cubeRootOfSize = 2;
		}
		int nodeIndex = 0;
		for (int x = 0; x < m && nodeIndex < numberOfNodesToShow; x += 2) {
			for (int y = 0; y < m && nodeIndex < numberOfNodesToShow; y += 2) {
				for (int z = 0; z < m && nodeIndex < numberOfNodesToShow; z += 2) {
					Node3D node = nodes.get(nodeIndex);
					nodeIndex++;
					nodeMatrix[x][y][z] = node;
					node.setIndices(x, y, z);
					node.setXYZ(positionFactor * x, positionFactor * y, positionFactor * z);
				}
			}
		}
		minX = Double.MAX_VALUE;
		System.out.println("Placed initially on grid");
	}
	public void randomizePositions(double probabilityOfMoving) {
		for (Node3D node : nodes) {
			if (random.nextDouble() < probabilityOfMoving) {
				int count = 0;
				while (true) {
					int x = random.nextInt(nodeMatrix.length);
					int y = random.nextInt(nodeMatrix.length);
					int z = random.nextInt(nodeMatrix.length);
					if (nodeMatrix[x][y][z] == null) {
						nodeMatrix[x][y][z] = node;
						nodeMatrix[node.getXIndex()][node.getYIndex()][node.getZIndex()] = null;
						node.setIndices(x, y, z);
						node.setXYZ(positionFactor * x, positionFactor * y, positionFactor * z);
						break;
					}
					count++;
					if (count == 30) {
						System.err.println("Couldn't find a move");
						break;
					}
				}
			}
		}
	}

	private int moveRandom(int index, int delta) {
		int v = index + random.nextInt(delta + delta) - delta;
		if (v < 0) {
			v = 0;
		}
		if (v >= nodeMatrix.length) {
			v = nodeMatrix.length - 1;
		}
		return v;
	}

	public void springModelBackground() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				int limit = 1 + nodes.size() / 100;
				for (int i = 0; i < limit; i++) {
					stochasticModel();
				}
			}
		};
		new Thread(runnable).start();
	}

	public void stochasticModel() {
		int iteration = 0;
		final long startTime = System.currentTimeMillis();
		final double secondsToWaitPerIteration = Math.max(0.002, secondsToWaitPerIterationPer1000*nodes.size()/1000.0);
		do {
			if (stochasticModelAuxRandomMoves(iteration) == 0) {
				break;
			}
			iteration++;
		} while (System.currentTimeMillis() - startTime < secondsToWaitPerIteration * 1000);
		if (nodes.size() > 200 && iteration > 2) {
			double seconds = 0.001 * (System.currentTimeMillis() - startTime);
			System.out.println("Exited stochasticModel() after " + iteration + " iterations in "
					+ numberFormat.format(seconds) + " seconds");
		}
	}



	public int stochasticModelAuxRandomMoves(int iteration) {
		int lessCount = 0;
		int delta = nodeMatrix.length / 2;
		while (delta > 0) {
			for (int i = 0; i < numberOfNodesToShow; i++) {
				final Node3D node = nodes.get(i);
				double cost = node.getCostIndex(this);
				for (int j = 0; j < innerReps; j++) {
					final int rx = moveRandom(node.getXIndex(), delta);
					final int ry = moveRandom(node.getYIndex(), delta);
					final int rz = moveRandom(node.getZIndex(), delta);
					Node3D nodeRxRyRz = nodeMatrix[rx][ry][rz];
					if (nodeRxRyRz == node) {
						continue;
					} else if (nodeRxRyRz == null) {
						double rc = node.getCostIfWeWereAtIndex(this,rx, ry, rz);
						if (rc < cost) {
							lessCount++;
							cost = rc;
							nodeMatrix[node.getXIndex()][node.getYIndex()][node.getZIndex()] = null;
							node.setIndices(rx, ry, rz);
							nodeMatrix[rx][ry][rz] = node;
						}
					} else { // See if swapping lowers cost.
						final double startCostNodeRxRyRz = nodeRxRyRz.getCostIndex(this);
						final double swappedCostNodeRxRyRz = nodeRxRyRz.getCostIfWeWereAtIndex(this,
								node.getXIndex(),node.getYIndex(),node.getZIndex());
						double rc = node.getCostIfWeWereAtIndex(this,rx, ry, rz);
						if (rc + swappedCostNodeRxRyRz < cost + startCostNodeRxRyRz) {
							final int nodeIndexX = node.getXIndex();
							final int nodeIndexY = node.getYIndex();
							final int nodeIndexZ = node.getZIndex();
							lessCount++;
							cost = rc;
							nodeMatrix[node.getXIndex()][node.getYIndex()][node.getZIndex()] = nodeRxRyRz;
							nodeMatrix[rx][ry][rz] = node;
							node.setIndices(rx, ry, rz);
							nodeRxRyRz.setIndices(nodeIndexX, nodeIndexY, nodeIndexZ);
						}
					}
				}
			} // for
//			if (nodes.size()>1000) {
//				System.out.println(lessCount + " updates for iteration " +iteration);
//			}		
			delta--;
		} // while
		for(Node3D node:nodes) {
			node.setXYZ(positionFactor*node.getXIndex(), positionFactor*node.getYIndex(), positionFactor*node.getZIndex());
		}
		return lessCount;
	}

	public void systematicModel() {
		int iteration = 0;
		final long startTime = System.currentTimeMillis();
		final double secondsToWaitPerIteration = Math.max(0.002, secondsToWaitPerIterationPer1000*nodes.size()/1000.0);
		do {
			if (systematicModelAux(iteration) == 0) {
				break;
			}
			iteration++;
		} while (System.currentTimeMillis() - startTime < secondsToWaitPerIteration * 1000);
		if (nodes.size() > 500 && iteration > 2) {
			double seconds = 0.001 * (System.currentTimeMillis() - startTime);
			System.out.println("Exited systematicModel() after " + iteration + " iterations, in "
					+ numberFormat.format(seconds) + " seconds");
		}
	}

	private int systematicModelAux(int iteration) {
		int lessCount = 0;
		int delta = nodeMatrix.length / 2;
		final int maxIndexMinus1 = nodeMatrix.length - 1;
		while (delta > 0) {
			for (int i = 0; i < numberOfNodesToShow; i++) {
				final Node3D node = nodes.get(i);
				int nodeIndexX = node.getXIndex();
				int nodeIndexY = node.getYIndex();
				int nodeIndexZ = node.getZIndex();
				double cost = node.getCost(this);
				for (int nx = Math.max(0, nodeIndexX - delta); nx <= Math.min(nodeIndexX + delta,
						maxIndexMinus1); nx++) {
					final int deltaXAbs = Math.abs(nodeIndexX - nx);
					final int limitY = delta - deltaXAbs;
					for (int ny = Math.max(0, nodeIndexY - limitY); ny <= Math.min(nodeIndexY + limitY,
							maxIndexMinus1); ny++) {
						final int deltaYAbs = Math.abs(nodeIndexY - ny);
						final int limitZ = delta - deltaXAbs - deltaYAbs;
						for (int nz = Math.max(nodeIndexZ - limitZ, 0); nz <= Math.min(nodeIndexZ + limitZ,
								maxIndexMinus1); nz++) {
							final int deltaZAbs = Math.abs(nodeIndexZ - nz);
							int diff = deltaXAbs + deltaYAbs + deltaZAbs;
							if (diff != delta) {
								continue;
							}
							Node3D nodeRxRyRz = nodeMatrix[nx][ny][nz];
							if (nodeRxRyRz == node) {
								continue;
							} else if (nodeRxRyRz == null) {
								double rc = node.getCostIfWeWereAtXYZ(this,positionFactor * nx, positionFactor * ny, positionFactor * nz);
								if (rc < cost) {
									lessCount++;
									cost = rc;
									nodeMatrix[node.getXIndex()][node.getYIndex()][node.getZIndex()] = null;
									node.setXYZ(positionFactor * nx, positionFactor * ny, positionFactor * nz);
									node.setIndices(nx, ny, nz);
									nodeMatrix[nx][ny][nz] = node;
									nodeIndexX = nx;
									nodeIndexY = ny;
									nodeIndexZ = nz;
								}
							} else { // See if swapping lowers cost.
								final double startCostNodeRxRyRz = nodeRxRyRz.getCost(this);
								final double swappedCostNodeRxRyRz = nodeRxRyRz.getCostIfWeWereAtXYZ(this,
										positionFactor * node.getXIndex(), positionFactor * node.getYIndex(), positionFactor * node.getZIndex());
								double rc = node.getCostIfWeWereAtXYZ(this,positionFactor * nx, positionFactor * ny, positionFactor * nz);
								if (rc + swappedCostNodeRxRyRz < cost + startCostNodeRxRyRz) {
									lessCount++;
									cost = rc;
									nodeMatrix[node.getXIndex()][node.getYIndex()][node.getZIndex()] = nodeRxRyRz;
									nodeMatrix[nx][ny][nz] = node;
									node.setXYZ(positionFactor * nx, positionFactor * ny, positionFactor * nz);
									node.setIndices(nx, ny, nz);
									nodeRxRyRz.setXYZ(positionFactor * nodeIndexX, positionFactor * nodeIndexY, positionFactor * nodeIndexZ);
									nodeRxRyRz.setIndices(nodeIndexX, nodeIndexY, nodeIndexZ);
									nodeIndexX = nx;
									nodeIndexY = ny;
									nodeIndexZ = nz;
								}
							}
						}
					}
				}
			} // for
			if (nodes.size() > 10) {
//				System.out.println(lessCount + " updates for iteration " +iteration);
			}
			delta--;
		} // while
		return lessCount;
	}

	// ----------------------
	public void springModel() {
		// TODO: add some repulsive forces, so that there's good separation between
		// subclusters.
		final long startTime = System.currentTimeMillis();
		long lastCheckTime = startTime;
		double cost = getCost();
		final int limit = 1 + nodes.size() / 10;
		final double secondsToWaitPerIteration = Math.max(0.002, secondsToWaitPerIterationPer1000*nodes.size()/1000.0);
		for (int i = 0; i < limit; i++) {
			final long now = System.currentTimeMillis();
			if (now - startTime > 1000 * secondsToWaitPerIteration) {
				System.out.println("Timed out after " + numberFormat.format(0.001 * (now - startTime)) + " seconds");
				break;
			}
			if (now - lastCheckTime > 1000) {
				double localCost = getCost();
				if (localCost >= cost) {
					System.out.println(
							"Cost increased after " + numberFormat.format(0.001 * (now - startTime)) + " seconds");
					break;
				}
				cost = localCost;
				lastCheckTime = now;
			}
			for (Node3D node : nodes) {
				final int nodeXIndex=node.getXIndex();
				final int nodeYIndex=node.getYIndex();
				final int nodeZIndex=node.getZIndex();
				int xIndexSum = 0;
				int yIndexSum = 0;
				int zIndexSum = 0;
				int count = 0;
				for (Node3D n : node.getNeighbors()) {
					if (n.isVisible()) {
						xIndexSum += n.getXIndex();
						yIndexSum += n.getYIndex();
						zIndexSum += n.getZIndex();
						count++;
					}
				}
				final int centerX = limitForIndex((int) Math.round((0.0 + xIndexSum) / count));
				final int centerY = limitForIndex((int) Math.round((0.0 + yIndexSum) / count));
				final int centerZ = limitForIndex((int) Math.round((0.0 + zIndexSum) / count));
				label: for (int delta = 1; delta < 8; delta++) {
					for (int x = Math.max(0, centerX - delta); x <= limitForIndex(centerX + delta); x++) {
						for (int y = Math.max(0, centerY - delta); y <= limitForIndex(centerY + delta); y++) {
							for (int z = Math.max(0, centerZ - delta); z <= limitForIndex(centerZ + delta); z++) {
								final Node3D otherNode = nodeMatrix[x][y][z];
								if (otherNode == null) {
									nodeMatrix[x][y][z] = node;
									nodeMatrix[nodeXIndex][nodeYIndex][nodeZIndex] = null;
									node.setIndices(x, y, z);
									node.setXYZ(positionFactor * x, positionFactor * y, positionFactor * z);
									break label;
								} else if (!node.getEdges().containsKey(otherNode)){
									nodeMatrix[x][y][z]=node;
									nodeMatrix[nodeXIndex][nodeYIndex][nodeZIndex]=otherNode;
									node.setIndices(x,y,z);
									otherNode.setIndices(nodeXIndex, nodeYIndex, nodeZIndex);
									node.setXYZ(positionFactor*x,positionFactor*y,positionFactor*z);
									otherNode.setXYZ(positionFactor*nodeXIndex,positionFactor*nodeYIndex,positionFactor*nodeZIndex);
									break label;
								}
							}
						}
					}
				}
			}
		}
		if (nodes.size() > 100) {
			System.out.println("Exited spring Model");
		}
	}

	// ----------------------
	public Point3D computeCentroid() {
		double x = 0;
		double y = 0;
		double z = 0;
		for (Node3D node : nodes) {
			x += node.getX();
			y += node.getY();
			z += node.getZ();
		}
		int n = nodes.size();
		return new Point3D(x / n, y / n, z / n);
	}

	public List<Node3D> getNodes() {
		return nodes;
	}

	@Override
	public String toString() {
		return nodes.toString() + " with minX=" + minX + ", maxX=" + maxX + ", minY=" + minY + ", maxY=" + maxY
				+ ", minZ=" + minZ + ", maxZ=" + maxZ;
	}

	public void merge(ConnectedComponent other) {
		if (other.equals(this)) {
			throw new IllegalStateException();
		}
		if (totalCount == 1) {
			System.err.println("totalCount== " + totalCount + ", merging \n  " + nodes + "\n and\n  " + other.nodes);
		}
		nodes.addAll(other.nodes);
		totalCount--;
		minX = Double.MAX_VALUE; // Signal that we need to recompute this
	}

	public int size() {
		return nodes.size();
	}

	private static double square(final double d) {
		return d * d;
	}

	private static Color randomColor() {
		return Color.hsb(360.0*random.nextDouble(), random.nextDouble(), 1.0);
		//return Color.rgb(64+random.nextInt(128), 128+random.nextInt(128), 128+random.nextInt(128), 0.8);
	}

	private static Point3D randomVectorOfLengthDistancePlusPoint(double distance, Point3D point) {
		double x = random.nextDouble() - 0.5;
		double y = random.nextDouble() - 0.5;
		double z = random.nextDouble() - 0.5;
		double factor = distance / Math.sqrt(x * x + y * y + z * z);
		return new Point3D(x * factor + point.getX(), y * factor + point.getY(), z * factor + point.getZ());
	}

	private static Point3D randomVectorOfLengthDistance(double distance) {
		double x = random.nextDouble() - 0.5;
		double y = random.nextDouble() - 0.5;
		double z = random.nextDouble() - 0.5;
		double factor = distance / Math.sqrt(x * x + y * y + z * z);
		return new Point3D(x * factor, y * factor, z * factor);
	}

	// --------------------------------------------
	private void permuteNodes() {
		final int n = nodes.size();
		for (int i = 0; i < n; i++) {
			int index = random.nextInt(n);
			if (i != index) {
				Node3D node1 = nodes.get(i);
				Node3D node2 = nodes.get(index);
				nodes.set(i, node2);
				nodes.set(index, node1);
			}
		}
	}

	// --------------------------------------------
	public void randomizeColors(int mergeCount) {
		for (Node3D node : nodes) {
			assert(node.isVisible());
			PhongMaterial material = new PhongMaterial(randomColor());
			node.setMaterial(material);
		}
		for (int i = 0; i < mergeCount; i++) {
			mergeNearbyColors();
		}
	}

	// --------------------------------------------
	public void mergeNearbyColors() {
		for (Node3D node : nodes) {
			assert(node.isVisible());
			Color oldColor = node.getMaterial().getDiffuseColor();
			double components[] = new double[3];
			components[0] = oldColor.getRed();
			components[1] = oldColor.getGreen();
			components[2] = oldColor.getBlue();
			int countVisibleNeighbors=0;
			for (Node3D neighbor : node.getNeighbors()) {
				if (neighbor.isVisible()) {
					countVisibleNeighbors++;
					Color color = neighbor.getMaterial().getDiffuseColor();
					components[0] += color.getRed();
					components[1] += color.getGreen();
					components[2] += color.getBlue();
				}
			}
			int cnt = 1 + countVisibleNeighbors;
			components[0] /= cnt;
			components[1] /= cnt;
			components[2] /= cnt;
			Color newColor = Color.color(components[0], components[1], components[2]);
			PhongMaterial material = new PhongMaterial(newColor);
			node.setMaterial(material);
		}
	}

	private static Point3D randomUnitVector() {
		return new Point3D(ThreadLocalRandom.current().nextDouble() - 0.5,
				ThreadLocalRandom.current().nextDouble() - 0.5, ThreadLocalRandom.current().nextDouble() - 0.5)
						.normalize();
	}

	// Returns max of width, height, and depth, and sets minX, minY, minY, maxY, and
	// minZ and maxZ
	public double getMaxWidthHeightDepth() {
		minX = Double.MAX_VALUE;
		maxX = Double.NEGATIVE_INFINITY;
		minY = Double.MAX_VALUE;
		maxY = Double.NEGATIVE_INFINITY;
		minZ = Double.MAX_VALUE;
		maxZ = Double.NEGATIVE_INFINITY;
		for (Node3D node : nodes) {
			double x = node.getX();
			double y = node.getY();
			double z = node.getZ();
			if (x < minX) {
				minX = x;
			}
			if (x > maxX) {
				maxX = x;
			}
			if (y < minY) {
				minY = y;
			}
			if (y > maxY) {
				maxY = y;
			}
			if (z < minZ) {
				minZ = z;
			}
			if (z > maxZ) {
				maxZ = z;
			}
		}
		double width = maxX - minX;
		double height = maxY - minY;
		double depth = maxZ - minZ;
		return Math.max(width, Math.max(height, depth));
	}

	public void shift(double deltaX, double deltaY, double deltaZ) {
		Point3D centroid = computeCentroid();
		for (Node3D node : nodes) {
			node.setXYZ(node.getX() - centroid.getX() + deltaX, node.getY() - centroid.getY() + deltaY,
					node.getZ() - centroid.getZ() + deltaZ);
		}
		getMaxWidthHeightDepth();
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
		return Math.abs(x - y) > 0.1;
	}

	// --------------
	private static final double attractive(double d, double k) {
		return (d * d) / k;
	}

	// --------------
	private static final double repulsive(double d, double k) {
		// return (k*k)/(d*(d<1?1: Math.log(d)));
		return (k * k) / (d); // d*d? d?
	}

	private static String toString(double x, double y, double z) {
		return "(" + toString(x) + ", " + toString(x) + ", " + toString(z) + ")";
	}

	public static String toString(double d) {
		return Node3D.numberFormat.format(d);
	}

	private static Point3D randomNormalizedDirectionVector() {
		return new Point3D(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
	}

	private Set<Node3D> chooseEightRandomNodes() {
		Set<Node3D> result = new TreeSet<>();
		while (result.size() < 8) {
			Node3D node = nodes.get(random.nextInt(nodes.size()));
			result.add(node);
		}
		return result;
	}

	private Set<Node3D> chooseEightPairwiseNodesWithNoEdgesBetween() {
		Set<Node3D> result = new TreeSet<>();
		for (int i = 0; i < 8; i++) {
			Node3D node = chooseNodeWithNoEdgeTo(result);
			if (node == null) {
				break;
			}
			result.add(node);
		}
		return result;
	}

	private Node3D chooseNodeWithNoEdgeTo(Set<Node3D> result) {
		for (Node3D node : nodes) {
			if (!result.contains(node) && !hasEdgeTo(node, result)) {
				return node;
			}
		}
		return null;
	}

	private static boolean hasEdgeTo(Node3D node, Set<Node3D> nodes) {
		for (Node3D other : nodes) {
			if (other.isVisible() && node.getNeighbors().contains(other)) {
				return true;
			}
		}
		return false;
	}
	public void setGroup(Group group) {
		this.group = group;
	}
	public Group getGroup() {
		return group;
	}
	

	private static boolean shown = false;

	public boolean intersects(ConnectedComponent component) {
		if (!shown) {
			System.err.println("intersects TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			shown = true;
		}
		return false;
	}

	private int limitForIndex(int index) {
		if (index < 0) {
			return 0;
		} else if (index >= nodeMatrix.length) {
			return nodeMatrix.length - 1;
		} else {
			return index;
		}
	}

	private void showMinMaxXYZ() {
		double minX = Double.MAX_VALUE, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.MAX_VALUE, maxY = Double.NEGATIVE_INFINITY;
		double minZ = Double.MAX_VALUE, maxZ = Double.NEGATIVE_INFINITY;
		for(Node3D n:nodes) {
			minX=Math.min(n.getX(), minX);
			minY=Math.min(n.getY(), minY);
			minZ=Math.min(n.getZ(), minZ);
			maxX=Math.max(n.getX(), maxX);
			maxY=Math.max(n.getY(), maxY);
			maxZ=Math.max(n.getZ(), maxZ);
		}
		System.out.print("Min = (" + numberFormat.format(minX) + ", " + numberFormat.format(minY) + ", " + numberFormat.format(minZ) + ")");
		System.out.println(", max = (" + numberFormat.format(maxX) + ", " + numberFormat.format(maxY) + ", " + numberFormat.format(maxZ) + ")");
	}
	private int countLimits[] = {0,0,0};
	
	//-----------------------------------
	public void placeUsingBarrycenter() {
		if (nodes.size()<8) {
			stochasticModel();
			return;
		}
		final double one = 0.0;
		final double two = 1000;
		nodes.get(0).setXYZ(one, one, one);
		nodes.get(1).setXYZ(one, one,  two);
		nodes.get(2).setXYZ(one,  two, one);
		nodes.get(3).setXYZ(one,  two,  two);
		nodes.get(4).setXYZ(two,  one, one);
		nodes.get(5).setXYZ(two,  one,  two);
		nodes.get(6).setXYZ(two,   two, one);
		nodes.get(7).setXYZ(two,   two,  two);
		final int n=nodes.size();
		for(int i=8;i<n;i++) {
			nodes.get(i).setXYZ(0,0,0);
		}
		final long startTime= System.currentTimeMillis();
		for(int rep=1;rep<4;rep++) {
			if (rep%100==0) {
				if (System.currentTimeMillis()-startTime > 1000) {
					System.out.println("Exited after one second");
					break;
				}
			}
			
			for(int i=8;i<n;i++) {
				Node3D node = nodes.get(i);
				double x=0;
				double y=0;
				double z=0;
				for(Node3D neighbor: node.getNeighbors()) {
					x+= neighbor.getX();
					y+= neighbor.getY();
					z+= neighbor.getZ();
				}
				double countOfNeighbors=1+node.getNeighbors().size();
				node.setXYZ(x/countOfNeighbors, y/countOfNeighbors, z/countOfNeighbors);
			}
		} // for rep
	} // barrycenter
	//---------------------------
	private double limit(double value, double originalValue, int source) {
		if (!Double.isFinite(value) || Math.abs(value)>2000) {
			countLimits[source]++;
			return originalValue+ random.nextDouble()-0.5;
		}
		return value;
	}
	public void fruchtermanAndReingold() {
		if (nodes.size() <= 1) {
			return;
		}
		final int n = nodes.size();
		showMinMaxXYZ();
		final double repulsionFactor = 50.0*Visualizer.repulsionFactor;
		final double attractionFactor = 0.1; // repulsionFactor; //0.1;
		int iterations=0;
		countLimits[0]=0;
		countLimits[1]=0;
		countLimits[2]=0;
		System.out.println("Entering fruchtermanAndReingold with repulsionFactor = " + numberFormat.format(repulsionFactor)
		 + " and Visuzlizer.maxRepulsiveNodesToInclude = " + Visualizer.maxRepulsiveNodesToInclude);
		for (double delta = 1.0; delta > 0.01; delta *= decayFactorForFruchtermanAndReingold) {
			iterations++;
			// Attractive forces are between vertices connected by an edge. f_a(d) = d*d/k. (Why k in denominator?)
			// All pairs of vertices have repulsive forces. f_r(d) = -k*k/d; but we do a sample.
			// k = C * sqrt(area/#vertices)
			
			// Eades(1984) uses f_a(d) = log(d/c2). f_r(d)=c3/(d*d) 
			for (int i = 0; i < n; i++) {
				final Node3D node = nodes.get(i);			
				final double x=node.getX();
				final double y=node.getY();
				final double z=node.getZ();
				double dx=0;
				double dy=0;
				double dz=0;
				final double maxInvertDistanceSquared = 10*Visualizer.distanceForOneEdge;
				for(Node3D neighbor: node.getNeighbors()) {
					if (neighbor.isVisible()) {
						final double xDisplacement = neighbor.getX() - x;
						final double yDisplacement = neighbor.getY() - y;
						final double zDisplacement = neighbor.getZ() - z;
						final double distanceSquared = Math.max(0.001, square(xDisplacement)+square(yDisplacement)+square(zDisplacement));
						final double distance = Math.sqrt(distanceSquared);
						final double f=attractionFactor*Math.min(1.0,(distance/Visualizer.distanceForOneEdge));
						final double xDeltaAttractive = f*xDisplacement;
						final double yDeltaAttractive = f*yDisplacement;
						final double zDeltaAttractive = f*zDisplacement;
						final double g=repulsionFactor*Math.min(maxInvertDistanceSquared, 1/distanceSquared);
						final double xDeltaRepulsive = xDisplacement *g;
						final double yDeltaRepulsive = yDisplacement *g;
						final double zDeltaRepulsive = zDisplacement *g;
						dx += xDeltaAttractive-xDeltaRepulsive;
						dy += yDeltaAttractive-yDeltaRepulsive;
						dz += zDeltaAttractive-zDeltaRepulsive;
					}
				}
				if (Visualizer.maxRepulsiveNodesToInclude==0 || repulsionFactor<0.001) {
				} else if (nodes.size()<= Visualizer.maxRepulsiveNodesToInclude) {
					for(Node3D other: nodes) {
						if (other!=node && !node.getEdges().containsKey(other)) {
							final double xDisplacement = other.getX() - x;
							final double yDisplacement = other.getY() - y;
							final double zDisplacement = other.getZ() - z;
							final double distanceSquared = Math.max(0.001,square(xDisplacement)+square(yDisplacement)+square(zDisplacement));
							final double g=repulsionFactor*Math.min(maxInvertDistanceSquared, 1.0/distanceSquared);
							final double xDeltaRepulsive = xDisplacement *g;
							final double yDeltaRepulsive = yDisplacement *g;
							final double zDeltaRepulsive = zDisplacement *g;
							dx -= xDeltaRepulsive;
							dy -= yDeltaRepulsive;
							dz -= zDeltaRepulsive;
						}
					}
				} else {
					random.setSeed(node.getIndexInImportanceOrder());
					for(int j=0;j<Visualizer.maxRepulsiveNodesToInclude;j++) {
						Node3D other = nodes.get(random.nextInt(nodes.size()));
						if (other!=node && !node.getEdges().containsKey(other)) {
							final double xDisplacement = other.getX() - x;
							final double yDisplacement = other.getY() - y;
							final double zDisplacement = other.getZ() - z;
							final double distanceSquared = square(xDisplacement)+square(yDisplacement)+square(zDisplacement);
							final double g=Math.min(maxInvertDistanceSquared, repulsionFactor/distanceSquared);
							final double xDeltaRepulsive = xDisplacement *g;
							final double yDeltaRepulsive = yDisplacement *g;
							final double zDeltaRepulsive = zDisplacement *g;
							dx -= xDeltaRepulsive;
							dy -= yDeltaRepulsive;
							dz -= zDeltaRepulsive;
						}
					}
				}
				double newX= limit(x+delta*dx, x,0);
				double newY= limit(y+delta*dy, y,1);
				double newZ= limit(z+delta*dz, z, 1);
				//System.out.println(newX + ", " + newY + ", " + newZ);
				node.setXYZ(newX,newY, newZ);
			} // for i
		}
		System.out.println(iterations + " iterations with countLimits = " + Arrays.toString(countLimits));
	}
	
	private static int intersectionSize(Set<?> s1, Set<?> s2) {
		int count=0;
		for(Object obj:s1) {
			if (s2.contains(obj)) {
				count++;
			}
		}
		return count;
	}
	
	private static int unionSize(Set s1, Set s2) {
		int size=s1.size();
		for(Object obj:s2) {
			if (!s2.contains(obj)) {
				size++;
			}
		}
		return size;
	}
	
	// Doesn't work well
	public void mds(int neighborhoodSize) {
		/*
		final int n=nodes.size();
		final double distanceMatrix[][] = new double[n][n];
		for(int i=0;i<n;i++) {
			final Node3D n1=nodes.get(i);
			final Set neighbors1 = neighborhoodSize==1? n1.getNeighbors(): n1.getNeighborhood(neighborhoodSize);
			for(int j=i+1;j<n;j++) {
				final Node3D n2=nodes.get(j);
				final double edgeDistance= n1.getNeighbors().contains(n2)? 0.0 : 1.0;
				final Set neighbors2 = neighborhoodSize==1? n2.getNeighbors():n2.getNeighborhood(neighborhoodSize);
				int unionSize=unionSize(neighbors1, neighbors2);
				int intersectionSize=intersectionSize(neighbors1, neighbors2);
				double ratio=(0.0+intersectionSize)/unionSize;
				double neighborsDistance =(1.0-ratio);
				double distance = edgeDistance + neighborsDistance;
				distanceMatrix[i][j]=distance;
				distanceMatrix[j][i]=distance;
			}
		}
		double[][] output= //MDSJ.classicalScaling(distanceMatrix,3); // apply MDS
				MDSJ.stressMinimization(distanceMatrix, 3); // apply MDS
		System.out.println("Output of MDS has dimensions: " + output.length + " x " + output[0].length);
		final double c=500.0; // TODO
		for(int i=0;i<n;i++) {
			double x=500*output[0][i];
			double y=500*output[1][i];
			double z=500*output[2][i];
			if (i<10) {
				System.out.println(x + ", " + y + ", " + z);
			}
			nodes.get(i).setXYZ(x, y, z);
		}
		*/
	}
	//---------------------

}
