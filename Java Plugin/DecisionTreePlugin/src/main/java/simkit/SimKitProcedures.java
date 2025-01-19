package simkit;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.RealMatrix;
import org.ejml.simple.SimpleMatrix;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.util.Pair;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import definition.EdgeList2;
import definition.NodeList2;
import eigendecomposed.EigenCalculation;
import eigendecomposed.MatrixCalculation;
import global.Neo4jGraphHandler;
import graph.DistanceMeasureNodes;
import graph.GraphTransform;
import graph.ReadCsvTestData;

/**
 *
 * This class is used to fetch nodes from graph database or from csv and call the functions to generate decision tree
 * with confusion matrix, generation time and prediction time for the output
 *
 * @author minh dung
 *
 */

public class SimKitProcedures implements AutoCloseable{

	private static Driver driver;
	private static List<Record> dataKey = new ArrayList<>();
	private static ArrayList<String> mapNodeList =  new ArrayList<String>();
	private static List<Double> trueNodeLabels =  new ArrayList<Double>();
	private static List<Double> predictedNodeLabels =  new ArrayList<Double>();

	private static String uri;
    private static String username;
    private static String password;

	@Context
    public Log log;

	/**
	 * Creation of driver object using bolt protocol
	 * @param uri Uniform resource identifier for bolto
	 * @param user Username
	 * @param password Password
	 */
	public SimKitProcedures( String uri, String user, String password )
	{
		driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );

	}

	/**
	 * Empty constructor
	 */
	public SimKitProcedures()
	{
		driver = null;
	}
	
	/**
	 * This function is used to initialize the SimKit
	 * @param uri The URI of DB
	 * @param user The Username for DB
	 * @param pass The Password for DB
	 * @throws Exception if connection to Neo4j fails
	 * @author Jonas Heinzmann
	 */
	@UserFunction
	public String initSimKit(@Name("URI") String uri, @Name("User") String user, @Name("Password") String pass) {
        String output;
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass));
            // Attempt to establish a connection to verify credentials and availability
            driver.verifyConnectivity();
            output = "Initialization succesfull";
            // Store credentials if needed
            SimKitProcedures.uri = uri;
            SimKitProcedures.username = user;
            SimKitProcedures.password = pass;
        } catch (ServiceUnavailableException e) {
            output = "Connection error: Service unavailable";
        } catch (AuthenticationException e) {
            output = "Connection error: Authentication failed";
        } catch (Neo4jException e) {
            output = "Connection error: " + e.getMessage();
        } finally {
            if (driver != null) {
                driver.close();
            }
        }
        return output;
    }

    public Driver getDriver() {
        return driver;
    }

	/**
	 * Close the driver object
	 */
	@Override
	public void close() throws Exception
	{
		driver.close();
	}

	@UserFunction
	public String csvToGraph(@Name("data_path") String data_path, @Name("distance_measure") String distance_measure, @Name("graph_type") String graph_type, @Name("parameter") String parameter,@Name("remove_column") String remove_columns) throws Exception {


		String confusionMatrix = "";
		try ( SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password) )
		{

			if(data_path == null && distance_measure == null) {
                throw new Exception("Missing dataPath or distance measure type");
			}else {
				String graphName = null;
				Double[][] adj_mat = null;
				String[] removeList = remove_columns.split(",");
				List<String> removeListNew = Arrays.stream(removeList).collect(Collectors.toList());
				ReadCsvTestData readCsvTestData = new ReadCsvTestData(data_path);
//				//ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(data_path,IndexColumn);
				ArrayList<NodeList2> nodePropertiesList = readCsvTestData.readCsvFileToMap(data_path);
//                ArrayList<NodeList2> nodePropertiesList_copy = readCsvTestData.readCsvFileToMap(data_path);
				Double[][] DistanceMatrix = getDistanceMatrixFromNodes(distance_measure,nodePropertiesList,removeListNew);

				if(graph_type.equals("full")) {
					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,parameter);
					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
					graphName = graph_type.concat("_"+parameter);
				}
				if(graph_type.equals("eps")) {
					Double espilonValue = Double.parseDouble(parameter);
					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,espilonValue);
					graphName = graph_type.concat("_"+parameter);
				}
				if(graph_type.equals("knn")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,parameter);
					adj_mat = ReadCsvTestData.calculateKNNGraph(DistanceMatrix,knn);
					graphName = graph_type.concat("_"+parameter);
				}
				if(graph_type.equals("mknn")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,parameter);
					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
					graphName = graph_type.concat("_"+parameter);
				}
				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adj_mat);

				for (NodeList2 node : nodePropertiesList) {
					Neo4jGraphHandler.createNodeGraph(graphName.concat("new"), "Created nodes succesfully!\n", node, connector.getDriver());
				}

				for (int i = 0; i < edgeList.size(); i++) {
					EdgeList2 edgeListDetail = edgeList.get(i);
					if(edgeListDetail.getWeight()==0.0){
						continue;
					}
					Neo4jGraphHandler.createRelationshipGraph(graphName.concat("new"), "Created similarity graph succesfully!\n", edgeListDetail, connector.getDriver());

				}

			}
			return "Create fully connected graph successful, " + confusionMatrix;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
	
	/**
	 * Creates a Similarity Transform graph based on distance measure, affinity matrix type, hyperparameter, excluded columns.
	 * @param  params Set of parameters
	 * @return String indicating the success of the graph creation and creates Graph with Nodes and Relationships in Neo4j.
	 * @throws Exception If an error occurs while creating the Laplacian graph in Neo4j.
	 */
