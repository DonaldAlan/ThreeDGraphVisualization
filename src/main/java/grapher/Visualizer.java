package grapher;


import java.awt.Event;

/*
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 * See README.txt for more documentation. 
 * 
 * TODO 1: First layout the most important nodes. Then fix their positions and layout the less important nodes, in stages.
 * TODO 2: Divide the (3D) Euclidean space into regions and restrict forces to local regions, to optimize UnrestrictedForces.
 */

import java.text.NumberFormat;
import grapher.Node3D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JOptionPane;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

public class Visualizer extends Application {
	public static int preferredCountOfNodesShown = 1000; // The slider can override this value.
	//--------------------
	public static enum Layout { Stochastic,Spring,Barrycenter,FruchtermanAndReingold, Systematic;}
	public static Layout layout = Layout.Systematic;
	public static Node3D[] nodesToDisplay = null;
	public static Node3D[] savedAllNodes=null;
	public static double distanceForOneEdge = 10;
	public static double sphereRadius = 5;
	public static double cylinderRadius = 1;
	public static String title="Visualize Graph";
	//.....
	private final static int width = 1600;
	private final static int height = 900;
	//..
	private final static String betweennessCentralityAlgorithm="BetweennessCentrality (Jung)";
    private final static String pageRankAlgorithm= "Page Rank (Jung)";
    private final static String degreeAlgorithm="Degree";
    private final static String closenessCentrality = "Closeness Centrality (Jung)";
    private final static String randomWalkVisits="Random walk visit count";
    private final static String markovCentralityJung="Markov Centrality(Jung)"; // throws exception "Matrix is singular"
    private final static String randomWalkCentralityJung="Random Walk Centrality(Jung)"; 
    //...
    private int maxFocusDistance=3;
	private String currentImportanceAlgorithm=degreeAlgorithm;
	private final XformWorld world = new XformWorld();
	private final PerspectiveCamera camera = new PerspectiveCamera(true);
	private final XformCamera cameraXform = new XformCamera();
	private static double cameraInitialX = 0;
	private static double cameraInitialY = 0;
	private static double cameraInitialZ = 0;
	private static final double CAMERA_NEAR_CLIP = 0.1;
	private static final double CAMERA_FAR_CLIP = 20000.0;
	private static Random random = new Random();
	private double mousePosX, mousePosY, mouseOldX, mouseOldY, mouseDeltaX, mouseDeltaY;
//	private PhongMaterial sphereMaterial1 = new PhongMaterial();
//	private PhongMaterial sphereMaterial2 = new PhongMaterial();
	private static final Point3D YAXIS = new Point3D(0, 1, 0);
	protected static final long ONE_MILLISECOND_IN_NANOSECONDS = 1_000_000L;
	private static NumberFormat numberFormat = NumberFormat.getInstance();
	private volatile Sphere selectedSphere = null;
	private Cylinder selectedCylinder = null;
	private volatile Node3D focusedNode=null;
	private Set<ConnectedComponent> connectedComponents=null;
	private List<Shape3D> shapes = new ArrayList<Shape3D>();
	private final Group root = new Group();
	
	private Stage primaryStage;
	private MessageBox messageBox;
	private double minImportance=Double.MAX_VALUE;
	private double maxImportance=Double.NEGATIVE_INFINITY;
	private double percentToShow=100; // 100.0; // display all by default
	private Font tooltipFont = new Font("Times Roman",20);
	private final Slider importanceSlider=new Slider();
	private final ComboBox<String> importanceAlgorithmComboBox = new ComboBox<>();
	private final ComboBox<String> stochasticCountComboBox = new ComboBox<>();
	private final ComboBox<String> graphingAlgorithmComboBox = new ComboBox<>();
	private int limitIndexForNodesTaoDisplay;
	private volatile long requestPlaceOnePassTimeInMls=0;
	private final String unrestrictedForcesText = "Unrestricted Forces";
	private final String redrawing="(Redrawing)";
	private final String approximateForcesText = "Approximate Forces";
	private final BackgroundFill backgroundFillNormal = new BackgroundFill(Color.MOCCASIN, CornerRadii.EMPTY, Insets.EMPTY);
	private final BackgroundFill backgroundFillRedrawing = new BackgroundFill(Color.YELLOW, CornerRadii.EMPTY, Insets.EMPTY);
	private final Background backgroundNormal =new Background(backgroundFillNormal);
	private final Background backgroundRedrawing =new Background(backgroundFillRedrawing);
	private final BackgroundFill controlBackgroundFill = new BackgroundFill(Color.SKYBLUE, CornerRadii.EMPTY, Insets.EMPTY);
	private final Background controlBackground = new Background(controlBackgroundFill);

