# ThreeDGraphVisualization
A graph visualization utility in 3D JavaFX

Copyright by Don Smith, ThinkerFeeler@gmail.com

License:  The GNU Lesser General Public License (LGPL)


RUNNING:

Execute run-ChooseGraphFileAndVisualize.sh  or  run-ChooseGraphFileAndVisualize.bat

I've tested it with Java 8.


See the images in screenshots/ for sample screenshots.

Two ways to use this toolkit are:

1. ChooseGraphFileAndVisualize.java  (run-ChooseGraphFilesAndVisualize.sh/bat)

  Run ChooseGraphFileAndVisualize.java to choose graph files in various formats (gexf, gml, and graphml) and visualize them.  
  In the graphs/ subdirectory you'll find some sample graphs.    When you run ChooseGraphFileAndVisualize.java, it will present a FileChooser dialog 
  pointing to the graphs/ subdirectory.


2.  BuildGeneratedGraphs.java  

  Run BuildGeneratedGraphs.java to view some synthetic (generated) graphs. Modify the code in main(String args) to choose which graph to visualize.

 
 In the UI you can navigate in 3d with the arrow keys or by dragging with the mouse.  Lots of features: click 'h' to get a help screen.

 Graphs are laid out, by default, using stochastic relaxation of spring tensions and repulsive forces.  Vertex positions are randomly moved from their current positions, using a decreasing temperature to control the distance moved.  After each move, the cost is recalculated. Moves that lower the cost are preferred.

 The layout algorithms take advantage of multi-threading if you have multiple cores.

 There are two components to the cost:
 1. an attactive force between nodes that share an edge. This is always included if there is an edge between two nodes.
 (to prevent overcrowding).
 2. a repulsive force that varies as the inverse of the Euclidean distance between vertices. But this is done only stochastically.


 A UI button allows the user to choose between the layout algorithms.  

 If you click the left mouse button on a node, it will open a window that shows details about the node's features.  Right clicking  (or clicking on "Focus" in the detail window) will zoom in on the node, showing only nodes within a configurable graph distance from the focused node.   Use the PageUp and Page Down keys to control that graph distance.

 Another trick:  closely connected subgraphs are made to have distinct colors (via random assignment and merging of colors). Type 'c' to re-assign colors.

 The public static variable Visuzlizer.preferredCountOfNodesShown defaults to the value 1000. By default, at most that number of nodes will be graphed. You can override the default via the slider at the top of the window. The order of nodes depends on the importance algorithm that the drop-down on the top-left lets you control. The slider is on a logarithmic scale; if you set it all the way to the right, all nodes will be shown, but layout and rendering may be slow.

 The second slider at the top controls the repulsive force between nodes.

 Click 'h' for help.

 The program can load in graphs with tens of thousands of nodes, but can visualize on the screen only thousands of nodes in a reasonable amount of time.  Graphs with tens of thousands of nodes will probably be take minutes to appear (if you adjust the slider to the right).

-------------------------------------------------------------------