//	@UserFunction
//	public String nodePropertyToGraph(@Name("params") Map<String, Object> params) throws Exception {
//
//	    String label = (String) params.getOrDefault("label", "Iris");
//	    String distanceMeasure = (String) params.getOrDefault("distance_measure", "euclidean");
//	    String graphType = (String) params.getOrDefault("graph_type", "full");
//	    String parameter = (String) params.getOrDefault("parameter", "7");
//	    String remove_columns = (String) params.getOrDefault("remove_column", "index,target");
//
//		StringBuilder outputString = new StringBuilder("Graph Data: ");
//		try ( SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password) )
//		{
//
//
//			if(label == null && distanceMeasure == null) {
//                throw new Exception("Missing dataPath or distance measure type");
//			}else {
//				String graphName = "affinity_";
//				//ArrayList<NodeList2> nodePropertiesList = Neo4jGraphHandler.retrieveNodeListFromNeo4jSimilarityGraph(label, connector.getDriver());
//				org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> nodeData = Neo4jGraphHandler.retrieveNodeListFromNeo4jSimilarityGraph(label, connector.getDriver());
//				ArrayList<NodeList2> nodePropertiesList = nodeData.getLeft();
//				//				ArrayList<NodeList2> nodePropertiesList_copy = Neo4jGraphHandler.retrieveNodeListFromNeo4j(label, connector.getDriver());
//				String[] removeList = remove_columns.split(",");
//				List<String> removeListNew = Arrays.stream(removeList).collect(Collectors.toList());
////				Double[][] DistanceMatrix = GraphTransform.euclideanDistance(nodePropertiesList);
//				Double[][] DistanceMatrix = getDistanceMatrixFromNodes(distanceMeasure,nodePropertiesList,removeListNew);
//				Double[][] adj_mat = null;
//
//				if(graphType.equals("full")) {
//					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,parameter);
//					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
//					graphName = graphName.concat(graphType + "_" + parameter.replace(".", "_"));
//				}
//				else if(graphType.equals("eps")) {
//					Double espilonValue = Double.parseDouble(parameter);
//					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,espilonValue);
//					graphName = graphName.concat(graphType + "_" + parameter.replace(".", "_"));
//
//				}
//				else if(graphType.equals("knn")) {
//					int[][] knn = ReadCsvTestData.calculateKNNIndices(DistanceMatrix,parameter);
//					adj_mat = ReadCsvTestData.calculateKNNGraphWithIndices(DistanceMatrix,knn);
//					graphName = graphName.concat(graphType + "_" + parameter.replace(".", "_"));
//
//				}
//				else if(graphType.equals("mknn")) {
//					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,parameter);
//					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
//					graphName = graphName.concat(graphType + "_" + parameter.replace(".", "_"));
//				} else {
//                    throw new Exception("Invalid graph_type specified.");
//				}
//				
//				outputString.append("\n\nAdjacency Matrix:\n").append(doubleToString(adj_mat));
//				
//				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adj_mat);
//				graphName += "_" + label;
////				Neo4jGraphHandler.bulkDeleteNodesWithBatching(graphName, connector.getDriver());
//
//
//				//for (EdgeList edgeListDetail : edgeList) {
//				for (NodeList2 node : nodePropertiesList) {
//					Neo4jGraphHandler.createNodeGraph(graphName, "Created nodes succesfully!\n", node, connector.getDriver());
//				}
//
//				for (int i = 0; i < edgeList.size(); i++) {
//					EdgeList2 edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//					Neo4jGraphHandler.createRelationshipGraph(graphName, "Created similarity graph succesfully!\n", edgeListDetail, connector.getDriver());
//
//				}
//
//			}
//			return "Create fully connected graph successful, " + outputString;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//
//	}

	public static Double[][] getDistanceMatrixFromNodes(String distanceMeasure, ArrayList<NodeList2> nodePropertiesList, List<String> removeList) {
		Double[][] DistanceMatrix = null;

		switch (distanceMeasure) {
			case "euclidean":
				DistanceMatrix = DistanceMeasureNodes.euclideanDistance(nodePropertiesList,removeList);
				break;
			case "manhattan":
				DistanceMatrix = DistanceMeasureNodes.manhattanDistance(nodePropertiesList,removeList);
				break;
			case "canberra":
				DistanceMatrix = DistanceMeasureNodes.canberraDistance(nodePropertiesList,removeList);
				break;
			case "cosine":
				DistanceMatrix = DistanceMeasureNodes.cosineSimilarity(nodePropertiesList,removeList);
				break;
			case "jaccard":
				DistanceMatrix = DistanceMeasureNodes.jaccardCoefficient(nodePropertiesList,removeList);
				break;
			case "bray_curtis":
				DistanceMatrix = DistanceMeasureNodes.brayCurtisDistance(nodePropertiesList,removeList);
				break;
			default:
				System.out.println("Invalid distance measure type");
		}

		return DistanceMatrix;

	}
	
	@UserFunction
	public String nodePropertyToGraph(@Name("params") Map<String, Object> params) throws Exception {

	    String label = (String) params.getOrDefault("label", "Iris");
	    String distance_measure = (String) params.getOrDefault("distance_measure", "euclidean");
	    String graphType = (String) params.getOrDefault("graph_type", "full");
	    String parameter = (String) params.getOrDefault("parameter", "7");
	    String remove_columns = (String) params.getOrDefault("remove_column", "index,target");

		try ( SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password) )
		{


			if(label == null && distance_measure == null) {
                throw new Exception("Missing dataPath or distance measure type");
			}else {
				String graph_name = "affinity_";
	            ArrayList<NodeList2> node_properties_list;
	            String property_names = "";
	            String identifier = "";
	            
                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(label, connector.getDriver());
                node_properties_list = node_data.getLeft();
                identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), label);
                
                property_names = node_data.getRight();

                String[] remove_list = remove_columns.split(",");
                List<String> remove_list_new = Arrays.stream(remove_list).collect(Collectors.toList());

                Double[][] distance_matrix = getDistanceMatrixFromNodes(distance_measure, node_properties_list, remove_list_new);
                Double[][] adj_mat = null;

				if(graphType.equals("full")) {
					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(distance_matrix,parameter);
					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(distance_matrix,sigmas);
					graph_name = graph_name.concat(graphType + "_" + parameter.replace(".", "_"));
				}
				else if(graphType.equals("eps")) {
					Double espilonValue = Double.parseDouble(parameter);
					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(distance_matrix,espilonValue);
					graph_name = graph_name.concat(graphType + "_" + parameter.replace(".", "_"));

				}
				else if(graphType.equals("knn")) {
					int[][] knn = ReadCsvTestData.calculateKNNIndices(distance_matrix,parameter);
					adj_mat = ReadCsvTestData.calculateKNNGraphWithIndices(distance_matrix,knn);
					graph_name = graph_name.concat(graphType + "_" + parameter.replace(".", "_"));

				}
				else if(graphType.equals("mknn")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(distance_matrix,parameter);
					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(distance_matrix,knn);
					graph_name = graph_name.concat(graphType + "_" + parameter.replace(".", "_"));
				} else {
                    throw new Exception("Invalid graph_type specified.");
				}
				
                ArrayList<EdgeList2> edge_list = GraphTransform.calculateEdgeList(node_properties_list, adj_mat);

                graph_name += "_" + label;

            	Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name, connector.getDriver(), identifier);   
                
            	Neo4jGraphHandler.bulkCreateNodes(graph_name, node_properties_list, connector.getDriver(), identifier);
                Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name, edge_list, connector.getDriver(), identifier);


			}
			return "Create fully connected graph successful!";
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
	

	@UserFunction
	public String nodePropertyEigen(@Name("params") Map<String, Object> params) throws Exception {

	    String node_label = (String) params.getOrDefault("node_label", "affinity_full_7_Iris");
	    String laplacian_type = (String) params.getOrDefault("laplacian_type", "sym");
	    Double number_of_eigenvectors = ((Number) params.getOrDefault("number_of_eigenvectors", 3)).doubleValue();

	    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
	        if (node_label == null) {
                throw new Exception("No nodes found with the label: " + node_label);
	        }

	        // Retrieve node and edge list from Neo4j and compute matrices
            org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
            ArrayList<NodeList2> node_properties_list = node_data.getLeft();
            String identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), node_label);
            
            ArrayList<EdgeList2> edge_list = Neo4jGraphHandler.retrieveEdgeList(node_label, connector.getDriver());	            
	        
            RealMatrix adjacency_matrix = MatrixCalculation.convertToAdjacencyMatrix(edge_list,node_properties_list);
            RealMatrix degree_matrix = MatrixCalculation.calculateDegreeMatrix(adjacency_matrix);
            RealMatrix laplacian_matrix = MatrixCalculation.calculateLaplacianMatrix(degree_matrix, adjacency_matrix, laplacian_type);

	        // Calculate eigenvalues and eigenvectors
	        Object eigen_result_obj;
	        try {
	            eigen_result_obj = EigenCalculation.calculateEigen(laplacian_matrix, number_of_eigenvectors);
	        } catch (IllegalArgumentException e) {
	        	throw new Exception("Invalid input: " + e.getMessage());
	        } catch (RuntimeException e) {
	        	throw new Exception("Error in eigen decomposition: " + e.getMessage());
	        }

	        // Cast the result to EigenResult
	        EigenCalculation.EigenResult eigen_result = (EigenCalculation.EigenResult) eigen_result_obj;

	        // Create edge list based on the eigen decomposition result
	        ArrayList<EdgeList2> edge_list_eigen = EigenCalculation.createEdgeList(node_properties_list, eigen_result.X, edge_list);

	        // Create a new graph name and remove existing nodes with that label
	        String graph_name = "eigen_" + laplacian_type + "_" + Math.round(number_of_eigenvectors) + "_" + node_label;
            
            Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name, connector.getDriver(), identifier);

            Neo4jGraphHandler.bulkCreateNodesWithEigen(graph_name, node_properties_list, eigen_result.X, connector.getDriver(), identifier);

            Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name, edge_list_eigen, connector.getDriver(), identifier);

	        return "Create eigendecomposed graph successful!";
	    } catch (Neo4jException e) {
	        throw new Exception("Error creating Eigendecomposed graph: " + e.getMessage());
	    }
	}

	@UserFunction
	public String mapNodes(@Name("nodeSet") String nodeSet, @Name("overlook") String overLook) throws Exception {
	    String listOfData = "";
	    String[] overLookArray = new String[0];
	    mapNodeList.clear();
	    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
	        if (!overLook.isEmpty()) {
	            overLookArray = overLook.split(",");
	        }
	        queryData(nodeSet);
	        for (Record key : dataKey) {
	            List<Pair<String, Value>> values = key.fields();
	            for (Pair<String, Value> nodeValues : values) {
	                if ("n".equals(nodeValues.key())) {
	                    Value value = nodeValues.value();
	                    String valueOfNode = getNodeValues(value, overLookArray);
	                    mapNodeList.add(valueOfNode);
//	                    listOfData = listOfData + valueOfNode + " | ";
	                    listOfData = mapNodeList.toString();
	                }
	            }
	        }
	    }
	    return "Map all node data: " + listOfData;
	}
	
	 /**
	 * Procedure for k-means clustering and visualization in neo4j
	 * @param params Type of node
	 * @return Cluster result and visualization
	 * @throws Exception
	 */
	@UserFunction
	@Description("K-means clustering function")