	private final Button redoLayoutButton=new Button("Redraw");
	// --------------
	static {
		numberFormat.setMaximumFractionDigits(3);
	}

	public Visualizer() {
		importanceSlider.setValue(percentToShow);
	}
	// Return cost of using point3D for node i
	private double getCost(int i, Point3D point3d, Node3D nodeI) {
		double sumCosts = 0;
		for (int j = 0; j < nodesToDisplay.length; j++) {
			if (i != j) {
				Node3D nodeJ = nodesToDisplay[j];
				double distance = point3d.distance(nodeJ.getPoint3D());
				if (distance < distanceForOneEdge) { // Prevent crowding
					return Double.MAX_VALUE;
				}
				if (nodeI.getEdges().containsKey(nodeJ) || nodeJ.getEdges().containsKey(nodeI)) {
					sumCosts += square(distance - distanceForOneEdge);
				}
			}
		}
		return sumCosts;
	}

	private boolean intersectsSomeOtherConnectedComponent(ConnectedComponent component, Set<ConnectedComponent> set) {
		for (ConnectedComponent other : set) {
			if (other != component && other.intersects(component)) {
				if (nodesToDisplay.length <= 10) {
					System.out.println(component + " intersects \n" + other + "\n");
				}
				return true;
			}
		}
		return false;
	}
	private void requestReplaceOnePass() {
		requestPlaceOnePassTimeInMls=System.nanoTime();
	}
	private void randomizeNodePlacements() {
		System.out.println("Entering randomizeNodePlacements");
		for(int i=0;i<nodesToDisplay.length;i++) {
			nodesToDisplay[i].randomizePlacement();
		}
	}
	public double getTotalCost() {
		double startTotalCost=0;
		for (ConnectedComponent connectedComponent : connectedComponents) {
			startTotalCost+= connectedComponent.getCost();
		}
		return startTotalCost;
	}
	public void placeOnePass(boolean initialize) {
		long startTime=System.currentTimeMillis();
	//	randomizeNodePlacements();
		computeConnectedComponents();
		double startTotalCost=getTotalCost();
		double newTotalCost=0;
		System.out.println("Using layout " + layout);
		int totalCountViaConnectedComponents=0;
		for (ConnectedComponent connectedComponent : connectedComponents) {
			totalCountViaConnectedComponents+= connectedComponent.size();
			switch (layout) {
			case Spring:
				connectedComponent.springModel();
				break;
			case Stochastic:
				connectedComponent.stochasticModel();
				break;
			case Barrycenter:
				throw new UnsupportedOperationException();
				//connectedComponent.placeUsingBarrycenter();
				//break;
			case FruchtermanAndReingold:
				System.err.println("FruchtermanAndReingold not implemented");
				break;
			case Systematic:
				connectedComponent.systematicModel();
				break;
			}
			connectedComponent.getMaxWidthHeightDepth();
			newTotalCost+= connectedComponent.getCost();
		}
		moveConnectedComponentsAwayFromEachOther();
		//TODO: moveCameraToPointToCenterOfMass();
		
		long middle = System.currentTimeMillis();
		double seconds=0.001*(middle-startTime);
		System.out.println(numberFormat.format(seconds)
				+ ", start cost = " + numberFormat.format(startTotalCost)
				+ ", final cost = " + numberFormat.format(newTotalCost)
				+ " cost change = " + numberFormat.format(startTotalCost-newTotalCost)
				//+ ",\n totalCost again = " + numberFormat.format(getTotalCost())
				+ ", totalCountViaConnectedComponents = " + totalCountViaConnectedComponents
				+ ", nodesToDisplay.length = " + nodesToDisplay.length
				+ " in " + numberFormat.format(seconds) + " seconds"
				 );
		refreshNodes();
		seconds = 0.001*(System.currentTimeMillis() - middle);
	}

