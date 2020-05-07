package grapher;

/*
 * @Author Don Smith, ThinkerFeeler@gmail.com
 * 
 * See README.txt for more documentation. 
 * 
 * TODO 0: Let user change diameter of edges, or hide them.
 * TODO 1: First layout the most important nodes. Then fix their positions and layout the less important nodes, in stages.
 * TODO 2: Allow the relaxation algorithms to run in the background while the UI updates, with a STOP button.
 * TODO 3: Allow different scales. It's OK if the user needs to ZOOM into see substructure.
 * TODO 4: Compare Gephi's Yifan Hu, Force Atlas
 */

import java.text.NumberFormat;
import grapher.Node3D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
	public static volatile int maxRepulsiveNodesToInclude=25;
	public static double repulsionSliderValue = -2.0;
	public static volatile double repulsionFactor = Math.exp(repulsionSliderValue);
	//--------------------
	public static enum Layout { Stochastic,Spring,Barrycenter,FruchtermanReingold, Systematic;}
	public static Layout layout = Layout.Stochastic;
	private Node3D[] nodesToDisplay = null;
	public static Node3D[] savedAllNodes=null;
	public static double distanceForOneEdge = 30;
	public static double sphereRadius = 5.5;
	public static double cylinderRadius = 1.1;
	public static String title="Visualize Graph";
	//.....
	private final static int width = 1600;
	private final static int height = 900;
	//..
	private final static String betweennessCentralityAlgorithm="BetweennessCentrality";
    private final static String pageRankAlgorithm= "Page Rank";
    private final static String degreeAlgorithm="Degree";
    private final static String closenessCentrality = "Closeness Centrality";
    private final static String randomWalkVisits="Random walk visit count";
    private final static String markovCentralityJung="Markov Centrality"; // throws exception "Matrix is singular"
    private final static String randomWalkCentralityJung="Random Walk Centrality"; 
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
	private List<Cylinder> cylinders = new ArrayList<Cylinder>();
	private final Group root = new Group();
	private Stage primaryStage;
	private MessageBox messageBox;
	static volatile int countToShow = 0; // used
	private Font tooltipFont = new Font("Times Roman",20);
	private final Slider importanceSlider=new Slider();
	private final Slider repulsionSlider=new Slider();
	private final ComboBox<String> importanceAlgorithmComboBox = new ComboBox<>();
	private final ComboBox<String> stochasticCountComboBox = new ComboBox<>();
	private final ComboBox<String> graphingAlgorithmComboBox = new ComboBox<>();
	private volatile long requestPlaceOnePassTimeInMls=0;
	private final BackgroundFill backgroundFillRedrawing = new BackgroundFill(Color.YELLOW, CornerRadii.EMPTY, Insets.EMPTY);
	private final Background backgroundRedrawing =new Background(backgroundFillRedrawing);
	private final BackgroundFill controlBackgroundFill = new BackgroundFill(Color.SKYBLUE, CornerRadii.EMPTY, Insets.EMPTY);
	private final Background controlBackground = new Background(controlBackgroundFill);

	private final Button redrawButton=new Button("Redraw");
	private volatile boolean newImportanceAlgorithm = false;
	// --------------
	static {
		numberFormat.setMaximumFractionDigits(3);
	}

	public Visualizer() {
	}
	
	private boolean intersectsSomeOtherConnectedComponent(ConnectedComponent component, Set<ConnectedComponent> set) {
		for (ConnectedComponent other : set) {
			if (other != component && other.intersects(component)) {
				return true;
			}
		}
		return false;
	}
	private void requestReplaceOnePass() {
		if (requestPlaceOnePassTimeInMls>0) {
			return;
		}
		requestPlaceOnePassTimeInMls=System.nanoTime();
		redrawButton.setBackground(backgroundRedrawing);
		redrawButton.setText("Redrawing");
	}
	public double getTotalCost() {
		double totalCost=0;
		for (ConnectedComponent connectedComponent : connectedComponents) {
			totalCost+= connectedComponent.getCost();
		}
		return totalCost;
	}
	public void placeOnePassAndRefreshNodes() {
		long startTime=System.currentTimeMillis();
	//	randomizeNodePlacements();
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
				try {
					connectedComponent.placeUsingBarrycenter();
				} catch (Throwable thr) {
					thr.printStackTrace();
					System.exit(1);
				}
				break;
			case FruchtermanReingold:
				//System.err.println("FruchtermanAndReingold not implemented");
				connectedComponent.fruchtermanAndReingold();
				break;
			case Systematic:
				connectedComponent.systematicModel();
				break;
			}
			connectedComponent.getMaxWidthHeightDepth();
			newTotalCost+= connectedComponent.getCost();
		}
		Point3D centerOfMass = findCenterOfMass();
		world.rotateAroundAndCenterOn(centerOfMass);
		//System.out.println("Center of mass = " + centerOfMass);
		
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
		redrawButton.setBackground(controlBackground);
		redrawButton.setText("Redraw");
	}
	@SuppressWarnings("unchecked")
	private void buildRepulsiveCountComboBox(Group root) {
		stochasticCountComboBox.setTranslateX(-520);
		stochasticCountComboBox.setTranslateY(-410);
		stochasticCountComboBox.setTranslateZ(1600);
	
		stochasticCountComboBox.setBackground(controlBackground);
		Tooltip tooltip = new Tooltip("Choose count of random nodes for repulsive force. Higher values result in better layouts but are slower.");
		stochasticCountComboBox.setTooltip(tooltip);
		  // Use Java Collections to create the List.
        final List<String> itemList = new ArrayList<String>();
        for(int i=0;i<=400;i+=10) {
        	itemList.add(""+i);
        }
        final ObservableList<String> observableList = FXCollections.observableList(itemList);
        stochasticCountComboBox.setItems(observableList);
        stochasticCountComboBox.setValue(""+maxRepulsiveNodesToInclude);
        stochasticCountComboBox.setOnAction( e -> {
        	String value=stochasticCountComboBox.getValue();
        	maxRepulsiveNodesToInclude=Integer.parseInt(value);
        	System.out.println("Setting repulsiveCount to " + maxRepulsiveNodesToInclude);
        	//requestReplaceOnePass();
			});
		root.getChildren().add(stochasticCountComboBox);
	}

	@SuppressWarnings("unchecked")
	private void buildImportanceAlgorithmComboBox(Group root) {
		importanceAlgorithmComboBox.setTranslateX(-750);
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
			try {
				currentImportanceAlgorithm = newAlgorithm;
				newImportanceAlgorithm=true;
				requestReplaceOnePass();
			} catch (Throwable thr) {
				thr.printStackTrace();
				System.exit(1);
			}
			});

		root.getChildren().addAll(importanceAlgorithmComboBox);
		
	}
	private void buildGraphPlacementAlgorithmComboBox(Group root) {
		graphingAlgorithmComboBox.setTranslateX(350);
		graphingAlgorithmComboBox.setTranslateY(-335);
		graphingAlgorithmComboBox.setTranslateZ(1300);
		graphingAlgorithmComboBox.setBackground(controlBackground);
		final List<String> itemList = new ArrayList<String>();
		itemList.add(Layout.Barrycenter.name());
		itemList.add(Layout.FruchtermanReingold.name());
		itemList.add(Layout.Spring.name());
		itemList.add(Layout.Stochastic.name());
		itemList.add(Layout.Systematic.name());
		final ObservableList<String> observableList = FXCollections.observableList(itemList);
		graphingAlgorithmComboBox.setItems(observableList);
		graphingAlgorithmComboBox.setValue(layout.name());
		graphingAlgorithmComboBox.setOnAction( e -> {
        	String value=graphingAlgorithmComboBox.getValue();
        	layout = Layout.valueOf(value);
		});
		root.getChildren().add(graphingAlgorithmComboBox);
	}
	
	private void runCurrentImportanceAlgorithm() {
			switch (currentImportanceAlgorithm) {
			case betweennessCentralityAlgorithm:
				Node3D.computeImportanceViaJungBetweenessCentrality(savedAllNodes);
				break;
			case pageRankAlgorithm:
				Node3D.computeImportanceViaJungPageRank(savedAllNodes);
				break;
			case degreeAlgorithm:
				Node3D.computeImportanceViaDegree(savedAllNodes);
				break;
			case randomWalkVisits:
				Node3D.computeImportanceViaRandomWalks(savedAllNodes, 20, 100);
				break;
			case randomWalkCentralityJung:
				Node3D.computeImportanceViaJungRandomWalkBetweenness(savedAllNodes);
				break;
			case markovCentralityJung:
				Node3D.computeImportanceViaJungMarkovCentrality(savedAllNodes);
				break;
			case closenessCentrality:
				Node3D.computeImportanceViaJungClosenessCentrality(savedAllNodes);
				break;
			default:
				throw new IllegalStateException();
			}
	}
	private void runCurrentImportanceAlgorithmPerComponent() {
		for (ConnectedComponent component : connectedComponents) {
			switch (currentImportanceAlgorithm) {
			case betweennessCentralityAlgorithm:
				Node3D.computeImportanceViaJungBetweenessCentrality(component.getNodes());
				break;
			case pageRankAlgorithm:
				Node3D.computeImportanceViaJungPageRank(component.getNodes());
				break;
			case degreeAlgorithm:
				Node3D.computeImportanceViaDegree(component.getNodes());
				break;
			case randomWalkVisits:
				Node3D.computeImportanceViaRandomWalks(component.getNodes(), 20, 100);
				break;
			case randomWalkCentralityJung:
				Node3D.computeImportanceViaJungRandomWalkBetweenness(component.getNodes());
				break;
			case markovCentralityJung:
				Node3D.computeImportanceViaJungMarkovCentrality(component.getNodes());
				break;
			case closenessCentrality:
				Node3D.computeImportanceViaJungClosenessCentrality(component.getNodes());
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void buildImportanceSlider(Group root) {
	        importanceSlider.setTranslateX(-200);
	        importanceSlider.setTranslateY(-286);
	        importanceSlider.setTranslateZ(1100);
	        double min=-Math.log(savedAllNodes.length);
	        importanceSlider.setMin(min);
	        importanceSlider.setMax(1); // logarithmic scale
	        {double proportionToShow = (0.0+countToShow)/savedAllNodes.length;
	        	System.out.println("proportionToShow = " + proportionToShow + ", log = " + Math.log(proportionToShow));
	        importanceSlider.setValue(1+Math.log(proportionToShow)); 
	        }
	        importanceSlider.setShowTickLabels(false);
	        importanceSlider.setShowTickMarks(true);
	        importanceSlider.setMajorTickUnit(5);
	        importanceSlider.setTooltip(new Tooltip("Control how many nodes to display, ordered by importance (logarithmic)."));
	        BackgroundFill backgroundFill = new BackgroundFill(Color.DARKGREEN, CornerRadii.EMPTY, Insets.EMPTY);
	        Background background = new Background(backgroundFill);
	        importanceSlider.setBackground(background);
	        importanceSlider.setMinWidth(width / 4);
	        root.getChildren().add(importanceSlider);
	        importanceSlider.setOnMouseReleased( e -> {
	        	double logProportionToShow=importanceSlider.getValue();
	        	double proportionToShow=Math.exp(logProportionToShow)/Math.E;
	        	// Math.E*proportionToShow= Math.exp(logProportionToShow)
	        	// log(Math.E*proportionToShow)= log(Math.exp(logProportionToShow))
	        	// 1+proportionToShow = logProportionToShow
	        	countToShow = Math.min(savedAllNodes.length,(int)Math.round(proportionToShow*savedAllNodes.length) );
	        	if (countToShow<1) {
	        		countToShow=1;
	        	}
	        	double percent = 100.0*proportionToShow;
	        	System.out.println("Showing " + countToShow + " (" + numberFormat.format(percent) + "%)");
	        	try {
	        		makeNodesToDisplayFromSavedNodesAndCountToShow();
	        		computeConnectedComponentsFromNodesToDisplay();
	        		if (e.isShiftDown()) {
	            		for(ConnectedComponent c:connectedComponents) {
	            			c.randomizePositions(1.0);
	            		}
	            	}
	        		requestReplaceOnePass();
	        	} catch (Throwable thr) {
	        		thr.printStackTrace();
	        		System.exit(1);
	        	}
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
	private void buildRepulsionSlider(Group root) {
        repulsionSlider.setTranslateX(-200);
        repulsionSlider.setTranslateY(-246);
        repulsionSlider.setTranslateZ(1100);
        repulsionSlider.setMin(-12.0);
        repulsionSlider.setMax(8.0); // logarithmic scale
        repulsionSlider.setValue(repulsionSliderValue);
        repulsionSlider.setShowTickLabels(false);
        repulsionSlider.setShowTickMarks(true);
        repulsionSlider.setMajorTickUnit(1);
        repulsionSlider.setTooltip(new Tooltip("Control repulsive force (logarthmic scale)"));
        BackgroundFill backgroundFill = new BackgroundFill(Color.DARKGREEN, CornerRadii.EMPTY, Insets.EMPTY);
        Background background = new Background(backgroundFill);
        repulsionSlider.setBackground(background);
        repulsionSlider.setMinWidth(width / 4);
        root.getChildren().add(repulsionSlider);
        repulsionSlider.setOnMouseReleased( e -> {
        	repulsionSliderValue=repulsionSlider.getValue();
        	if (repulsionSliderValue==repulsionSlider.getMin()) {
        		System.out.println("Setting repulsion factor to zero");
        		repulsionFactor= 0.0;
        	} else {
        		repulsionFactor = Math.exp(repulsionSliderValue);
        	}
        	System.out.println("RepulsionSliderValue = " + repulsionSliderValue + ", repulsionFactor = " + repulsionFactor);
        	if (e.isShiftDown()) {
        		for(ConnectedComponent c:connectedComponents) {
        			c.randomizePositions(1.0);
        		}
        	}
    		requestReplaceOnePass();
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
	
	private void buildRedrawButton(Group root) {
		redrawButton.setTranslateX(540);
		redrawButton.setTranslateY(-335);
		redrawButton.setTranslateZ(1300);
		redrawButton.setBackground(controlBackground);
		redrawButton.setOnAction( e -> {
			requestReplaceOnePass();
		});
		root.getChildren().add(redrawButton);
	}
	private void refreshNodes() {
		world.getChildren().clear();
		cylinders.clear();
		displayNodes();
		world.requestLayout();
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
	private void computeConnectedComponentsFromNodesToDisplay() {
		connectedComponents = new HashSet<>();
		Node3D.computeConnectedComponentsForDisplay(nodesToDisplay);
		for(Node3D node:nodesToDisplay) {
			connectedComponents.add(node.getConnectedComponent());
		}
		for (ConnectedComponent conn : connectedComponents) {
			conn.done();
		}
		System.out.println(connectedComponents.size() + " connected components");
	}
	//----------------------------------
	private void makeNodesToDisplayFromSavedNodesAndCountToShow() {
		nodesToDisplay = new Node3D[countToShow];
		for(int i=0;i<countToShow;i++) {
			nodesToDisplay[i]=savedAllNodes[i];
		}
	}
	// -------------------------
	private static class XformWorld extends Group {
		final Translate t = new Translate(0.0, 0.0, 0.0);
		final Rotate rx = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
		final Rotate ry = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
		final Rotate rz = new Rotate(0, 0, 0, 0, Rotate.Z_AXIS);

		private void pivot(Rotate r, Point3D point) {
			r.setPivotX(point.getX());
			r.setPivotY(point.getY());
			r.setPivotZ(point.getZ());
		}
		public void rotateAroundAndCenterOn(Point3D point) {
			pivot(rx,point);
			pivot(ry,point);
			pivot(rz,point);
			
			t.setX(-point.getX());
			t.setY(-point.getY());
			t.setZ(-point.getZ());
			
		}
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
	private Cylinder createCylinderBetween(Point3D origin, Point3D target,Node3D sourceNode) {
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

	private Sphere makeSphere(double x, double y, double z, double radius, PhongMaterial material, String toolTipMessage) {
		Sphere sphere = new Sphere(radius);
		// double opacity= square(square(square(1- distance/maxDistance)));
		// sphere.setOpacity(opacity);
		// if (count%10==0) {System.out.println(opacity);}
		sphere.setMaterial(material);
		sphere.setTranslateX(x);
		sphere.setTranslateY(y);
		sphere.setTranslateZ(z);
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
		double meanDistanceToNonNeighbors = node.meanXYZDistanceToNonNeighbors();
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
				+ "\n Importance Rank = " + numberFormat.format(1+node.getIndexInImportanceOrder())
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
		
		nodesToDisplay=getNodesNear(node,maxFocusDistance);
		placeOnePassAndRefreshNodes();
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
			final int factor=ke.isShiftDown() ? 10: 1;
			switch (ke.getCode()) {
			case Q:
				System.exit(0);
				break;
			case R: {
				// TODO: sort nodes by cost and move the costliest ones.
				if (ke.isShiftDown()|| ke.isControlDown()) {
					System.out.println("Randomize");
					final double probability = ke.isShiftDown() ? 1.0: 0.1;
					for(ConnectedComponent c:connectedComponents) {
						c.randomizePositions(probability);
					}
					refreshNodes();
				} else {
					requestReplaceOnePass();
				}
			}
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
				if (focusedNode!=null || ke.isShiftDown()) {
					for(Node3D n:savedAllNodes) {
						n.setXYZ(Node3D.windowSize*random.nextDouble(),Node3D.windowSize*random.nextDouble(),Node3D.windowSize*random.nextDouble());
					}
				}
				if (focusedNode!=null) {
					focusedNode=null;
					nodesToDisplay=savedAllNodes;
					connectedComponents=null;
					requestReplaceOnePass();
				} else {
					refreshNodes();
				}
				break;
			case LEFT: {
				world.setTranslateX(world.getTranslateX() + factor*10);
			}
				break;
			case RIGHT:
				world.setTranslateX(world.getTranslateX() - factor*10);
				break;
			case UP:
				if (ke.isControlDown()) {
					world.setTranslateY(world.getTranslateY() - factor*10);
				} else {
					world.setTranslateZ(world.getTranslateZ() - factor*10);
				}
				break;
			case DOWN:
				if (ke.isControlDown()) {
					world.setTranslateY(world.getTranslateY() + factor*10);
				} else {
					world.setTranslateZ(world.getTranslateZ() + factor*10);
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
				placeOnePassAndRefreshNodes();
				//showAverageDistances();
				break;
			case H:  {
				String message="\n\n Navigate in 3D space by dragging the mouse, or by pressing the arrow keys."
						+ "\n Use the drop-down at the top left to choose the importance algorithm that's used to rank nodes."
						//+ "\n Use the drop-down at the top left to choose the count of stochastic placements; higher values result in nicer graphs."
						+ "\n Use the slider at the top to adjust how many nodes to display, ordered by importance."
						+ "\n Use the next slider to adjust how the repulsive force (how spread out the graph is)."
						+ "\n For large graphs, it defaults to Approximate Forces; for smaller graphs it defaults to Unrestricted Forces"
						+ "\n Left-click on a node to see details. Right click to focus on that node."
						+ "\n Press Ctrl-F to search for a node by id. If found, the program will focus on that node."
						+ "\n\n Press PageUp and PageDown to adjust how many nodes are shown when focussed."
						+ "\n Press 'r' or click on 'Redraw' to optimize the layout, 'R' to randomize first"
						+ "\n Press 's' to decrease the size of the nodes and edges, 'S' to increase the sizes"
						+ "\n Press 'c' to randomize the colors."
						+ "\n Press 'q' to exit."
						;
				new MessageBox(message,"Help");
				break;
			}
			case S: {
				 final double sizeFactor = ke.isShiftDown()?1.1 : 1.0/1.1;
				 sphereRadius*= sizeFactor;
				 cylinderRadius*=sizeFactor;
				 for(Node3D n:nodesToDisplay) {
					 n.getSphere().setRadius(sphereRadius);
				 }
				 for(Cylinder c:cylinders) {
					 c.setRadius(cylinderRadius); 
				 }
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
			case INSERT:
				repulsionFactor= Math.min(25.0, factor*0.05+repulsionFactor);
				System.out.println("repulsionFactor = " + numberFormat.format(repulsionFactor));
				break;
			case DELETE:
				repulsionFactor= Math.max(0, factor*-0.05+repulsionFactor);
				System.out.println("repulsionFactor = " + numberFormat.format(repulsionFactor));
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
	private final Comparator<Node3D> comparatorDecreasing = new Comparator<Node3D>() {
		@Override
		public int compare(Node3D node1, Node3D node2) {
			return -Double.compare(node1.getImportance(), node2.getImportance());
		}};
	
	private void assignImportanceIndicesAndSortSavedAllNodes() {
		Arrays.sort(savedAllNodes, comparatorDecreasing);
		int index=0;
		for(Node3D node: savedAllNodes) {
			node.setIndexInImportanceOrder(index);
			index++;
		}
		index++;
	}

	
	private void randomizeColors(int mergeCount) {
		for (ConnectedComponent c : connectedComponents) {
			c.randomizeColors(mergeCount);
		}
		for(Cylinder c:cylinders) {
			Node3D node = (Node3D) c.getUserData();
			c.setMaterial(node.getMaterial());
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

	//----
	private double randomTranslation(double amount) {
		return amount*random.nextDouble()-0.5*amount;
	}
	//----
	private static boolean someComponentIntersects(List<ConnectedComponent> list, ConnectedComponent component) {
		final Bounds bounds = component.getGroup().getBoundsInParent();
		for(ConnectedComponent c: list) {
			if (c.getGroup().getBoundsInParent().intersects(bounds)) {
				return true;
			}
		}
		return false;
	}
	//--------------------
	private void moveConnectedComponentsAwayFromEachOther() {
		if (connectedComponents.size()==1) {
			return;
		}
		int successes = 0;
		int moves = 0;
		final List<ConnectedComponent> placed = new ArrayList<>();
		final List<ConnectedComponent> sortedListOfComponents = new ArrayList<>();
		sortedListOfComponents.addAll(connectedComponents);
		sortedListOfComponents.sort(new Comparator<ConnectedComponent>() {
			@Override
			public int compare(ConnectedComponent c1, ConnectedComponent c2) {
				return Integer.compare(c2.size(), c1.size());
			}});
		int size=sortedListOfComponents.get(0).size();
		for(ConnectedComponent c:sortedListOfComponents) {
			int s=c.size();
			if (s>size) {
				throw new IllegalStateException();
			}
			size=s;
		}
		for (ConnectedComponent connectedComponent : sortedListOfComponents) {
			double amount=0.5*Node3D.windowSize;
			for (int i = 0; i < 30; i++) {
				if (!someComponentIntersects(placed, connectedComponent)) {
					// System.out.println(connectedComponent + " does not
					// intersect\n " + connectedComponents + "\n");
					successes++;
					break;
				}
				moves++;
				Group group = connectedComponent.getGroup();
				group.setTranslateX(randomTranslation(amount) + group.getTranslateX());
				group.setTranslateY(randomTranslation(amount) + group.getTranslateY());
				group.setTranslateZ(randomTranslation(amount) + group.getTranslateZ());
			}
			placed.add(connectedComponent);
			amount+= 50;
		}
		System.out.println("Moving connectedComponents: successes count = " + successes 
				+ " out of " + connectedComponents.size() + " connected components with " + moves
				+ " moves");
	}
	// --------------------------
	private void displayNodes() {
		// If we are focused on a node, then ignore the importance limit
		System.out.println(countToShow + " nodes to display in " + connectedComponents.size() + " connected components");
		for(ConnectedComponent component: connectedComponents) {
			final Group group = new Group();
			component.setGroup(group);
			world.getChildren().add(group);
			for(Node3D node: component.getNodes()) {
				double radius = node == focusedNode?  3*sphereRadius: sphereRadius;
				Sphere sphere = makeSphere(node.getX(), node.getY(), node.getZ(), radius, randomMaterial(), 
						node.toString() + ", imp=" + numberFormat.format(node.getImportance()));
				sphere.setUserData(node);
				group.getChildren().add(sphere);
				node.setSphere(sphere);
			}
			for(Node3D node:component.getNodes()) {
				Point3D p1 = node.getPoint3D();
				for (Node3D neighbor : node.getNeighbors()) {
					if (focusedNode!=null || neighbor.isVisible()) {
						Point3D p2 = neighbor.getPoint3D();
						Cylinder cylinder = createCylinderBetween(p1, p2,node);
						cylinder.setUserData(node);
						cylinders.add(cylinder);
						cylinder.setMaterial(node.getMaterial());
						group.getChildren().add(cylinder);
					}
				}
			}
		}
		moveConnectedComponentsAwayFromEachOther();
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
					if (newImportanceAlgorithm) {
						newImportanceAlgorithm=false;
						runCurrentImportanceAlgorithm();
						assignImportanceIndicesAndSortSavedAllNodes();
						makeNodesToDisplayFromSavedNodesAndCountToShow();
						computeConnectedComponentsFromNodesToDisplay();
					}
					placeOnePassAndRefreshNodes();
				}
			}};
		timer.start();
	}
	// --------------------------
	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;		
		countToShow = savedAllNodes.length;
		if (savedAllNodes.length>preferredCountOfNodesShown) {
			countToShow = Math.min(savedAllNodes.length,preferredCountOfNodesShown);
			System.out.println("Showing " + countToShow + " nodes");
		}
		try {
		
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
			buildRepulsiveCountComboBox(root); 
			buildImportanceSlider(root);
			buildRepulsionSlider(root);
			//handleKeyEvents(scene);
			buildGraphPlacementAlgorithmComboBox(root);
			buildRedrawButton(root);
			scene.setCamera(camera);

			runCurrentImportanceAlgorithm();
			assignImportanceIndicesAndSortSavedAllNodes();
			makeNodesToDisplayFromSavedNodesAndCountToShow();
			computeConnectedComponentsFromNodesToDisplay();
			placeOnePassAndRefreshNodes();
			randomizeColors(2);
			
			//showAverageDistances();
			world.setTranslateY(0.05*Node3D.windowSize);
			world.setTranslateZ(0.9*Node3D.windowSize);
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