//	public String kMeans(@Name("params") Map<String, Object> params) throws Exception {
	public double kMeans(@Name("params") Map<String, Object> params) throws Exception {

	    predictedNodeLabels.clear();
	
		String nodeSet = (String) params.getOrDefault("nodeSet", "eigen_sym_3_affinity_full_7_Iris");
		String numberOfCentroid = (String) params.getOrDefault("numberOfCentroid", "3");
		String numberOfInteration = (String) params.getOrDefault("numberOfInteration", "100");
		String distanceMeasure = (String) params.getOrDefault("distanceMeasure", "euclidean");
		String originalNodeSet = (String) params.getOrDefault("originalSet", "Iris");
		String overLook = (String) params.getOrDefault("overlook", "target,sepal_length,sepal_width,petal_length,petal_width");
		String overlookOriginal = (String) params.getOrDefault("overlookOriginal", "target");
		boolean kmeanBool = (Boolean) params.getOrDefault("useKmeanForSilhouette", false);
		int seed = ((Number) params.getOrDefault("seed", 42)).intValue();
	
	    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
	        int numCentroids = Integer.parseInt(numberOfCentroid);
	        int numIterations = Integer.parseInt(numberOfInteration);
	        double centroidNumber = 1.0;
			//Clear Existing node Labels
			String nodeLabel = "Clustering_" + nodeSet;

			Neo4jGraphHandler.deleteExistingNodeLabels(nodeLabel, connector.getDriver());
			
	        ArrayList<String> mapNodeList = parseNodeValues(nodeSet, overLook.split(","));
	        ArrayList<String> mapNodeOriginalList = parseNodeValues(originalNodeSet, overlookOriginal.split(","));
    
	        HashMap<String, ArrayList<String>> kmeanAssign = Unsupervised.KmeanClust(
	            mapNodeList, numCentroids, numIterations, distanceMeasure, false, new ArrayList<>(), seed
	        );

		    log.info("mapNodeList ..." + mapNodeList);
	        
			HashMap<String, ArrayList<String>> cleanedKmeanAssign = Unsupervised.removeIndexAndId(kmeanAssign);
	
	        double averageSilhouetteCoefficientValue;
	        if (kmeanBool) {
				// Remove index and id from kmeanAssign
	            averageSilhouetteCoefficientValue = Unsupervised.averageSilhouetteCoefficient(cleanedKmeanAssign, distanceMeasure);
	        } else {
	            HashMap<String, ArrayList<String>> mappedNodes = Unsupervised.replaceValuesWithOriginalSet(kmeanAssign, mapNodeOriginalList, log);
	            averageSilhouetteCoefficientValue = Unsupervised.averageSilhouetteCoefficient(mappedNodes, distanceMeasure);
	        }
	        
	        processClusters(connector, nodeSet, cleanedKmeanAssign, centroidNumber, distanceMeasure);
	
//	        return "The average Silhouette Coefficient value is: " + averageSilhouetteCoefficientValue + " predicted labels: " + predictedNodeLabels;
	        return averageSilhouetteCoefficientValue;
	    }
	}
	
	/**
	 * Parses node values from the dataset based on overlook fields.
	 */
	private ArrayList<String> parseNodeValues(String nodeSet, String[] overlookFields) throws Exception {
	    ArrayList<String> nodeList = new ArrayList<>();
	    queryData(nodeSet);
	
	    for (Record record : dataKey) {
	        for (Pair<String, Value> pair : record.fields()) {
	            if ("n".equals(pair.key())) {
	                String value = getNodeValues(pair.value(), overlookFields);
	                nodeList.add(value);
	            }
	        }
	    }
        System.out.println("parsed nodeList: " + nodeList);
	    return nodeList;
	}
	
	/**
	 * Processes clusters and connects nodes based on the k-means result.
	 */
	private void processClusters(SimKitProcedures connector, String nodeSet,
	                             HashMap<String, ArrayList<String>> kmeanAssign,
	                             double centroidNumber, String distanceMeasure) throws Exception {
	    DecimalFormat decimalFormat = new DecimalFormat("#.###");
	    log.info("process Cluster starts ..." + kmeanAssign);
	    for (String centroid : kmeanAssign.keySet()) {
	        ArrayList<String> clusterNodes = kmeanAssign.get(centroid);
	
	        for (String clusterNode : clusterNodes) {
	            predictedNodeLabels.add(centroidNumber);
	
	            double distance = Unsupervised.calculateDistance(clusterNode, centroid, distanceMeasure);
	            String formattedDistance = decimalFormat.format(distance);
	    	    log.info("centroid ..." + centroid);
	    	    log.info("clusterNode ..." + clusterNode);


	            connectNodes(nodeSet, "create relationship in kmean node", centroid, clusterNode, Double.parseDouble(formattedDistance), getDriver());
	        }
	        centroidNumber += 1.0;
	    }
	}

	 /**
	 * Procedure for calculating adjusted rand index
	 * @param params Set of parameters (node label, true label column's name)
	 * @return Adjusted rand index score
	 * @throws Exception
	 */
    @UserFunction