	@SuppressWarnings("unchecked")
	private void buildImportanceAlgorithmComboBox(Group root) {
		importanceAlgorithmComboBox.setTranslateX(-660);
		importanceAlgorithmComboBox.setTranslateY(-410);
		importanceAlgorithmComboBox.setTranslateZ(1600);
		importanceAlgorithmComboBox.setBackground(controlBackground);
		Tooltip tooltip = new Tooltip("Importance algorithm");
		importanceAlgorithmComboBox.setTooltip(tooltip);
		  // Use Java Collections to create the List.
        final List<String> itemList = new ArrayList<String>();
      
        itemList.add(betweennessCentralityAlgorithm);
        itemList.add(pageRankAlgorithm);
        itemList.add(degreeAlgorithm);
        itemList.add(closenessCentrality);
   //     itemList.add(randomWalkCentralityJung);  Throws excecption "Matrix is sinular"
   //     itemList.add(markovCentralityJung);  Throws excecption "Matrix is sinular"
        itemList.add(randomWalkVisits);
        final ObservableList<String> observableList = FXCollections.observableList(itemList);
		importanceAlgorithmComboBox.setItems(observableList);
		importanceAlgorithmComboBox.setValue(currentImportanceAlgorithm);
		importanceAlgorithmComboBox.setOnAction( e -> {
			String newAlgorithm = importanceAlgorithmComboBox.getValue();
			if (newAlgorithm.equals(currentImportanceAlgorithm)) {
				return;
			}
			currentImportanceAlgorithm=newAlgorithm;
			runCurrentImportanceAlgorithm();
			calculateMinMaxImportance();
			assignImportanceIndices();
			placeOnePass(true);
			refreshNodes();
			});
		root.getChildren().add(importanceAlgorithmComboBox);
	}
	private void buildGraphPlacementAlgorithmComboBox(Group root) {
		graphingAlgorithmComboBox.setTranslateX(380);
		graphingAlgorithmComboBox.setTranslateY(-335);
		graphingAlgorithmComboBox.setTranslateZ(1300);
		graphingAlgorithmComboBox.setBackground(controlBackground);
		final List<String> itemList = new ArrayList<String>();
		itemList.add(Layout.Stochastic.name());
		itemList.add(Layout.Systematic.name());
		final ObservableList<String> observableList = FXCollections.observableList(itemList);
		graphingAlgorithmComboBox.setItems(observableList);
		graphingAlgorithmComboBox.setValue(Layout.Systematic.name());
		graphingAlgorithmComboBox.setOnAction( e -> {
        	String value=graphingAlgorithmComboBox.getValue();
        	layout = Layout.valueOf(value);
		});
		root.getChildren().add(graphingAlgorithmComboBox);
	}
	@SuppressWarnings("unchecked")
	private void buildStochasticCountComboBox(Group root) {
		stochasticCountComboBox.setTranslateX(-750);
		stochasticCountComboBox.setTranslateY(-410);
		stochasticCountComboBox.setTranslateZ(1600);
	
		stochasticCountComboBox.setBackground(controlBackground);
		Tooltip tooltip = new Tooltip("Choose count of stochastic placements. Higher values result in better layouts but are slower.");
		stochasticCountComboBox.setTooltip(tooltip);
		  // Use Java Collections to create the List.
        final List<String> itemList = new ArrayList<String>();
        for(int i=2;i<=40;i++) {
        	itemList.add(""+i);
        }
        final ObservableList<String> observableList = FXCollections.observableList(itemList);
        stochasticCountComboBox.setItems(observableList);
        stochasticCountComboBox.setValue(""+ConnectedComponent.stochasticMovesReps);
        stochasticCountComboBox.setOnAction( e -> {
        	String value=stochasticCountComboBox.getValue();
        	int anInt=Integer.parseInt(value);
        	ConnectedComponent.stochasticMovesReps=anInt;
        	System.out.println("Settingcount of stochastic placements to " + anInt);
        	//requestReplaceOnePass();
			});
		root.getChildren().add(stochasticCountComboBox);
	}

	private void runCurrentImportanceAlgorithm() {
		switch (currentImportanceAlgorithm) {
		case betweennessCentralityAlgorithm:
			Node3D.computeImportanceViaJungBetweenessCentrality(nodesToDisplay);
			break;
		case  pageRankAlgorithm:
			Node3D.computeImportanceViaJungPageRank(nodesToDisplay); 
			break;
		case degreeAlgorithm:
			Node3D.computeImportanceViaDegree(nodesToDisplay);
			break;
		case randomWalkVisits:
			Node3D.computeImportanceViaRandomWalks(nodesToDisplay, 20, 100);
			break;
		case randomWalkCentralityJung:
			Node3D.computeImportanceViaJungRandomWalkBetweenness(nodesToDisplay);
			break;
		case markovCentralityJung:
			Node3D.computeImportanceViaJungMarkovCentrality(nodesToDisplay); 
			break;
		case closenessCentrality:
			Node3D.computeImportanceViaJungClosenessCentrality(nodesToDisplay);
			break;
		  default: throw new IllegalStateException();
		}
	}

