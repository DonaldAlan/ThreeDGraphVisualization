# ThreeDGraphVisualization
A graph visualization utility in 3D JavaFX

Copyright by Don Smith, ThinkerFeeler@gmail.com

License:  The GNU Lesser General Public License (LGPL)


RUNNING:

Either run inside Eclipse or some other IDE, or you'll need to edit compile.bat, run-BuildGeneratedGraphs.bat  and run-ChooseGraphFilesAndVisualize.bat  so they point to your jdk/jre. You may need to edit the path to jfxrt.jar in those files, if you use an older version of java.

See the images in screenshots/ for sample screenshots.

Two ways to use this toolkit are:

1. ChooseGraphFileAndVisualize.java  (run-ChooseGraphFilesAndVisualize.bat)

  Run ChooseGraphFileAndVisualize.java to choose graph files in various formats (gexf, gml, and graphml) and visualize them.  
  In the graphs/ subdirectory you'll find some sample graphs.    When you run ChooseGraphFileAndVisualize.java, it will present a FileChooser dialog 
  pointing to the graphs/ subdirectory.


2.  BuildGeneratedGraphs.java  (run-BuildGeneratedGraphs.bat)

  Run BuildGeneratedGraphs.java to view some synthetic (generated) graphs. Modify the code in main(String args) to choose which graph to visualize.

 
 In the UI you can navigate in 3d with the arrow keys or by dragging with the mouse.  Lots of features: click 'h' to get a help screen.

 Graphs are laid out using stochastic relaxation of spring tensions and repulsive forces.  Vertex positions are randomly moved from their current positions, using a decreasing temperature to control the distance moved.  After each move, the cost is recalculated. Moves that lower the cost are preferred.

 There are three components to the cost:
 1. a large, constant repulsive force that prevents vertices from being placed more than a specified minimum distance from each other
 (to prevent overcrowding).
 2. a repulsive force that varies as the inverse of the Euclidean distance between vertices.
 3. a spring force (either attractive or repulsive) between nodes. The spring constant (the force of the spring) depends on the graph
 theoretic distance between the nodes.

 There are two related layout algorithms: UnrestrictedForces and ApproximateForces.

 UnrestrictedForces is appropriate for smaller graphs, as its running time is O(n*n*d*d), where n is number of vertices and d is  the (max) degree of vertices. UnrestrictedForces sets up repulsive forces (type 1 and 2 above) between each pair of vertices,  as well as spring forces between vertices at graph-theoretic distance 1 or 2 (neighbors, or neighbors of neighbors).   The resulting visualizations are more informative and attractive but are slow to build for large graphs.

 ApproximateForces running time is O(n*d*d), as it mainly forces only between pairs of vertices at graph theoretic distance 1 or 2   (neighbors or neighbors of neighbors). In addition, it adds repulsive forces of a <em>sample</em> of nodes not in the neighborhood.    The sampling represents an approximation to UnrestrictedForces.

 The layout algorithms take advantage of multi-threading if you have multiple cores.

 A UI button allows the user to choose between the layout algorithms.  For small graphs the layout defaults to UnrestrictedForces.  For large graphs it defaults to ApproximateForces.

 If you click the left mouse button on a node, it will open a window that shows details about the node's features.  Right clicking  (or clickong on "Focus" in the detail window) will zoom in on the node, showing only nodes within a configurable graph distance from the focused node.   Use the PageUp and Page Down keys to control that graph distance.

 Another trick:  closely connected subgraphs are made to have distinct colors (via random assignment and merging of colors). Type 'c' to re-assign colors.

 The public static variable Visuzlizer.preferredCountOfNodesShown defaults to the value 1000. By default, at most that number of nodes will be graphed. You can override the default via the slider at the top of the window. The order of nodes depends on the importance algorithm that the drop-down on the top-left lets you control. So, if the graph has 6000 nodes and the slider is one third of the way to right, then 2000 nodes will be displayed on the screen.

 The program can load in graphs with tens of thousands of nodes, but can visualize on the screen only thousands of nodes in a reasonable amount of time.  Graphs with tens of thousands of nodes will probably be take minutes to appear (if you adjust the slider to the right).

-------------------------------------------------------------------

 In the lib/ subdirectory you'll find several jar files:

collections-generic-4.01.jar Apache license

gs-core-1.3.jar              CeCILL-C (French version) and LGPL v3.     from http://graphstream-project.org/      Used to read some graphs.

The following Jung jars are used for importance algoritms and graph generators (not for layout).    From http://jung.sourceforge.net/         
jung-algorithms-2.0.1.jar    Berkley license 
jung-api-2.0.1.jar           Berkley license
jung-graph-impl-2.0.1.jar    Berkley license