//	public String adjustedRandIndex(@Name("params") Map<String, Object> params) throws Exception {
	public Map<String, Object> adjustedRandIndex(@Name("params") Map<String, Object> params) throws Exception {

	    String nodeSet = (String) params.getOrDefault("nodeSet", "Iris");
	    String trueLabels = (String) params.getOrDefault("trueLabels", "target");
	    
	    if(predictedNodeLabels.size()==0)
	    {
	    	throw new Exception(" predicted Labels is null, please run kmean clustering to add the predicted labels");
	    }
	    else {
	    	String listOfData = "";
	    	Double adjustedRandIndexValue = 0.0;
		    trueNodeLabels.clear();
		    List<String> stringTrueNodeLabelsList = new ArrayList<String>();
		    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
		        queryData(nodeSet);
		        for (Record key : dataKey) {
		            List<Pair<String, Value>> values = key.fields();
		            for (Pair<String, Value> nodeValues : values) {
		                if ("n".equals(nodeValues.key())) {
		                    Value value = nodeValues.value();
		                    StringBuilder nodeLabel = new StringBuilder();
		                    for (String nodeKey : value.keys()) {
		                    	if(nodeKey.equals(trueLabels))
		                    	{
		                    		try {
		                	            double num = Double.parseDouble(String.valueOf(value.get(nodeKey)));
	                	            	trueNodeLabels.add(num);
	                	            	listOfData = listOfData + num;
	//	                	            	nodeLabel.append(getStringValue(nodeLabel)).append(nodeKey).append(":").append(value.get(nodeKey));
		                	        } catch (NumberFormatException e) {
		                	            System.out.println(value.get(nodeKey) + " is not a number.");
		                	            stringTrueNodeLabelsList.add(String.valueOf(value.get(nodeKey)));
		                	        }
		                    	}
		                    }
		                }
		            }
		        }
		        if(stringTrueNodeLabelsList.size() != 0 )
		        {
		        	trueNodeLabels =  convertStringLabels(stringTrueNodeLabelsList);
		        }
		        
		        if(trueNodeLabels.size() != predictedNodeLabels.size())
		        {
		        	throw new Exception("true labels size: " + trueNodeLabels +" and predicted labels:" + predictedNodeLabels + " does not have the same size");
		        }
		        else {
		        	adjustedRandIndexValue = calculateAdjustedRandIndex(trueNodeLabels, predictedNodeLabels);
				}
		    }
		    
		    Map<String, Object> result = new HashMap<>();
		    result.put("adjustedRandIndex", adjustedRandIndexValue);
		    result.put("trueLabels", trueNodeLabels);
		    result.put("predictedLabels", predictedNodeLabels);

		    return result;
		    
//		    return adjustedRandIndexValue;
//		    return "ajusted rand index of " + nodeSet + " is: " + adjustedRandIndexValue ;
//		    return "true labels: " + trueNodeLabels + " -- predicted labels: " + predictedNodeLabels + " -- adjustedRandIndexValue: " + adjustedRandIndexValue;
	    }
	}
    

    @UserFunction
    @Description("Calculate the mean of the Silhouette Coefficients for all point")
	public String averageSilhouetteCoefficient(@Name("nodeSet") String nodeSet, @Name("numberOfCentroid") String numberOfCentroid, @Name("numberOfInteration") String numberOfInteration, @Name("distanceMeasure") String distanceMeasure, @Name("Seed") Number seed) throws Exception
	{
    	if(nodeSet != null)
    	{
			String averageSilhouetteCoefficientString = "The average Silhouette Coefficient value is: ";
			HashMap<String, ArrayList<String>> kmeanAssign = new HashMap<String, ArrayList<String>>();
			int numberOfCentroidInt = Integer.parseInt(numberOfCentroid);
			int numberOfInterationInt = Integer.parseInt(numberOfInteration);
			ArrayList<String> debug = new ArrayList<>();
			kmeanAssign = Unsupervised.KmeanClust(mapNodeList, numberOfCentroidInt, numberOfInterationInt, distanceMeasure, false, debug, (int) seed);
			double averageSilhouetteCoefficientValue = Unsupervised.averageSilhouetteCoefficient(kmeanAssign, distanceMeasure);
	        return averageSilhouetteCoefficientString + averageSilhouetteCoefficientValue ;
		}
    	else
    	{
    		return null;
    	}
	}
    
    
	/**
	 * This function is used to query the data from graph database
	 * @param nodeType type of node
	 */
	public void queryData( final String nodeType)
	{
		try ( Session session = getDriver().session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					Result result = tx.run( "MATCH (n:" + nodeType + ") RETURN n");
					dataKey = result.list();
					return "Query Successful";
				}
			} );
		}
	}
    