	@SuppressWarnings("unchecked")
	private void buildSlider(Group root) {
	        importanceSlider.setTranslateX(-200);
	        importanceSlider.setTranslateY(-286);
	        importanceSlider.setTranslateZ(1100);
	        importanceSlider.setMin(0);
	        importanceSlider.setMax(100); // logarithmic scale
	        importanceSlider.setValue(percentToShow);
	        importanceSlider.setShowTickLabels(false);
	        importanceSlider.setShowTickMarks(true);
	        importanceSlider.setMajorTickUnit(5);
	        importanceSlider.setTooltip(new Tooltip("Control how many nodes to display, ordered by importance."));
	        BackgroundFill backgroundFill = new BackgroundFill(Color.DARKGREEN, CornerRadii.EMPTY, Insets.EMPTY);
	        Background background = new Background(backgroundFill);
	        importanceSlider.setBackground(background);
	        importanceSlider.setMinWidth(width / 4);
	        root.getChildren().add(importanceSlider);
	        importanceSlider.setOnMouseReleased( e -> {
	        	percentToShow=importanceSlider.getValue();
	        	refreshNodes();
	        });
	        // We add this to prevent the slider from processing the key event
	        EventHandler filter = new EventHandler<InputEvent>() {
	            public void handle(InputEvent event) {
	            	if (event instanceof KeyEvent) {
	            		keyEventHandler.handle((KeyEvent)event);
	            		event.consume();
	            	}
	            }
	        };
	        root.addEventFilter(KeyEvent.KEY_PRESSED, filter);
	    }
	
	private void buildRedoLayoutButton(Group root) {
		redoLayoutButton.setTranslateX(540);
		redoLayoutButton.setTranslateY(-335);
		redoLayoutButton.setTranslateZ(1300);
		redoLayoutButton.setBackground(controlBackground);
		redoLayoutButton.setOnAction( e -> {
			if (requestPlaceOnePassTimeInMls>0) {
				return;
			} else {
				requestReplaceOnePass();
			}
		});
		root.getChildren().add(redoLayoutButton);
	}
	private void refreshNodes() {
		world.getChildren().clear();
		shapes.clear();
		displayNodes();
		world.requestLayout();
	}

	private void moveConnectedComponentsAwayFromEachOther() {
		if (connectedComponents.size()==1) {
			return;
		}
		int successes = 0;
		int moves = 0;
		for (ConnectedComponent connectedComponent : connectedComponents) {
			for (int i = 0; i < 30; i++) {
				if (!intersectsSomeOtherConnectedComponent(connectedComponent, connectedComponents)) {
					// System.out.println(connectedComponent + " does not
					// intersect\n " + connectedComponents + "\n");
					successes++;
					break;
				}
				moves++;
				connectedComponent.shift(random.nextInt(Node3D.windowSize), random.nextInt(Node3D.windowSize),
						random.nextInt(Node3D.windowSize));
			}
		}
//		System.out.println("Successes count = " + successes + " out of " + connectedComponents.size() + " with " + moves
//				+ " moves");
	}

	private Map<ConnectedComponent, Point3D> getCentroids() {
		Map<ConnectedComponent, Point3D> centroids = new HashMap<>();
		for (ConnectedComponent cc : connectedComponents) {
			centroids.put(cc, cc.computeCentroid());
		}
		return centroids;
	}

	// -------
	/**
	 * Ignores isVisible. Only needs to be done once
	 */
	private void computeConnectedComponents() {
		if (connectedComponents==null) {
			connectedComponents = new HashSet<>();
			for (int i=0;i<nodesToDisplay.length;i++) {
				connectedComponents.add(nodesToDisplay[i].getConnectedComponent());
			}
			for(ConnectedComponent conn:connectedComponents) {
				conn.done();
			}
		}
	}

	// -------------------------
	private static class XformWorld extends Group {
		final Translate t = new Translate(0.0, 0.0, 0.0);
		final Rotate rx = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
		final Rotate ry = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
		final Rotate rz = new Rotate(0, 0, 0, 0, Rotate.Z_AXIS);

		public XformWorld() {
			super();
			this.getTransforms().addAll(t, rx, ry, rz);
		}
	}

	// -------------------------
	private static class XformCamera extends Group {
		final Translate t = new Translate(0.0, 0.0, 0.0);
		final Rotate rx = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
		final Rotate ry = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
		final Rotate rz = new Rotate(0, 0, 0, 0, Rotate.Z_AXIS);

		public XformCamera() {
			super();
			this.getTransforms().addAll(t, rx, ry, rz);
		}
	}

	private void buildCamera(Group root) {
		root.getChildren().add(cameraXform); 
		cameraXform.getChildren().add(camera);
		camera.setNearClip(CAMERA_NEAR_CLIP);
		camera.setFarClip(CAMERA_FAR_CLIP);
		camera.setTranslateX(cameraInitialX);
		camera.setTranslateY(cameraInitialY);
		camera.setTranslateZ(cameraInitialZ);
	}

	private void doReleaseOrMouseExit() {
		if (selectedSphere != null) {
			selectedSphere = null;
		}
		if (selectedCylinder != null) {
			selectedCylinder = null;
		}
	}