//	public void connectNodes(final String nodeType, final String message, final String nodeCentroid, final String nodeCluster, final double distance) throws Exception
//    {
//    	final String name = "kmean";
//    	try ( Session session = getDriver().session() )
//	        {
//	
//    		log.info("kmeans connection starts....");
//        	String identifier = Neo4jGraphHandler.resolveDynamicIdentifier(getDriver(), nodeType);
//            String greeting = session.writeTransaction( new TransactionWork<String>()
//            {
//                @Override
//                public String execute( Transaction tx )
//                {
//					// First, find and delete the existing node if it exists
//					String deleteQuery = "MATCH (a:Clustering_" + nodeType + " {" + nodeCentroid + "}) " +
//										 "DETACH DELETE a";
//					//tx.run(deleteQuery);
//					// Then create the new nodes and relationship
//					String createQuery = "MERGE (a:Clustering_" + nodeType + " {" + nodeCentroid + "}) " +
//										 "MERGE (b:Clustering_" + nodeType + " {" + nodeCluster + "}) " +
//										 "MERGE (a)-[r:link]->(b) " +
//										 "SET r.distance = " + distance + " " +
//										 "RETURN a.message";
//					
////					String createQuery = "MERGE (a:Clustering_" + nodeType + " {" + identifier + ": '" + nodeCentroid + "'}) " +
////		                     "MERGE (b:Clustering_" + nodeType + " {" + identifier + ": '" + nodeCluster + "'}) " +
////		                     "MERGE (a)-[r:link]->(b) " +
////		                     "SET r.distance = " + distance + " " +
////		                     "RETURN a";
//
//
//					Result result = tx.run(createQuery);
//					return result.single().get(0).asString();
//                }
//            } );
//		}
//	}
	

	public void connectNodes(final String nodeType, final String message, final String nodeCentroid, final String nodeCluster, final double distance, Driver driver) throws Exception
  {
  	final String name = "kmean";
  	try ( Session session = driver.session() )
	        {
	
  		log.info("kmeans connection starts....");
      	String identifier = Neo4jGraphHandler.resolveDynamicIdentifier(getDriver(), nodeType);
      	
          String greeting = session.writeTransaction( new TransactionWork<String>()
          {
              @Override
              public String execute( Transaction tx )
              {
            	  
      	        // Resolve identifier for dynamic node matching
      	        String identifier = Neo4jGraphHandler.resolveDynamicIdentifier(getDriver(), nodeType);

      	        // Log resolved identifier and input parameters
      	        log.info("Resolved Identifier: " + identifier);
      	        log.info("Node Type: " + nodeType);
      	        log.info("Node Centroid: " + nodeCentroid);
      	        log.info("Node Cluster: " + nodeCluster);
      	        log.info("Distance: " + distance);

        	  
				// First, find and delete the existing node if it exists
				String deleteQuery = "MATCH (a:Clustering_" + nodeType + " {" + nodeCentroid + "}) " +
									 "DETACH DELETE a";
				//tx.run(deleteQuery);
				// Then create the new nodes and relationship
				String createQuery = "MERGE (a:Clustering_" + nodeType + " {" + nodeCentroid + "}) " +
									 "MERGE (b:Clustering_" + nodeType + " {" + nodeCluster + "}) " +
									 "MERGE (a)-[r:link]->(b) " +
									 "SET r.distance = " + distance + " " +
									 "RETURN a.message";

				Result result = tx.run(createQuery);
				return result.single().get(0).asString();
              }
          } );
		}
	}
	
	private String getNodeValues(Value value, String[] overLookArray) {
	    StringBuilder valueOfNode = new StringBuilder();
	    for (String nodeKey : value.keys()) {
	        if (overLookArray.length > 0 && Arrays.asList(overLookArray).contains(nodeKey)) {
	            continue;
	        }
	        try {
	            double num = Double.parseDouble(String.valueOf(value.get(nodeKey)));
	            if (value.get(nodeKey).getClass().equals(String.class)) {
	                valueOfNode.append(getStringValue(valueOfNode)).append(nodeKey).append(":").append(value.get(nodeKey));
	            } else {
	                valueOfNode.append(getStringValue(valueOfNode)).append(nodeKey).append(":").append(value.get(nodeKey));
	            }
	        } catch (NumberFormatException e) {
	            System.out.println(value.get(nodeKey) + " is not a number.");
	        }
	    }
	    return valueOfNode.toString();
	}
	
	private String getStringValue(StringBuilder valueOfNode) {
	    return valueOfNode.length() > 0 ? ", " : "";
	}

    public static List<Double> convertStringLabels(List<String> strings) {
        Map<String, Double> labelMap = new HashMap<>();
        List<Double> labels = new ArrayList<>();

        double currentLabel = 0.0;
        for (String s : strings) {
            if (!labelMap.containsKey(s)) {
                labelMap.put(s, currentLabel++);
            }
            labels.add(labelMap.get(s));
        }

        return labels;
    }
    
    public static double calculateAdjustedRandIndex(List<Double> trueLabels, List<Double> predictedLabels) {
        if (trueLabels.size() != predictedLabels.size()) {
            throw new IllegalArgumentException("Input lists must have the same length");
        }

        int n = trueLabels.size();
        Map<Double, Map<Double, Double>> contingencyTable = new HashMap<>();
        Map<Double, Double> trueLabelCounts = new HashMap<>();
        Map<Double, Double> predictedLabelCounts = new HashMap<>();

        // Build the contingency table and label counts
        for (int i = 0; i < n; i++) {
            double trueLabel = trueLabels.get(i);
            double predictedLabel = predictedLabels.get(i);

            contingencyTable.computeIfAbsent(trueLabel, k -> new HashMap<>());
            contingencyTable.get(trueLabel).merge(predictedLabel, 1.0, Double::sum);

            trueLabelCounts.merge(trueLabel, 1.0, Double::sum);
            predictedLabelCounts.merge(predictedLabel, 1.0, Double::sum);
        }

        double a = 0.0; // Number of pairs in the same cluster in both true and predicted
        for (Map<Double, Double> row : contingencyTable.values()) {
            for (double count : row.values()) {
                a += count * (count - 1) / 2.0;
            }
        }

        double b = 0.0; // Number of pairs in the same cluster in trueLabels
        for (double count : trueLabelCounts.values()) {
            b += count * (count - 1) / 2.0;
        }

        double c = 0.0; // Number of pairs in the same cluster in predictedLabels
        for (double count : predictedLabelCounts.values()) {
            c += count * (count - 1) / 2.0;
        }

        double totalPairs = n * (n - 1) / 2.0;
        double expectedIndex = (b * c) / totalPairs;
        double maxIndex = 0.5 * (b + c);
        double adjustedRandIndex = (a - expectedIndex) / (maxIndex - expectedIndex);

        return adjustedRandIndex;
    }
    
	/**
	* Procedure for spectral clustering and visualization in neo4j
	* @param params Set of parameters
	* @return String indicating the successful creation of graphs and results of clustering.
	* @throws Exception If any error occurs during the spectral clustering process.
    */
    @UserFunction
    public double spectralClustering(@Name("params") Map<String, Object> params) throws Exception {
        String node_label = (String) params.getOrDefault("node_label", "Iris");
        Boolean is_feature_based = (Boolean) params.getOrDefault("is_feature_based", true);
        String distance_measure = (String) params.getOrDefault("distance_measure", "euclidean");
        String graph_type = (String) params.getOrDefault("graph_type", "full");
        String parameter = (String) params.getOrDefault("parameter", "7");
        String remove_columns = (String) params.getOrDefault("remove_columns", "index,target");
        String laplacian_type = (String) params.getOrDefault("laplacian_type", "sym");
        Double number_of_eigenvectors = ((Number) params.getOrDefault("number_of_eigenvectors", 3)).doubleValue();
        String number_of_iterations = (String) params.getOrDefault("number_of_iterations", "100");
        String distance_measure_kmean = (String) params.getOrDefault("distance_measure_kmean", "euclidean");
        String target_column = (String) params.getOrDefault("target_column", "target");
        Boolean use_kmean_for_silhouette = (Boolean) params.getOrDefault("use_kmean_for_silhouette", false);
        int seed = ((Number) params.getOrDefault("seed", 42)).intValue();

        predictedNodeLabels.clear();
        
        try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
            if (node_label == null) {
                throw new Exception("Missing node label");
            }

            String graph_name = "affinity_" + graph_type + "_" + parameter.replace(".", "_") + "_" + node_label;

            ArrayList<NodeList2> node_properties_list;
            String property_names = "";
            String identifier = "";
            
            // Step 1: Create affinity graph
            if (is_feature_based) {
                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
                node_properties_list = node_data.getLeft();
                identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), node_label);
                
                property_names = node_data.getRight();

                String[] remove_list = remove_columns.split(",");
                List<String> remove_list_new = Arrays.stream(remove_list).collect(Collectors.toList());
                Double[][] distance_matrix = getDistanceMatrixFromNodes(distance_measure, node_properties_list, remove_list_new);     
                Double[][] adj_mat;
                if (graph_type.equals("full")) {
                    Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(distance_matrix, sigmas);
                } else if (graph_type.equals("eps")) {
                    Double epsilon_value = Double.parseDouble(parameter);
                    adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(distance_matrix, epsilon_value);
                } else if (graph_type.equals("knn")) {
                    int[][] knn = ReadCsvTestData.calculateKNNIndices(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateKNNGraphWithIndices(distance_matrix, knn);
                } else if (graph_type.equals("mknn")) {
                    Double[][] knn = ReadCsvTestData.calculateKNN(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateMutualKNNGraph(distance_matrix, knn);
                } else {
                    throw new Exception("Invalid graph_type specified.");
                }
                ArrayList<EdgeList2> edge_list = GraphTransform.calculateEdgeList(node_properties_list, adj_mat);

            	Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name, connector.getDriver(), identifier);   
            	Neo4jGraphHandler.bulkCreateNodes(graph_name, node_properties_list, connector.getDriver(), identifier);
                Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name, edge_list, connector.getDriver(), identifier);

            } else {
            	graph_name=node_label;
                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(graph_name, connector.getDriver());
                node_properties_list = node_data.getLeft();
                if (node_properties_list.isEmpty()) {
                    throw new Exception("Affinity graph exists but no nodes retrieved.");
                }

                property_names = node_data.getRight();
                identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), graph_name);
            }
            
            // Step 2: Compute Laplacian matrix
            ArrayList<EdgeList2> edge_list_2 = Neo4jGraphHandler.retrieveEdgeList(graph_name, connector.getDriver());	                
            RealMatrix adjacency_matrix = MatrixCalculation.convertToAdjacencyMatrix(edge_list_2,node_properties_list);
            RealMatrix degree_matrix = MatrixCalculation.calculateDegreeMatrix(adjacency_matrix);
            RealMatrix laplacian_matrix = MatrixCalculation.calculateLaplacianMatrix(degree_matrix, adjacency_matrix, laplacian_type);
            Object eigen_result_obj = EigenCalculation.calculateEigen(laplacian_matrix, number_of_eigenvectors);

            if (eigen_result_obj instanceof String) {
                throw new Exception((String) eigen_result_obj);
            }
            EigenCalculation.EigenResult eigen_result = (EigenCalculation.EigenResult) eigen_result_obj;

            ArrayList<EdgeList2> edge_list_eigen = EigenCalculation.createEdgeList(node_properties_list, eigen_result.X, edge_list_2);
            
            String graph_name_eigen = "eigen_" + laplacian_type + "_" + Math.round(number_of_eigenvectors) + "_" + graph_name;
            
            Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name_eigen, connector.getDriver(), identifier);
            Neo4jGraphHandler.bulkCreateNodesWithEigen(graph_name_eigen, node_properties_list, eigen_result.X, connector.getDriver(), identifier);   
            Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name_eigen, edge_list_eigen, connector.getDriver(), identifier);
            
            // Step 3: Perform clustering
            String number_of_clusters = Integer.toString(number_of_eigenvectors.intValue());
            String[] properties_array = property_names.split(",");
            StringBuilder filtered_properties = new StringBuilder();
            for (String property : properties_array) {
                if (!property.equalsIgnoreCase(identifier) && !property.equalsIgnoreCase("target")) {
                    if (filtered_properties.length() > 0) {
                        filtered_properties.append(",");
                    }
                    filtered_properties.append(property);
                }
            }
            
            System.out.println("filtered_properties: " +filtered_properties);
            
            double kmean_result = kMeans(Map.of(
                "nodeSet", graph_name_eigen,
                "numberOfCentroid", number_of_clusters,
                "numberOfInteration", number_of_iterations,
                "distanceMeasure", distance_measure_kmean,
                "originalSet", node_label,
                "overlook", target_column + "," + filtered_properties.toString(),
                "overlookOriginal", target_column,
                "useKmeanForSilhouette", use_kmean_for_silhouette,
                "seed", seed
            ));

            // Return metrics
            return kmean_result;
        }
    }
    
	/**
	* Procedure for experiments to debug spectral clustering procedures
	* @param params Set of parameters
	* @return String indicating the successful creation of graphs and results of clustering.
	* @throws Exception If any error occurs during the spectral clustering process.
    */
    @UserFunction
    public Map<String, Object> experimental_spectralClustering(@Name("params") Map<String, Object> params) throws Exception {
        String node_label = (String) params.getOrDefault("node_label", "Iris");
        Boolean is_feature_based = (Boolean) params.getOrDefault("is_feature_based", true);
        String distance_measure = (String) params.getOrDefault("distance_measure", "euclidean");
        String graph_type = (String) params.getOrDefault("graph_type", "full");
        String parameter = (String) params.getOrDefault("parameter", "7");
        String remove_columns = (String) params.getOrDefault("remove_columns", "index,target");
        String laplacian_type = (String) params.getOrDefault("laplacian_type", "sym");
        Double number_of_eigenvectors = ((Number) params.getOrDefault("number_of_eigenvectors", 3)).doubleValue();
        String number_of_iterations = (String) params.getOrDefault("number_of_iterations", "100");
        String distance_measure_kmean = (String) params.getOrDefault("distance_measure_kmean", "euclidean");
        String target_column = (String) params.getOrDefault("target_column", "target");
        Boolean use_kmean_for_silhouette = (Boolean) params.getOrDefault("use_kmean_for_silhouette", false);
        int seed = ((Number) params.getOrDefault("seed", 42)).intValue();

        predictedNodeLabels.clear();

        long totalStartTime = System.nanoTime();
        long affinityTime = 0, laplacianTime = 0, clusteringTime = 0, adjustedRandIndexTime = 0;

//        StringBuilder output_string = new StringBuilder("Debug Data: ");

        try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
            if (node_label == null) {
                throw new Exception("Missing node label");
            }

            String graph_name = "affinity_" + graph_type + "_" + parameter.replace(".", "_") + "_" + node_label;

            ArrayList<NodeList2> node_properties_list;
            String property_names = "";
            String identifier = "";
            
            // Step 1: Create affinity graph
            long startAffinityTime = System.nanoTime();
            if (is_feature_based) {
                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
                node_properties_list = node_data.getLeft();
                identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), node_label);
                
                property_names = node_data.getRight();

                String[] remove_list = remove_columns.split(",");
                List<String> remove_list_new = Arrays.stream(remove_list).collect(Collectors.toList());

                Double[][] distance_matrix = getDistanceMatrixFromNodes(distance_measure, node_properties_list, remove_list_new);