	// From http://netzwerg.ch/blog/2015/03/22/javafx-3d-line/
	public Cylinder createCylinderBetween(Point3D origin, Point3D target,Node3D sourceNode) {
		Point3D diff = target.subtract(origin);
		double height = diff.magnitude();
		Point3D mid = target.midpoint(origin);
		Translate moveToMidpoint = new Translate(mid.getX(), mid.getY(), mid.getZ());
		Point3D axisOfRotation = diff.crossProduct(YAXIS);
		double angle = Math.acos(diff.normalize().dotProduct(YAXIS));
		Rotate rotateAroundCenter = new Rotate(-Math.toDegrees(angle), axisOfRotation);
		Cylinder line = new Cylinder(cylinderRadius, height);
		line.setUserData(sourceNode);
		line.getTransforms().addAll(moveToMidpoint, rotateAroundCenter);
		return line;
	}

	private Sphere drawSphere(double x, double y, double z, double radius, PhongMaterial material, String toolTipMessage) {
		Sphere sphere = new Sphere(radius);
		// double opacity= square(square(square(1- distance/maxDistance)));
		// sphere.setOpacity(opacity);
		// if (count%10==0) {System.out.println(opacity);}
		sphere.setMaterial(material);
		sphere.setTranslateX(x);
		sphere.setTranslateY(y);
		sphere.setTranslateZ(z);
		world.getChildren().add(sphere);
		Tooltip tooltip = new Tooltip(toolTipMessage);
		tooltip.setFont(tooltipFont);
		Tooltip.install(sphere, tooltip);
		return sphere;
	}

	private static double square(double x) {
		return x * x;
	}

	private void handleMouse(Scene scene) {
		scene.setOnMousePressed((MouseEvent me) -> {
			mousePosX = me.getSceneX();
			mousePosY = me.getSceneY();
			mouseOldX = me.getSceneX();
			mouseOldY = me.getSceneY();
			// this is done after clicking and the rotations are apparently
			// performed in coordinates that are NOT rotated with the camera.
			// (pls activate the two lines below for clicking)
			// cameraXform.rx.setAngle(-90.0);
			// cameraXform.ry.setAngle(180.0);
			PickResult pr = me.getPickResult();
			if (pr.getIntersectedNode() instanceof Sphere) {
				selectedSphere = (Sphere) pr.getIntersectedNode();
				Node3D node = (Node3D) selectedSphere.getUserData();
				if (me.isPrimaryButtonDown()) {
					spherePopup(node);
				} else {
					focus(node);
				}
			} else if (pr.getIntersectedNode() instanceof Cylinder) {
			}
		});
		scene.setOnMouseReleased((MouseEvent me) -> {
			doReleaseOrMouseExit();
		});
		scene.setOnMouseDragExited((MouseEvent me) -> {		
			doReleaseOrMouseExit();
		});
		scene.setOnMouseDragged((MouseEvent me) -> {
			mouseOldX = mousePosX;
			mouseOldY = mousePosY;
			mousePosX = me.getSceneX();
			mousePosY = me.getSceneY();
			mouseDeltaX = (mousePosX - mouseOldX);
			mouseDeltaY = (mousePosY - mouseOldY);
			if (me.isPrimaryButtonDown()) {
				world.ry.setAngle(world.ry.getAngle() - mouseDeltaX * 0.2);
				world.rx.setAngle(world.rx.getAngle() + mouseDeltaY * 0.2);

				// world.ry.setAngle(world.ry.getAngle() + mouseDeltaX * 0.2);
				// world.rx.setAngle(world.rx.getAngle() - mouseDeltaY * 0.2);
			} else if (me.isSecondaryButtonDown()) {
				world.ry.setAngle(world.ry.getAngle() - mouseDeltaX * 0.2);
				world.rz.setAngle(world.rz.getAngle() + mouseDeltaY * 0.2);
//				world.t.setZ(world.t.getZ() - mouseDeltaY);
//				world.t.setX(world.t.getX() - mouseDeltaX);
			
			}
		});
	}

	private void spherePopup(Node3D node) {
		double meanDistanceToNeighbors = node.meanXYZDistanceToNeighbors();
		double meanDistanceToNonNeighbors = node.meanXYZDistanceToNonNeighbors(nodesToDisplay);
		StringBuilder sb = new StringBuilder();
		sb.append('\n');
		int rowCnt = 0;
		for (Map.Entry<String, Object> entry : node.getAttributes().entrySet()) {
			if (!entry.getKey().equals("description")) {
				sb.append(" " + entry.getKey() + " = " + entry.getValue().toString() + "\n");
				rowCnt++;
			}
		}
		sb.append(" Number of neighbors = " + node.getNeighbors().size() + ":\n ");
		int col = 0;
		for (Node3D neighbor : node.getNeighbors()) {
			if (col > 40) {
				sb.append(";\n ");
				col = 0;
				rowCnt++;
				if (rowCnt > 10) {
					if (col > 0) {
						sb.append("\n ");
					}
					sb.append("...");
					break;
				}
			} else if (col > 0) {
				sb.append("; ");
				col += 2;
			}
			String neighborIdDescription = neighbor.getIdAndDescription();
			sb.append(neighborIdDescription);
			col += neighborIdDescription.length();
		} // for
		if (sb.length() > 0) {
			char ch = sb.charAt(sb.length() - 1);
			if (ch != ' ' && ch != ';') {
				sb.append(' ');
			}
		}
		String message = sb.toString() + "\n\n Size of connected component = " + node.getConnectedComponent().size()
				+ "\n Mean distance to neighbors = " + numberFormat.format(meanDistanceToNeighbors)
				+ "\n Mean distance to non-neighbors = " + numberFormat.format(meanDistanceToNonNeighbors)
				+ "\n Importance = " + numberFormat.format(node.getImportance())
				//+ "\n isVisible = " + node.isVisible()
				;
		String title = node.getIdAndDescription();
		if (messageBox == null || messageBox.isClosed()) {
			messageBox = new MessageBox(message, title, node, this);
		} else {
			messageBox.update(message, title, node);
		}
	}
	//--------------------------------------------
	/* package*/ void focus(Node3D node) {
		focusedNode=node;
		connectedComponents=null;
		if (savedAllNodes==null) {
			savedAllNodes=nodesToDisplay;
		}
		
		for(Node3D n:savedAllNodes) {
			n.setIsVisible(false);
		}
		nodesToDisplay=getNodesNear(node,maxFocusDistance);
		for(Node3D n: nodesToDisplay) {
			n.setIsVisible(true); 
		}
		placeOnePass(true);
	}

	private Node3D[] getNodesNear(Node3D node, int maxDistance) {
		final Set<Node3D> nearNodes = new HashSet<>();
		node.addNearbyNodes(nearNodes,maxDistance);
		final Node3D [] result = new Node3D[nearNodes.size()];
		nearNodes.toArray(result);
		return result;
	}