//                output_string.append("\n\nDistance Matrix:\n").append(doubleToString(distance_matrix));
                
                Double[][] adj_mat;
                if (graph_type.equals("full")) {
                    Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(distance_matrix, sigmas);
                } else if (graph_type.equals("eps")) {
                    Double epsilon_value = Double.parseDouble(parameter);
                    adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(distance_matrix, epsilon_value);
                } else if (graph_type.equals("knn")) {
                    int[][] knn = ReadCsvTestData.calculateKNNIndices(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateKNNGraphWithIndices(distance_matrix, knn);
                } else if (graph_type.equals("mknn")) {
                    Double[][] knn = ReadCsvTestData.calculateKNN(distance_matrix, parameter);
                    adj_mat = ReadCsvTestData.calculateMutualKNNGraph(distance_matrix, knn);
                } else {
                    throw new Exception("Invalid graph_type specified.");
                }
                
//                output_string.append("\n\nAdjacency Matrix1:\n");

//                for (int i = 0; i < adj_mat.length; i++) {
//                    for (int j = 0; j < adj_mat[i].length; j++) {
//                        output_string.append(adj_mat[i][j]).append(" ");
//                    }
//                    output_string.append("\n");
//                }

                ArrayList<EdgeList2> edge_list = GraphTransform.calculateEdgeList(node_properties_list, adj_mat);
                
//                output_string.append("\n\nEdge List1:\n");
//                for (EdgeList2 edge : edge_list) {
//                	output_string.append(" | ").append(edge.toString());
//                }

            	Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name, connector.getDriver(), identifier);   
                
            	Neo4jGraphHandler.bulkCreateNodes(graph_name, node_properties_list, connector.getDriver(), identifier);
                Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name, edge_list, connector.getDriver(), identifier);

            } else {
            	graph_name=node_label;
                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(graph_name, connector.getDriver());
                node_properties_list = node_data.getLeft();
                if (node_properties_list.isEmpty()) {
                    throw new Exception("Affinity graph exists but no nodes retrieved.");
                }

                property_names = node_data.getRight();
                identifier = Neo4jGraphHandler.resolveDynamicIdentifier(connector.getDriver(), graph_name);
            }
            affinityTime = System.nanoTime() - startAffinityTime;

            // Step 2: Compute Laplacian matrix
            long startLaplacianTime = System.nanoTime();
            ArrayList<EdgeList2> edge_list_2 = Neo4jGraphHandler.retrieveEdgeList(graph_name, connector.getDriver());	            
            
//            output_string.append("\n\nEdge List2:\n");
//            for (EdgeList2 edge : edge_list_2) {
//            	output_string.append(" | ").append(edge.toString());
//            }

	            
            RealMatrix adjacency_matrix = MatrixCalculation.convertToAdjacencyMatrix(edge_list_2,node_properties_list);
//            log.info(edge_list_2.toString());
//	        output_string.append("\n\nAdjacency Matrix2:\n").append(matrixToString(adjacency_matrix));
            RealMatrix degree_matrix = MatrixCalculation.calculateDegreeMatrix(adjacency_matrix);
            
            RealMatrix laplacian_matrix = MatrixCalculation.calculateLaplacianMatrix(degree_matrix, adjacency_matrix, laplacian_type);
            
//	        output_string.append("\n\nLaplacian Matrix:\n").append(matrixToString(laplacian_matrix));

            Object eigen_result_obj = EigenCalculation.calculateEigen(laplacian_matrix, number_of_eigenvectors);

            if (eigen_result_obj instanceof String) {
                throw new Exception((String) eigen_result_obj);
            }
            EigenCalculation.EigenResult eigen_result = (EigenCalculation.EigenResult) eigen_result_obj;


            ArrayList<EdgeList2> edge_list_eigen = EigenCalculation.createEdgeList(node_properties_list, eigen_result.X, edge_list_2);
//            log.info(edge_list_eigen.toString());
            
            String graph_name_eigen = "eigen_" + laplacian_type + "_" + Math.round(number_of_eigenvectors) + "_" + graph_name;
            
            Neo4jGraphHandler.bulkDeleteNodesWithBatching(graph_name_eigen, connector.getDriver(), identifier);

            Neo4jGraphHandler.bulkCreateNodesWithEigen(graph_name_eigen, node_properties_list, eigen_result.X, connector.getDriver(), identifier);

            
            Neo4jGraphHandler.bulkCreateRelationshipsWithBatching(graph_name_eigen, edge_list_eigen, connector.getDriver(), identifier);
            
            laplacianTime = System.nanoTime() - startLaplacianTime;

//	        output_string.append("\n\nEigenvalues:\n").append(Arrays.toString(eigen_result.eigenvalues));
//	        output_string.append("\n\nEigenvectors:\n").append(matrixToString2(eigen_result.eigenvectors));
//	        output_string.append("\n\nX Matrix (Top eigenvectors):\n").append(matrixToString2(eigen_result.X));
            
            // Step 3: Perform clustering
            long startClusteringTime = System.nanoTime();
            
            String number_of_clusters = Integer.toString(number_of_eigenvectors.intValue());
            String[] properties_array = property_names.split(",");
            StringBuilder filtered_properties = new StringBuilder();
            for (String property : properties_array) {
                if (!property.equalsIgnoreCase(identifier) && !property.equalsIgnoreCase("target")) {
                    if (filtered_properties.length() > 0) {
                        filtered_properties.append(",");
                    }
                    filtered_properties.append(property);
                }
            }
            
            System.out.println("filtered_properties: " +filtered_properties);
            
            double kmean_result = kMeans(Map.of(
                "nodeSet", graph_name_eigen,
                "numberOfCentroid", number_of_clusters,
                "numberOfInteration", number_of_iterations,
                "distanceMeasure", distance_measure_kmean,
                "originalSet", node_label,
                "overlook", target_column + "," + filtered_properties.toString(),
                "overlookOriginal", target_column,
                "useKmeanForSilhouette", use_kmean_for_silhouette,
                "seed", seed
            ));
            clusteringTime = System.nanoTime() - startClusteringTime;

            // Step 4: Compute adjusted Rand index
            long startAdjustedRandIndexTime = System.nanoTime();
//            double adjustedRandIndexValue = adjustedRandIndex(Map.of(
//                "nodeSet", node_label,
//                "trueLabels", target_column
//            ));
            
            Map<String, Object> adjustedRandIndexResult = adjustedRandIndex(Map.of(
            	    "nodeSet", node_label,
            	    "trueLabels", target_column
            	));
            
         // Extract the results
            double adjustedRandIndexValue = (double) adjustedRandIndexResult.get("adjustedRandIndex");
            List<Integer> trueLabels = (List<Integer>) adjustedRandIndexResult.get("trueLabels");
            List<Integer> predictedLabels = (List<Integer>) adjustedRandIndexResult.get("predictedLabels");

            adjustedRandIndexTime = System.nanoTime() - startAdjustedRandIndexTime;

            long totalTime = System.nanoTime() - totalStartTime;

            // Return metrics
            Map<String, Object> results = new HashMap<>();
            results.put("silhouette_score", kmean_result);
            results.put("rand_index", adjustedRandIndexValue);
//            results.put("true_labels", trueLabels);
//            results.put("predicted_labels", predictedLabels);
            results.put("total_time", totalTime / 1e6);
            results.put("affinity_time", affinityTime / 1e6);
            results.put("laplacian_time", laplacianTime / 1e6);
            results.put("clustering_time", clusteringTime / 1e6);
            results.put("adjusted_rand_index_time", adjustedRandIndexTime / 1e6);

         // Append results to the output string
//            output_string.append("\n\nMetrics:\n").append(results.toString());

            // Return the combined string