	private EventHandler<KeyEvent> keyEventHandler = new EventHandler<KeyEvent>() {
		public void handle(KeyEvent ke) {
			switch (ke.getCode()) {
			case Q:
				System.exit(0);
				break;
			case R:
				for(ConnectedComponent c:connectedComponents) {
					c.randomizePositions(0.1);
				}
				System.out.println("Total cost = " + getTotalCost());
				refreshNodes();
				break;
			case ESCAPE:
				if (ke.isShiftDown()) {
					requestReplaceOnePass();
					break;
				}
				world.t.setZ(0);
				world.rx.setAngle(0);
				world.ry.setAngle(0);
				world.setTranslateZ(0.75*Node3D.windowSize);
				percentToShow=100.0;
				if (savedAllNodes==null) {
					savedAllNodes=nodesToDisplay;
				}
				if (focusedNode!=null || ke.isShiftDown()) {
					for(Node3D n:savedAllNodes) {
						n.setXYZ(Node3D.windowSize*random.nextDouble(),Node3D.windowSize*random.nextDouble(),Node3D.windowSize*random.nextDouble());
					}
				}
				if (focusedNode!=null) {
					focusedNode=null;
					nodesToDisplay=savedAllNodes;
					connectedComponents=null;
					for(Node3D n:nodesToDisplay) {
						n.setIsVisible(true);
					}
					requestReplaceOnePass();
				} else {
					refreshNodes();
				}
				break;
			case LEFT:
				world.setTranslateX(world.getTranslateX() + 10);
				break;
			case RIGHT:
				world.setTranslateX(world.getTranslateX() - 10);
				break;
			case UP:
				if (ke.isShiftDown()) {
					world.setTranslateY(world.getTranslateY() + 10);
				} else {
					world.setTranslateZ(world.getTranslateZ() - 10);
				}
				break;
			case DOWN:
				if (ke.isShiftDown()) {
					world.setTranslateY(world.getTranslateY() - 10);
				} else {
					world.setTranslateZ(world.getTranslateZ() + 10);
				}
				break;
			// Doesn't work
			// case M:
			// System.out.println("Merging colors");
			// for(ConnectedComponent c: getConnectedComponents()) {
			// c.mergeNearbyColors();
			// }
			// world.requestLayout();
			// break;
			case C:
				try {
					randomizeColors(2);
				} catch (Exception exc) {
					exc.printStackTrace();
				}
				break;
			case P:
				placeOnePass(false);
				//showAverageDistances();
				break;
			case H:  {
				String message="\n\n Navigate in 3D space by dragging the mouse, or by pressing the arrow keys."
						+ "\n Use the drop-down at the top left to choose the count of stochastic placements; higher values result in nicer graphs."
						+ "\n Use the next drop-down to choose the importance algorithm that's used to rank nodes."
						+ "\n Use the slider at the top to adjust how many nodes to display, ordered by importance."
						+ "\n The button on the upper right controls whether to approximate forces (faster).  "
						+ "\n For large graphs, it defaults to Approximate Forces; for smaller graphs it defaults to Unrestricted Forces"
						+ "\n Left-click on a node to see details. Right click to focus on that node."
						+ "\n Press Ctrl-F to search for a node by id. If found, the program will focus on that node."
						+ "\n\n Press PageUp and PageDown to adjust how many nodes are shown when focussed."
						+ "\n Press 'r' to reset the view. Press 'R' or click on 'Redraw' to recompute the layout."
						+ "\n Press 'c' to randomize the colors."
						+ "\n Press 'q' to exit."
						;
				new MessageBox(message,"Help");
				break;
			}
			case I: {
					double delta=1.1;
					percentToShow*=  ke.isShiftDown() ? delta : 1.0/delta;
					if (percentToShow>100) {
						percentToShow=100;
					} else if (percentToShow<0) {
						percentToShow=0;
					} else {
						//System.out.println("New percentToShow = " + percentToShow);
					}
					importanceSlider.setValue(percentToShow);
					refreshNodes();
				}
				break;
			case PAGE_UP:
				maxFocusDistance++;
				if (focusedNode!=null) {
					System.out.println("Focusing with maxFocusDistance = " + maxFocusDistance);
					focus(focusedNode);
				}
				break;
			case PAGE_DOWN:
				if (maxFocusDistance>1) {
					maxFocusDistance--;
					if (focusedNode!=null) {
						System.out.println("Focusing with maxFocusDistance = " + maxFocusDistance);
						focus(focusedNode);
					}
				}
				break;
			case F:
				if (ke.isControlDown()) {
					doSearchAndFocus();
				}
				break;
			default:
			}
		}
	};
	private void doSearchAndFocus() {
		String id = (String) JOptionPane.showInputDialog(
                null,
                "Enter node name");
		if (id!=null) {
			id = id.trim();
			if (id.length()>0) {
				for(Node3D node: nodesToDisplay) {
					if (node.getId().equals(id)) {
						focus(node);
						return;
					}
				}
			}
		}
	}
	private void handleKeyEvents(Scene scene) {
		scene.setOnKeyPressed(keyEventHandler);
	}
	private void assignImportanceIndices() {
		Comparator<Node3D> comparator = new Comparator<Node3D>() {
			@Override
			public int compare(Node3D node1, Node3D node2) {
				return -Double.compare(node1.getImportance(), node2.getImportance());
			}};
		Arrays.sort(nodesToDisplay,comparator );
		for(int i=0;i<nodesToDisplay.length;i++) {
			nodesToDisplay[i].setIndexInImportanceOrder(i);
		}
	}

	private void calculateMinMaxImportance() {
		for(Node3D node: nodesToDisplay) {
			double imp=node.getImportance();
			if (imp<minImportance) {
				minImportance=imp;
			}
			if (imp>maxImportance) {
				maxImportance=imp;
			}
		}
		System.out.println("minImportance = " + minImportance + ", maxImportance = " + maxImportance);
	}
	private void randomizeColors(int mergeCount) {
		for (ConnectedComponent c : connectedComponents) {
			c.randomizeColors(mergeCount);
		}
		for(Shape3D shape:shapes) {
			if (shape instanceof Cylinder) {
				Cylinder c = (Cylinder) shape;
				Node3D node = (Node3D) c.getUserData();
				c.setMaterial(node.getMaterial());
			}
		}
		world.requestLayout();
	}

	private void showAverageDistances() {
		double dNeighbors = getAverageDistanceBetweenNeighbors();
		double dNonNeighbors = getAverageDistanceBetweenNonNeighbors();
		double ratio = dNeighbors / dNonNeighbors;
		String message = "ratio = " + numberFormat.format(dNeighbors) + "/" + numberFormat.format(dNonNeighbors) + " = "
				+ numberFormat.format(ratio);
		//primaryStage.setTitle(message);
		System.out.println(message);
	}

	private double getAverageDistanceBetweenNeighbors() {
		double sum = 0.0;
		int count = 0;
		for (Node3D node : nodesToDisplay) {
			for (Node3D other : node.getEdges().keySet()) {
				sum += node.distance(other);
				count++;
			}
		}
		return sum / count;
	}