//            return output_string.toString();
            return results;
        }
    }

	@UserFunction
	public String displayEdgeList(@Name("nodeType") String nodeType, @Name("dataPath") String dataPath, @Name("distance_measure") String distance_measure, @Name("graph_type") String graph_type, @Name("method") String method, @Name("parameter") String parameter,@Name("remove_column") String remove_columns) throws Exception {
		
		try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
		
		if(dataPath == null && distance_measure == null) {
			return "Missing data_path or distance measure type";
		}else {
			
            // Display edge list

			
			String graphName = "affinity_";
			Double[][] adj_mat = null;
			ArrayList<NodeList2> nodePropertiesList = null;
			String[] removeList = remove_columns.split(",");
			List<String> removeListNew = Arrays.stream(removeList).collect(Collectors.toList());
			if(nodeType.isEmpty()) {
				ReadCsvTestData readCsvTestData = new ReadCsvTestData(dataPath);
//				//ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(data_path,IndexColumn);
				nodePropertiesList = readCsvTestData.readCsvFileToMap(dataPath);
			}
			org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> nodeData = Neo4jGraphHandler.retrieveNodeList(nodeType, connector.getDriver());
			nodePropertiesList = nodeData.getLeft();
//			nodePropertiesList = Neo4jGraphHandler.retrieveNodeListFromNeo4jSimilarityGraph(nodeType, connector.getDriver());

//            ArrayList<NodeList2> nodePropertiesList_copy = readCsvTestData.readCsvFileToMap(data_path);
			Double[][] DistanceMatrix = getDistanceMatrixFromNodes(distance_measure,nodePropertiesList,removeListNew);

            StringBuilder outputString = new StringBuilder("Graph Data: ");
            outputString.append("\n\nDistance Matrix:\n").append(doubleToString(DistanceMatrix));
			
			
			if(graph_type.equals("full")) {
				Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,parameter);
				adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
				graphName = graph_type.concat("_"+parameter);
			}
			if(graph_type.equals("eps")) {
				Double espilonValue = Double.parseDouble(parameter);
				adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,espilonValue);
				graphName = graph_type.concat("_"+parameter);
			}
			if(graph_type.equals("knn")) {
				Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,parameter);
				adj_mat = ReadCsvTestData.calculateKNNGraph(DistanceMatrix,knn);
				graphName = graph_type.concat("_"+parameter);
			}
			if(graph_type.equals("mknn")) {
				Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,parameter);
				adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
				graphName = graph_type.concat("_"+parameter);
			}

			outputString.append("\n\nAdjacency Matrix:\n").append(doubleToString(adj_mat));
			
			ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adj_mat);
			
			outputString.append("\n\nEdge List:\n");
            for (EdgeList2 edge : edgeList) {
                outputString.append(" | ").append(edge.toString());
            }
			
			
			return outputString.toString() + '\n' + "nodes " + nodePropertiesList;
		}
		}
    }
	
	/**
	 * Displays information about a graph based on the specified node label and desired number of eigenvectors.
	 * The Laplacian matrix is set based on the specified laplacian_type.
	 *
	 * @param node_label             The label of the nodes in the graph.
	 * @param number_of_eigenvectors The number of desired eigenvectors to compute during eigen decomposition.
	 * @param laplacian_type         The type of Laplacian matrix to be used.
	 * @return String containing information about the graph, including edge list, adjacency matrix, degree matrix,
	 *         Laplacian matrix, and eigen decomposition results.
	 * @throws Exception If an error occurs during the process.
	 */
	@UserFunction
	public String displayGraphList(@Name("node_label") String node_label,
	                               @Name("number_of_eigenvectors") Double number_of_eigenvectors,
	                               @Name("laplacian_type") String laplacian_type) throws Exception {
	    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
	        if (node_label == null) {
	            return "Error: Missing node label.";
	        }

	        StringBuilder output_string = new StringBuilder("Edge List Data: ");
	        ArrayList<EdgeList2> edge_list = Neo4jGraphHandler.retrieveEdgeListFromNeo4j(node_label, connector.getDriver());
	        for (EdgeList2 edge : edge_list) {
	            output_string.append(" | ").append(edge.toString());
	        }
            org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
            ArrayList<NodeList2> node_list = node_data.getLeft();
	        // Calculate matrices
	        RealMatrix adjacency_matrix = MatrixCalculation.convertToAdjacencyMatrix(edge_list,node_list);
	        output_string.append("\n\nAdjacency Matrix:\n").append(matrixToString(adjacency_matrix));

	        RealMatrix degree_matrix = MatrixCalculation.calculateDegreeMatrix(adjacency_matrix);
	        output_string.append("\n\nDegree Matrix:\n").append(matrixToString(degree_matrix));

	        RealMatrix laplacian_matrix = MatrixCalculation.calculateLaplacianMatrix(degree_matrix, adjacency_matrix, laplacian_type);
	        output_string.append("\n\nLaplacian Matrix:\n").append(matrixToString(laplacian_matrix));

	        // Attempt to calculate eigen decomposition
	        Object eigen_result_obj = EigenCalculation.calculateEigen(laplacian_matrix, number_of_eigenvectors);

	        // Check if the result is an error message
	        if (eigen_result_obj instanceof String) {
	            return (String) eigen_result_obj;  // Return the error message directly
	        }

	        // Cast the result to EigenResult and output eigenvalues, eigenvectors, and X matrix
	        EigenCalculation.EigenResult eigen_result = (EigenCalculation.EigenResult) eigen_result_obj;

	        output_string.append("\n\nEigenvalues:\n").append(Arrays.toString(eigen_result.eigenvalues));
	        output_string.append("\n\nEigenvectors:\n").append(matrixToString2(eigen_result.eigenvectors));
	        output_string.append("\n\nX Matrix (Top eigenvectors):\n").append(matrixToString2(eigen_result.X));

	        return output_string.toString();
	    } catch (Neo4jException e) {
	        throw new RuntimeException(e.getMessage());
	    }
	}

	@UserFunction
	public String displayFinalResults(@Name("nodeSet") String nodeSet, @Name("numberOfCentroid") String numberOfCentroid, @Name("numberOfInteration") String numberOfInteration, @Name("distanceMeasure") String distanceMeasure, @Name("Seed") Number seed) throws Exception {
		
    	predictedNodeLabels.clear();
		    	try ( SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password))
        {
		    		
		    		if(nodeSet == null && distanceMeasure == null) {
		    			return "Missing data_path or distance measure type";
		    		}else {

		    StringBuilder outputString = new StringBuilder("The average Silhouette Coefficient value is: ");
			HashMap<String, ArrayList<String>> kmeanAssign = new HashMap<String, ArrayList<String>>();
			int numberOfCentroidInt = Integer.parseInt(numberOfCentroid);
			int numberOfInterationInt = Integer.parseInt(numberOfInteration);
			double centroidNumber = 1.0;

			ArrayList<String> debug = new ArrayList<>();
			kmeanAssign = Unsupervised.KmeanClust(mapNodeList, numberOfCentroidInt, numberOfInterationInt, distanceMeasure, false, debug, (int) seed);
			double averageSilhouetteCoefficientValue = Unsupervised.averageSilhouetteCoefficient(kmeanAssign, distanceMeasure);
			outputString.append(averageSilhouetteCoefficientValue);

            outputString.append("\n\nKmean assign:\n").append(hashMapToString(kmeanAssign));
            
			for (String centroid : kmeanAssign.keySet()) {
			    ArrayList<String> clusterNode = kmeanAssign.get(centroid);
			    for (int i = 0; i < clusterNode.size(); i++) {
			    	//Add predict labels
		    		predictedNodeLabels.add(centroidNumber);

			    	DecimalFormat decimalFormat = new DecimalFormat("#.###");
			        double distance = Unsupervised.calculateDistance(clusterNode.get(i), centroid, distanceMeasure);
			        connectNodes(nodeSet, "create relationship in kmean node", centroid, clusterNode.get(i), distance, connector.getDriver());
//			        connector.connectNodes(nodeSet, "create relationship in kmean node", centroid, clusterNode.get(i), roundedDistance);
			    }
			    centroidNumber = centroidNumber + 1;
			}

			return outputString + " predicted labels: " + predictedNodeLabels;
		}
		}
    }
	
	/**
	 * Retrieves and saves a specified matrix (adjacency or X matrix) from the graph in Neo4j.
	 *
	 * @param node_label  The label of the nodes in the graph.
	 * @param matrix_type The type of matrix to retrieve ("adjacency" or "x_matrix").
	 * @param save_path   The path where the matrix CSV file should be saved.
	 * @return String indicating the success of the operation.
	 * @throws Exception If an error occurs during the process.
	 */
	@UserFunction
	public String getMatrix(@Name("node_label") String node_label,
	                        @Name("matrix_type") String matrix_type,
	                        @Name("save_path") String save_path) throws Exception {
	    try (SimKitProcedures connector = new SimKitProcedures(SimKitProcedures.uri, SimKitProcedures.username, SimKitProcedures.password)) {
	        if (node_label == null) {
	            return "Missing node label";
	        } else {
	            StringBuilder output_string = new StringBuilder();
	            if ("adjacency".equals(matrix_type)) {
	                ArrayList<EdgeList2> edge_list = Neo4jGraphHandler.retrieveEdgeListFromNeo4j(node_label, connector.getDriver());
	                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> node_data = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
	                ArrayList<NodeList2> node_list = node_data.getLeft();
	                RealMatrix adjacency_matrix = MatrixCalculation.convertToAdjacencyMatrix(edge_list,node_list);
	                output_string.append("Adjacency matrix:\n").append(matrixToString(adjacency_matrix));

	                // Save adjacency matrix to CSV at user-defined or default path
	                saveMatrixToCSV(adjacency_matrix, node_label + "_adjacency_matrix.csv", save_path);
	            } else if ("x_matrix".equals(matrix_type)) {
	                org.apache.commons.lang3.tuple.Pair<ArrayList<NodeList2>, String> nodes = Neo4jGraphHandler.retrieveNodeList(node_label, connector.getDriver());
	                RealMatrix x_matrix = MatrixCalculation.convertToXMatrix(nodes.getLeft());
	                output_string.append("X matrix with eigenvectors:\n").append(matrixToString(x_matrix));

	                // Save X matrix to CSV at user-defined or default path
	                saveMatrixToCSV(x_matrix, node_label + "_x_matrix.csv", save_path);
	            } else {
	                return "Invalid matrix type specified";
	            }
	            return output_string.toString();
	        }
	    } catch (Neo4jException e) {
	        throw new RuntimeException("Error in Neo4j operation: " + e.getMessage());
	    }
	}

	/**
	 * Saves a given matrix to a CSV file at the specified path.
	 *
	 * @param matrix    The matrix to save.
	 * @param filename  The name of the CSV file.
	 * @param save_path The path where the CSV file should be saved.
	 * @throws IOException If an error occurs during file writing.
	 */
	public void saveMatrixToCSV(RealMatrix matrix, String filename, String save_path) throws IOException {
	    // Determine the file path based on user input or default to the current directory
	    String file_path = (save_path == null || save_path.isEmpty()) ? "./" + filename : save_path + "/" + filename;

	    // Write the matrix data to the CSV file
	    try (FileWriter csv_writer = new FileWriter(file_path)) {
	        for (int i = 0; i < matrix.getRowDimension(); i++) {
	            for (int j = 0; j < matrix.getColumnDimension(); j++) {
	                csv_writer.append(Double.toString(matrix.getEntry(i, j)));
	                if (j < matrix.getColumnDimension() - 1) {
	                    csv_writer.append(",");
	                }
	            }
	            csv_writer.append("\n");
	        }
	    }
	}
	
    public static String doubleToString(Double[][] matrix) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(matrix[i][j]);
                if (j < matrix[i].length - 1) {
                    sb.append(" "); // Separate columns with a space
                }
            }
            sb.append("\n"); // Newline after each row
        }
        
        return sb.toString();
    }
    
    private String matrixToString(RealMatrix matrix) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < matrix.getRowDimension(); i++) {
            for (int j = 0; j < matrix.getColumnDimension(); j++) {
                result.append(matrix.getEntry(i, j)).append("\t");
            }
            result.append("\n");
        }
        return result.toString();
    }
    
    private String matrixToString2(SimpleMatrix matrix) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < matrix.getNumRows(); i++) {
            for (int j = 0; j < matrix.getNumCols(); j++) {
                result.append(matrix.get(i, j)).append("\t");
            }
            result.append("\n");
        }
        return result.toString();
    }
    
    
    public String hashMapToString(HashMap<String, ArrayList<String>> kmeanAssign) {
        StringBuilder sb = new StringBuilder();
        
        // Iterate through the entries of the HashMap
        for (HashMap.Entry<String, ArrayList<String>> entry : kmeanAssign.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> values = entry.getValue();
            
            sb.append(key).append(": ");
            
            // Append the values in the ArrayList to the StringBuilder
            sb.append(values.toString());
            
            // Add a newline after each key-value pair
            sb.append("\n");
        }
        
        return sb.toString();
    }
}