	private double getAverageDistanceBetweenNonNeighbors() {
		double sum = 0.0;
		int count = 0;
		for (Node3D node1 : nodesToDisplay) {
			for (Node3D node2 : nodesToDisplay) {
				if (node1 != node2 && !node1.getEdges().containsKey(node2) && !node2.getEdges().containsKey(node1)) {
					sum += node1.distance(node2);
					count++;
				}
			}
		}
		return sum / count;
	}
	private static PhongMaterial randomMaterial() {
		return new PhongMaterial(Color.rgb(128+random.nextInt(128),128+random.nextInt(128),128+random.nextInt(128)));
	}

	// --------------------------
	private void displayNodes() {
		// If we are focused on a node, then ignore the importance limit
		System.out.println(nodesToDisplay.length + " nodes to display");
		for (int i = 0; i < nodesToDisplay.length; i++) {
			Node3D node = nodesToDisplay[i];
			node.setIsVisible(true);
			double radius = node == focusedNode?  3*sphereRadius: sphereRadius;
			Sphere sphere = drawSphere(node.getX(), node.getY(), node.getZ(), radius, randomMaterial(), 
					node.toString() + ", imp=" + numberFormat.format(node.getImportance()));
			sphere.setUserData(node);
			node.setSphere(sphere);
			shapes.add(sphere);
		}
		for (int i = 0; i < nodesToDisplay.length; i++) {
			Node3D node = nodesToDisplay[i];
			Point3D p1 = node.getPoint3D();
			for (Node3D neighbor : node.getNeighbors()) {
				if (focusedNode!=null || neighbor.isVisible()) {
					Point3D p2 = neighbor.getPoint3D();
					Cylinder cylinder = createCylinderBetween(p1, p2,node);
					cylinder.setUserData(node);
					shapes.add(cylinder);
					cylinder.setMaterial(node.getMaterial());
					world.getChildren().add(cylinder);
				}
			}
		}
	}

	private Point3D findCenterOfMass() {
		double x=0,y=0,z=0;
		for(Node3D n:nodesToDisplay) {
			x+= n.getX();
			y+= n.getY();
			z+= n.getZ();
		}
		int n=nodesToDisplay.length;
		return new Point3D(x/n,y/n,z/n);
	}
	//--------------
	private void animate() {
		final AnimationTimer timer = new AnimationTimer() {
			@Override
			public void handle(long nowInNanoSeconds) {
				final long diff=nowInNanoSeconds-requestPlaceOnePassTimeInMls;
				if (requestPlaceOnePassTimeInMls>0 && diff>50*ONE_MILLISECOND_IN_NANOSECONDS) {
					requestPlaceOnePassTimeInMls=0;
					placeOnePass(false);
				}
			}};
		timer.start();
	}
	// --------------------------
	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;		
		
		if (nodesToDisplay.length>preferredCountOfNodesShown) {
			percentToShow = 100.0*preferredCountOfNodesShown/nodesToDisplay.length;
			ConnectedComponent.stochasticMovesReps = 4; 
			ConnectedComponent.decayFactor *= 0.75; 
		}
		try {
			runCurrentImportanceAlgorithm();
			calculateMinMaxImportance();
			assignImportanceIndices();

//			PhongMaterial mat = new PhongMaterial(Color.GOLD);
//			Sphere sp=new Sphere(15); 
//			sp.setMaterial(mat);
//			root.getChildren().add(sp);
			
			root.getChildren().add(world);
			root.setDepthTest(DepthTest.ENABLE);
			Scene scene = new Scene(root, width, height, true);
			scene.setFill(Color.BLACK);
			primaryStage.setTitle(title + " -- Use the mouse & arrow keys to navigate. Use the slider to control which nodes appear, by importance. Left click on a node for details. Right click to focus;"
					+ " page up/down sets focus distance. 'h' for more help.");
			primaryStage.setScene(scene);
			primaryStage.setOnCloseRequest(r -> System.exit(0));
			handleMouse(scene);
			buildCamera(root);
			buildImportanceAlgorithmComboBox(root);
			buildStochasticCountComboBox(root);
			buildSlider(root);
			//handleKeyEvents(scene);
			buildGraphPlacementAlgorithmComboBox(root);
			buildRedoLayoutButton(root);
			scene.setCamera(camera);

			placeOnePass(true);
			randomizeColors(2);
			displayNodes();
			//showAverageDistances();
			world.setTranslateZ(0.5*Node3D.windowSize);
			animate();
			primaryStage.show();
		} catch (Throwable exc) {
			exc.printStackTrace();
		}
	}

	// public static void main(String[] args) {
	// try {
	// Application.launch(args);
	// } catch (Exception exc) {
	// exc.printStackTrace();
	// }
	// }
}
