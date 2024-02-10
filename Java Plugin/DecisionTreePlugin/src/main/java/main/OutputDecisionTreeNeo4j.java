package main;
import static org.neo4j.driver.Values.parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Map;

import org.apache.commons.math4.legacy.linear.BlockRealMatrix;
import org.apache.commons.math4.legacy.linear.RealMatrix;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.util.Pair;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import cv.CrossValidation;
import definition.EdgeList;
import definition.EdgeList2;
import definition.NodeList2;
import eigendecomposed.EigenCalculation;
import eigendecomposed.MatrixCalculation;
import evaluate.EvaluateTree;
import gainratio.EvaluateTreeGR;
import gini.EvaluateTreeGI;
import global.Neo4jGraphHandler;
import global.ReadCsvFile;
import graph.DistanceMeasure;
import graph.DistanceMeasureNodes;
import graph.GraphTransform;
import graph.ReadCsvTestData;
import input.ProcessInputData;
import output.PrintTree;

/**
 *
 * This class is used to fetch nodes from graph database or from csv and call the functions to generate decision tree
 * with confusion matrix, generation time and prediction time for the output
 *
 * @author minh dung
 *
 */

/**
 * 
 */
public class OutputDecisionTreeNeo4j implements AutoCloseable{

	private static Driver driver;
	private static List<Record> dataKey = new ArrayList<>();
	private static ArrayList<String> testDataList =  new ArrayList<String>();
	private static ArrayList<String> trainDataList =  new ArrayList<String>();
	private static ArrayList<String> autoSplitDataList =  new ArrayList<String>();
	private static ArrayList<String> classificationDataList = new ArrayList<String>();
	private static ArrayList<String> mapNodeList =  new ArrayList<String>();
	private static List<Double> trueNodeLabels = new ArrayList<Double>();
	private static List<Double> predictedNodeLabels = new ArrayList<Double>();
	

	/**
	 * Creation of driver object using bolt protocol
	 * @param uri Uniform resource identifier for bolt
	 * @param user Username
	 * @param password Password
	 */
	public OutputDecisionTreeNeo4j( String uri, String user, String password )
	{
		driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );

	}

	/**
	 * Empty constructor
	 */
	public OutputDecisionTreeNeo4j()
	{
		driver = null;
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


	public void connectNodeToClassLabel(final String nodeType, final String classLabel, final String node)
	{
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					//a is present for the node
					Result result = tx.run( "MERGE (a:" + nodeType + "{" + node +"}) " +
							"MERGE (b {" + "predictedLabel:"+ classLabel +"}) " +
							"MERGE (a)-[:link]->(b) "
							+ "RETURN a.message");
					return "connected";
				}
			} );
		}
	}

	@UserFunction
	public String classifyOfNodes(@Name("nodeType") String nodeType, @Name("decisionTreeType") String decisionTreeType , @Name("classLabel") String targetAttribute ) throws Exception
	{
		String output = "";
		classificationDataList.clear();
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			boolean isTrainListEmpty = trainDataList.isEmpty();
			boolean isTestListEmpty = testDataList.isEmpty();
			if(isTrainListEmpty && isTestListEmpty) {
				return targetAttribute + "False";
			}else {

				EvaluateTree mine;
				if(decisionTreeType == "IG")
				{
					mine = new EvaluateTree(trainDataList, testDataList, targetAttribute,"False",0);
				}
				else if (decisionTreeType == "GI")
				{
					mine = new EvaluateTreeGI(trainDataList, testDataList, targetAttribute,"False",0);
				}
				else
				{
					mine = new EvaluateTreeGR(trainDataList, testDataList, targetAttribute,"False",0);
				}

				mine.calculateAccuracy();
				HashMap<String, ArrayList<String>> hashMapClassify = mine.predictedResults;
				for (String classLabel: hashMapClassify.keySet()) {
					ArrayList<String> arrayNodes = hashMapClassify.get(classLabel);
					for (String node : arrayNodes)
					{
						connector.connectNodeToClassLabel(nodeType,classLabel,node);
					}
				}
				output = hashMapClassify.values().toString();

			}
		}

		return output;
	}
	
	public void createNodeConnectedGraph(final String GraphType, final String message, final String nodeDetail,final int index)
	{
		final String name = "Index" + index;
 
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					//CREATE (p:fullyConnected {id:"p2", a1: 2, a2: 8,a3: 2})
					//a is present for the node
					Result result = tx.run( "CREATE (connectedGraph:" +GraphType+
							"{id:" +"\""+name+"\""+
							",NodeProperties:" + "\""+nodeDetail+"\""
							+ "})", parameters( "name", name ) );
					return message;
				}
			} );
		}
	}

	/**
	 *
	 * Create nodes in Neo4j using Java
	 * @param dtType Type of decision tree
	 * @param message String The message that print to Console
	 * @param nodeDetail ArrayList<String> Detail of a node
	 */
	public void createNode(final String dtType, final String message, final ArrayList<String> nodeDetail)
	{
		final String name = nodeDetail.get(2);
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					//a is present for the node
					Result result = tx.run( "CREATE (" + nodeDetail.get(0) + dtType + nodeDetail.get(1) +") " +
									"SET a.name = $name" +
									" SET a.l = " + nodeDetail.get(3) +
									" SET a.i = " + nodeDetail.get(4) +
									" SET a.dupValue = " + nodeDetail.get(5) +
									" RETURN a.message + ', from node ' + id(a)",
							parameters( "name", name ) );
					return result.single().get( 0 ).asString();
				}
			} );
		}
	}
	
	public void createRelationshipConnectedGraph( final String dtType, final String message, final EdgeList edgeListDetail)
	{
		final long bid = edgeListDetail.getTarget();
		final String bindex = "Index" + bid;
		double weightValue = (double)Math.round(edgeListDetail.getWeight() * 100000d) / 100000d;
		final String weight = "`" + Double.toString(weightValue) + "` {value: " + weightValue + "}" ;
		//final String weight = "_" + Double.toString(edgeListDetail.getWeight()).replace(".","_") + "_";
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					Result result = tx.run( "MATCH (a:" + dtType + "), (b:" + dtType + ") " +
							"WHERE a.id = "+"\"" +"Index"+edgeListDetail.getSource() +  "\""+" AND "+ "b.id ="+ "\""+bindex+"\""+" "+
							"CREATE (a)-[r:`link` {value:" + "$weightValue" +  "}]->(b)",parameters("weightValue",weightValue));
					return message;
				}
			} );
		}
	}
	

	/**
	 * Create relationship between nodes in Java
	 * @param dtType Type of decision tree
	 * @param message String the message that print to Console
	 * @param relationshipDetail ArrayList<String> Detail of a relationship
	 */
	public void createRelationship( final String dtType, final String message, final ArrayList<String> relationshipDetail)
	{
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					Result result = tx.run( "MATCH (a:" + dtType + "), (b:" + dtType + ") " +
							"Where a.name = '" + relationshipDetail.get(0) + "' And " +
							"a.l = " + relationshipDetail.get(1) + " And " +
							"a.dupValue = " + relationshipDetail.get(2) + " And " +
							"b.name = '" + relationshipDetail.get(3) + "' And " +
							"b.dupValue = " + relationshipDetail.get(5) + " And " +
							"b.l = " + relationshipDetail.get(4) +
							" Create (a)-[r:"+"_"+ relationshipDetail.get(6)+"_"+" {type: '" + relationshipDetail.get(7) +
							"' , value: '" +relationshipDetail.get(6) +
							"' , propname: '" + relationshipDetail.get(0) + "' }]->(b)" +
							" RETURN type(r)");
					return result.single().get( 0 ).asString();
				}
			} );
		}
	}



	/**
	 * This function is used to query the data from graph database
	 * @param nodeType type of node
	 */
	public void queryData( final String nodeType)
	{
		try ( Session session = driver.session() )
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


	
	@UserFunction
	public String loadCsvGraph(@Name("dataPath") String dataPath,@Name("Name") String Name)  throws Exception{

		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{
			if(dataPath == null && Name == null) {
				return "Missing dataPath or distance measure type";
			}else {
				ReadCsvFile readCsvTestData = new ReadCsvFile(dataPath);
				ArrayList<String> arrayListHeaders = readCsvTestData.readCSVHeader(dataPath);
				ArrayList<String> arrayListFirst = readCsvTestData.readCSVFirstLine(dataPath);
				connector.loadCsvConnector(dataPath, arrayListHeaders,arrayListFirst);
			}
		}catch(Exception e) {
			throw new RuntimeException(e);
		}
		return dataPath;
	}
	
	private void loadCsvConnector(String dataPath, ArrayList<String> arrayListHeaders,ArrayList<String> arrayListFirst) throws Exception {

		// LOAD CSV with headers FROM 'file:///test.csv' AS row
		//merge (:csvdata8 {points: row.points,x_cordinate: toFloat(row.x_coordinate),y_cordinate: toFloat(row.y_coordinate),class: toFloat(row.class)})
		String proerties = OutputDecisionTreeNeo4j.getHeadersList(arrayListHeaders,arrayListFirst);
		String fileName = Paths.get(dataPath).getFileName().toString();
		String Name = fileName.substring(0,fileName.indexOf("."));
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					Result result = tx.run( "LOAD CSV WITH HEADERS FROM 'file:///"+fileName+"' AS row "+"merge (:"+Name+"{"+ proerties+"})", parameters( "name", fileName ) );
					return fileName;
				}
			} );
		}

	}

	private static String getHeadersList(ArrayList<String> arrayListHeaders,ArrayList<String> arrayListFirst) {

		StringBuilder stringBuilder = new StringBuilder();
		Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");


		for (int i = 0; i < arrayListHeaders.size(); i++) {

			if(i == arrayListHeaders.size()-1) {

				if (pattern.matcher(arrayListFirst.get(i)).matches()) {
					stringBuilder.append(arrayListHeaders.get(i) + ": toFloat(row." + arrayListHeaders.get(i) + ")");
				} else
					stringBuilder.append(arrayListHeaders.get(i) + ": row." + arrayListHeaders.get(i));
			}else {
				if (pattern.matcher(arrayListFirst.get(i)).matches()) {
					stringBuilder.append(arrayListHeaders.get(i) + ": toFloat(row." + arrayListHeaders.get(i) + "),");
				} else
					stringBuilder.append(arrayListHeaders.get(i) + ": row." + arrayListHeaders.get(i) + ",");
			}


		}

		return stringBuilder.toString();
	}
	
	public Double [][] getDistanceMatrix(String distanceMeasure,ArrayList<ArrayList<String>> testData){
		Double[][] DistanceMatrix = null;


		switch (distanceMeasure){
			case "euclideanDistance":
				DistanceMatrix = ReadCsvTestData.euclidianDistance(testData);
				break;
			case "manhattanDistance" :
				DistanceMatrix = DistanceMeasure.calculateManhattanDistance(testData);
				break;
			case "canberraDistance" :
				DistanceMatrix = DistanceMeasure.calculateCanberraDistance(testData);
				break;
			case "cosineSimilarity" :
				DistanceMatrix = DistanceMeasure.calculateCosineSimilarity(testData);
				break;
			case "jaccardCoefficient" :
				DistanceMatrix = DistanceMeasure.calculateJaccardCoefficient(testData);
				break;
			case "brayCurtisDistance" :
				DistanceMatrix = DistanceMeasure.calculateBrayCurtisDistance(testData);
				break;
			default:
				System.out.println("give correct name");
		}


		return DistanceMatrix;



	}



	@UserFunction
	public String createGraphFromCsv(@Name("data_path") String data_path, @Name("distance_measure") String distance_measure, @Name("graph_type") String graph_type, @Name("parameter") String epsilon,@Name("remove_column") String remove_columns) throws Exception {


		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{

			if(data_path == null && distance_measure == null) {
				return "Missing data_path or distance measure type";
			}else {
				Path path = Paths.get(data_path);
		        String fileName = path.getFileName().toString();
		        String fileNameWithoutExtension = fileName.replaceFirst("[.][^.]+$", "");
//		        String fileType = path.getName(path.getNameCount() - 1).toString();
		        String graphName = fileNameWithoutExtension + "_" + graph_type;
//				String graphName = null;
				Double[][] adj_mat = null;
				List<String> removeList = Arrays.stream(remove_columns.split(",")).collect(Collectors.toList());
				ReadCsvTestData readCsvTestData = new ReadCsvTestData(data_path);
//				//ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(data_path,IndexColumn);
				ArrayList<NodeList2> nodePropertiesList = readCsvTestData.readCsvFileToMap(data_path);

				Double[][] DistanceMatrix = getDistanceMatrixFromNodes(distance_measure,nodePropertiesList,removeList);

				if(graph_type.equals("full")) {
					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
					graphName = graphName.concat("_"+epsilon);
				}
				if(graph_type.equals("eps")) {
					Double espilonValue = Double.parseDouble(epsilon);
					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,espilonValue);
					graphName = graphName.concat("_"+epsilon);
				}
				if(graph_type.equals("knn")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateKNNGraph(DistanceMatrix,knn);
					graphName = graphName.concat("_"+epsilon);

				}
				if(graph_type.equals("mknn")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
					graphName = graphName.concat("_"+epsilon);
				}
				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adj_mat);

				for (NodeList2 node : nodePropertiesList) {
					Neo4jGraphHandler.createNodeGraph(graphName.concat("new"), "created nodes in neo4j", node, connector.getDriver());
				}
				
				

				for (int i = 0; i < edgeList.size(); i++) {
					EdgeList2 edgeListDetail = edgeList.get(i);
					if(edgeListDetail.getWeight()==0.0){
						continue;
					}
					Neo4jGraphHandler.createRelationshipGraph(graphName.concat("new"), "created relationship in neo4j \n", edgeListDetail, connector.getDriver());

				}

			}
			return "Create fully connected graph successful, " + confusionMatrix;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
	
	@UserFunction
	public String createGraphFromNodes(@Name("label") String label,@Name("distanceMeasure") String distanceMeasure,@Name("graphType") String graphType,@Name("parameter") String epsilon,@Name("remove_column") String remove_columns) throws Exception {

		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{


			if(label == null && distanceMeasure == null) {
				return "Missing dataPath or distance measure type";
			}else {
				String graphName = null;
				ArrayList<NodeList2> nodePropertiesList = Neo4jGraphHandler.retrieveNodeListFromNeo4j(label, connector.getDriver());
				List<String> removeList = Arrays.stream(remove_columns.split(",")).toList();
//				Double[][] DistanceMatrix = GraphTransform.euclideanDistance(nodePropertiesList);
				Double[][] DistanceMatrix = getDistanceMatrixFromNodes(distanceMeasure,nodePropertiesList,removeList);
				Double[][] adj_mat = null;

				if(graphType.equals("ConnectedGraph")) {
					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
					graphName = graphType.concat("_"+epsilon);
				}
				if(graphType.equals("EpsilonGraph")) {
					Double espilonValue = Double.parseDouble(epsilon);
					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,espilonValue);
					graphName = graphType.concat("_"+epsilon);

				}
				if(graphType.equals("knnGraph")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateKNNGraph(DistanceMatrix,knn);
					graphName = graphType.concat("_"+epsilon);

				}
				if(graphType.equals("MutualKnnGraph")) {
					Double[][] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,epsilon);
					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
					graphName = graphType.concat("_"+epsilon);
				}
				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adj_mat);



				//for (EdgeList edgeListDetail : edgeList) {
				for (NodeList2 node : nodePropertiesList) {
					Neo4jGraphHandler.createNodeGraph(graphName.concat("new"), "created nodes in neo4j", node, connector.getDriver());
				}

				for (int i = 0; i < edgeList.size(); i++) {
					EdgeList2 edgeListDetail = edgeList.get(i);
					if(edgeListDetail.getWeight()==0.0){
						continue;
					}
					Neo4jGraphHandler.createRelationshipGraph(graphName.concat("new"), "created relationship in neo4j \n", edgeListDetail, connector.getDriver());

				}

			}
			return "Create fully connected graph successful, " + confusionMatrix;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
	

	private Double[][] getDistanceMatrixFromNodes(String distanceMeasure, ArrayList<NodeList2> nodePropertiesList,List<String> removeList) {
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
			case "cosine_similarity":
				DistanceMatrix = DistanceMeasureNodes.cosineSimilarity(nodePropertiesList,removeList);
				break;
			case "jaccard_coefficient":
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
	public String createConnectedGraph(@Name("dataPath") String dataPath,@Name("distanceMeasure") String distanceMeasure,@Name("IndexBoolean") Boolean IndexColumn) throws Exception {

		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{


			if(dataPath == null && distanceMeasure == null) {
				return "Missing dataPath or distance measure type";
			}else {
				ReadCsvFile readCsvTestData = new ReadCsvFile(dataPath);
				ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(dataPath,IndexColumn);

				Double[][] DistanceMatrix = ReadCsvFile.euclidianDistance(testData);
				Double[] sigmas = ReadCsvFile.calculateLocalSigmas(DistanceMatrix);
				Double[][] adj_mat = ReadCsvFile.calculateAdjacencyMatrix(DistanceMatrix,sigmas);

				ArrayList<String> nodeList = ReadCsvFile.getNodeList(testData);
				ArrayList<EdgeList> edgeList = ReadCsvFile.calulateEdgeList(adj_mat);



				for (String nodeDetail : nodeList) {

					connector.createNodeConnectedGraph("connectedGraph", "created nodes in neo4j", nodeDetail,1);
				}

				//for (EdgeList edgeListDetail : edgeList) {
				for (int i = 0; i < edgeList.size()-1; i++) {
					EdgeList edgeListDetail = edgeList.get(i);
//					ArrayList<String> relationshipDetail = new ArrayList<>();
//					relationshipDetail.add(String.valueOf(edgeListDetail.getSource()));
//					relationshipDetail.add(String.valueOf(edgeListDetail.getTarget()));
//					relationshipDetail.add(String.valueOf(edgeListDetail.getWeight()));
//					String testCypherQuery = "MATCH (a:" + dtType + "), (b:" + dtType + ") " +
//							"WHERE a.id = "+"\"" +"Index"+edgeListDetail.getSource() +  "\""+" AND "+ "b.id ="+ "\""+bindex+"\""+" "+
//							"CREATE (a)-[r:" + edgeListDetail.getWeight() + "]->(b)";
					connector.createRelationshipConnectedGraph("Index", "created relationship in neo4j \n", edgeListDetail);
				}

			}
			return "Create fully connected graph successful, " + confusionMatrix;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
//
//	@UserFunction
//	public String createEpsilonGraph(@Name("dataPath") String dataPath,@Name("distanceMeasure") String distanceMeasure,@Name("IndexBoolean") Boolean IndexColumn,@Name("epsilon") Double epsilon) throws Exception {
//
//		String confusionMatrix = "";
//		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
//		{
//
//
//			if(dataPath == null && distanceMeasure == null) {
//				return "Missing dataPath or distance measure type";
//			}else {
//				ReadCsvFile readCsvTestData = new ReadCsvFile(dataPath);
//				ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(dataPath,IndexColumn);
//
//				Double[][] DistanceMatrix = ReadCsvFile.euclidianDistance(testData);
//				//Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix);
//				//Double[][] adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
//				Double[][] adj_mat = ReadCsvFile.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,epsilon);
//
//				ArrayList<String> nodeList = ReadCsvFile.getNodeList(testData);
//				ArrayList<EdgeList> edgeList = ReadCsvFile.calulateEdgeList(adj_mat);
//
//
//
//				for (String nodeDetail : nodeList) {
//
//					connector.createNodeConnectedGraph("connectedGraph", "created nodes in neo4j", nodeDetail);
//				}
//
//				//for (EdgeList edgeListDetail : edgeList) {
//				for (int i = 0; i < edgeList.size()-1; i++) {
//					EdgeList edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//					connector.createRelationshipConnectedGraph("Index", "created relationship in neo4j \n", edgeListDetail);
//				}
//
//			}
//			return "Create epsilon  graph successful, " + confusionMatrix;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//
//	}
//	@UserFunction
//	public String createKnnGraph(@Name("dataPath") String dataPath,@Name("distanceMeasure") String distanceMeasure,@Name("IndexBoolean") Boolean IndexColumn) throws Exception {
//
//		String confusionMatrix = "";
//		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
//		{
//
//
//			if(dataPath == null && distanceMeasure == null) {
//				return "Missing dataPath or distance measure type";
//			}else {
//				ReadCsvFile readCsvTestData = new ReadCsvFile(dataPath);
//				ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(dataPath,IndexColumn);
//
//				Double[][] DistanceMatrix = ReadCsvFile.euclidianDistance(testData);
////				Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix);
////				Double[][] adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
//				Double[] knn = ReadCsvFile.calculateKNN(DistanceMatrix);
//				Double[][] adj_mat = ReadCsvFile.calculateKNNGraph(DistanceMatrix,knn);
//
//				ArrayList<String> nodeList = ReadCsvFile.getNodeList(testData);
//				ArrayList<EdgeList> edgeList = ReadCsvFile.calulateEdgeList(adj_mat);
//
//
//
//				for (String nodeDetail : nodeList) {
//
//					connector.createNodeConnectedGraph("connectedGraph", "created nodes in neo4j", nodeDetail);
//				}
//
//				//for (EdgeList edgeListDetail : edgeList) {
//				for (int i = 0; i < edgeList.size()-1; i++) {
//					EdgeList edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//					connector.createRelationshipConnectedGraph("Index", "created relationship in neo4j \n", edgeListDetail);
//				}
//
//			}
//			return "Create fully connected graph successful, " + confusionMatrix;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//
//	}
//
//	@UserFunction
//	public String createMutualKnnGraph(@Name("dataPath") String dataPath,@Name("distanceMeasure") String distanceMeasure,@Name("IndexBoolean") Boolean indexColumn) throws Exception {
//
//		String confusionMatrix = "";
//		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
//		{
//
//
//			if(dataPath == null && distanceMeasure == null) {
//				return "Missing dataPath or distance measure type";
//			}else {
//				ReadCsvFile readCsvTestData = new ReadCsvFile(dataPath);
//				ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNew(dataPath,indexColumn);
//
//				Double[][] DistanceMatrix = ReadCsvFile.euclidianDistance(testData);
////				Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix);
////				Double[][] adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
//				Double[] knn = ReadCsvFile.calculateKNN(DistanceMatrix);
//				Double[][] adj_mat = ReadCsvFile.calculateMutualKNNGraph(DistanceMatrix,knn);
//
//				ArrayList<String> nodeList = ReadCsvFile.getNodeList(testData);
//				ArrayList<EdgeList> edgeList = ReadCsvFile.calulateEdgeList(adj_mat);
//
//
//
//				for (String nodeDetail : nodeList) {
//
//					connector.createNodeConnectedGraph("connectedGraph", "created nodes in neo4j", nodeDetail);
//				}
//
//				//for (EdgeList edgeListDetail : edgeList) {
//				for (int i = 0; i < edgeList.size()-1; i++) {
//					EdgeList edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//					connector.createRelationshipConnectedGraph("Index", "created relationship in neo4j \n", edgeListDetail);
//				}
//
//			}
//			return "Create fully connected graph successful, " + confusionMatrix;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//
//	}
	
//	@UserFunction
//	public String createGraphTransformFromCSV(@Name("dataPath") String dataPath, @Name("distanceMeasure") String distanceMeasure, @Name("method") String method, @Name("IndexBoolean") Boolean indexColumn, @Name("epsilon") Double epsilon) throws Exception {
//
//	    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
//	        if (dataPath == null || distanceMeasure == null) {
//	            return "Missing label or distance measure type";
//	        } else {
//	        	
////				ArrayList<String> arrayListHeaders = ReadCsvFile.readCSVHeader(dataPath);
////				ArrayList<String> arrayListFirst = ReadCsvFile.readCSVFirstLine(dataPath);
////				List<NodeList2> nodeList = ReadCsvFile.loadCsvConnector(dataPath, name ,arrayListHeaders,arrayListFirst, driver);
//	        	ArrayList<NodeList2> nodeList = ReadCsvFile.retrieveNodeListFromCSV(dataPath);
//	        	//nodeList doesnt have any properties - hence no id or proparties for euclidean distance
//	            Double[][] distanceMatrix = GraphTransform.euclideanDistance(nodeList);
//	            Double[][] adjMatrix = GraphTransform.calculateAdjMatrix(distanceMatrix, method, epsilon);
//				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodeList,adjMatrix);
//	            
//				String graphName = "transformedGraph_" + method;				
//				
//	            for (NodeList2 node : nodeList) {
//	            	Neo4jGraphHandler.createNodeGraph(graphName, "created nodes in neo4j", node, connector.getDriver());
//	            }
//	            
//				for (int i = 0; i < edgeList.size(); i++) {
//					EdgeList2 edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//	            	Neo4jGraphHandler.createRelationshipGraph(graphName, "created relationship in neo4j \n", edgeListDetail, connector.getDriver());
//
//				}
//				
//	        }
//	        return "Create graph transform successful!";
//	    } catch (Neo4jException e) {
//	        throw new RuntimeException("Error creating connected graph in Neo4j: " + e.getMessage());
//	    }
//	}
//
//	@UserFunction
//	public String createGraphTransformFromNeo4j(@Name("label") String label, @Name("distanceMeasure") String distanceMeasure, @Name("method") String method, @Name("epsilon") Double epsilon) throws Exception {
//
//	    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
//	        if (label == null || distanceMeasure == null) {
//	            return "Missing label or distance measure type";
//	        } else {
//	        	ArrayList<NodeList2> nodePropertiesList = Neo4jGraphHandler.retrieveNodeListFromNeo4j(label, connector.getDriver());
//	            Double[][] distanceMatrix = GraphTransform.euclideanDistance(nodePropertiesList);
//	            Double[][] adjMatrix = GraphTransform.calculateAdjMatrix(distanceMatrix, method, epsilon);
//				ArrayList<EdgeList2> edgeList = GraphTransform.calculateEdgeList(nodePropertiesList,adjMatrix);
//	            
//				String graphName = "transformedGraph_" + method;				
//				
//	            for (NodeList2 node : nodePropertiesList) {
//	            	Neo4jGraphHandler.createNodeGraph(graphName, "created nodes in neo4j", node, connector.getDriver());
//	            }
//	            
//				for (int i = 0; i < edgeList.size(); i++) {
//					EdgeList2 edgeListDetail = edgeList.get(i);
//					if(edgeListDetail.getWeight()==0.0){
//						continue;
//					}
//	            	Neo4jGraphHandler.createRelationshipGraph(graphName, "created relationship in neo4j \n", edgeListDetail, connector.getDriver());
//
//				}
//				
//	        }
//	        return "Create graph transform successful!";
//	    } catch (Neo4jException e) {
//	        throw new RuntimeException("Error creating connected graph in Neo4j: " + e.getMessage());
//	    }
//	}


	@UserFunction
	public String create_laplacian_eigen_transform_graph(@Name("node_label") String node_label,  @Name("laplacian_type") String laplacian_type, @Name("number_of_eigenvectors") Double number_of_eigenvectors) throws Exception {
	    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
	        if (node_label == null) {
	            return "Missing node label";
	        } else {
	            ArrayList<EdgeList2> edgeList = new ArrayList<>();
	            edgeList = Neo4jGraphHandler.retrieveEdgeListFromNeo4j(node_label, connector.getDriver());

	            double[][] adjacencyMatrixData = MatrixCalculation.convertToAdjacencyMatrix2(edgeList);
	            RealMatrix adjacencyMatrix = new BlockRealMatrix(adjacencyMatrixData);
	            RealMatrix degreeMatrix = MatrixCalculation.calculateDegreeMatrix(adjacencyMatrixData);
	            RealMatrix laplacianMatrix = MatrixCalculation.calculateLaplacianMatrix(degreeMatrix, adjacencyMatrix, laplacian_type);

	            EigenCalculation.EigenResult eigenResult = EigenCalculation.calculateEigen(laplacianMatrix, number_of_eigenvectors);
	            ArrayList<NodeList2> nodeListEigen = Neo4jGraphHandler.retrieveNodeListFromNeo4j(node_label, connector.getDriver());
	            ArrayList<EdgeList2> edgeListEigen = EigenCalculation.createEdgeList(nodeListEigen, eigenResult.X);
	            
	            String graphName = "eigendecomposedGraph_" + laplacian_type + "_" + node_label + "_" + Math.round(number_of_eigenvectors);
	            
	            for (NodeList2 node : nodeListEigen) {
	            	Neo4jGraphHandler.createNodeGraph(graphName, "created nodes in neo4j", node, eigenResult.X, connector.getDriver());
	            }

//	            for (EdgeList2 edge : edgeListEigen) {
	            for (int i = 0; i < edgeListEigen.size(); i++) {
					EdgeList2 edgeListDetail = edgeListEigen.get(i);
					if(edgeListDetail.getWeight()==0.0){
						continue;
					}
	            	Neo4jGraphHandler.createRelationshipGraph(graphName, "created relationship in neo4j \n", edgeListDetail, connector.getDriver());
	            }
	        }
	        return "Create eigendecomposed graph successful!";
	    } catch (Neo4jException e) {
	        throw new RuntimeException("Error creating laplacian graph in Neo4j: " + e.getMessage());
	    }
	}


	@UserFunction
	public String displayEdgeList(@Name("nodeType") String nodeType, @Name("threshold") double threshold, @Name("laplacianType") String laplacianType, @Name("epsilon") Double epsilon) throws Exception {
//	    String outputString = "";
	    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
	        if (nodeType == null) {
	            return "Missing nodeType";
	        } else {
	            ArrayList<EdgeList2> edgeList = Neo4jGraphHandler.retrieveEdgeListFromNeo4j(nodeType, connector.getDriver());

	            double[][] adjacencyMatrixData = MatrixCalculation.convertToAdjacencyMatrix2(edgeList);
	            RealMatrix adjacencyMatrix = new BlockRealMatrix(adjacencyMatrixData);
	            RealMatrix degreeMatrix = MatrixCalculation.calculateDegreeMatrix(adjacencyMatrixData);
	            RealMatrix laplacianMatrix = MatrixCalculation.calculateLaplacianMatrix(degreeMatrix, adjacencyMatrix, laplacianType);

	            EigenCalculation.EigenResult eigenResult = EigenCalculation.calculateEigen(laplacianMatrix, epsilon);
	            ArrayList<NodeList2> nodeListEigen = Neo4jGraphHandler.retrieveNodeListFromNeo4j(nodeType, connector.getDriver());
	            ArrayList<EdgeList2> edgeListEigen = EigenCalculation.createEdgeList(nodeListEigen, eigenResult.X);

	            
	            
	            
	            // Debug information
	            StringBuilder outputString = new StringBuilder("Edge List Data: ");
	            for (EdgeList2 edge : edgeList) {
                    outputString.append(" | ").append(edge.toString());
                }
	            
	            outputString.append("\n\nAdjacency Matrix:\n").append(matrixToString(adjacencyMatrix));
                outputString.append("\n\nDegree Matrix:\n").append(matrixToString(degreeMatrix));
                outputString.append("\n\nLaplacian Matrix:\n").append(matrixToString(laplacianMatrix));

                outputString.append("\n\nEigenvalues:\n").append(Arrays.toString(eigenResult.eigenvalues));
                outputString.append("\n\nEigenvectors:\n").append(matrixToString(eigenResult.eigenvectors));
                outputString.append("\n\nX:\n").append(matrixToString(eigenResult.X));
                

                
	            if (edgeListEigen.size() > 0) {
	                outputString.append("\n\nEigendecomposed Eigen List:\n");
	                for (EdgeList2 edge : edgeListEigen) {
	                    outputString.append(" | ").append(edge.toString());
	                }
	            } else {
	                outputString.append("Could not retrieve edge list or no edges passed the threshold.");
	            }
	            
	            String graphName = "eigendecomposedGraph_" + laplacianType + "_" + nodeType + "_" + Math.round(epsilon);

	            
//	            for (NodeList2 node : nodeListEigen) {
//	            	Neo4jGraphHandler.createNodeGraph(graphName, "created nodes in neo4j", node, eigenResult.X, connector.getDriver());
//	            }
	            
	            return outputString.toString();
	        }
	        
	    } catch (Neo4jException e) {
	        throw new RuntimeException("Error creating laplacian graph in Neo4j: " + e.getMessage());
	    }
	}

    
    @UserFunction
    public String displayGraphList(@Name("nodeType") String nodeType, @Name("numberOfEigenvectors") Double numberOfEigenvectors) throws Exception {
        try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
            if (nodeType == null) {
                return "Missing nodeType";
            } else {
                ArrayList<EdgeList2> edgeList = Neo4jGraphHandler.retrieveEdgeListFromNeo4j(nodeType, connector.getDriver());

                // Display edge list
                StringBuilder outputString = new StringBuilder("Edge List Data: ");
                for (EdgeList2 edge : edgeList) {
                    outputString.append(" | ").append(edge.toString());
                }

                // Display adjacency matrix
                double[][] adjacencyMatrixData = MatrixCalculation.convertToAdjacencyMatrix2(edgeList);
                RealMatrix adjacencyMatrix = new BlockRealMatrix(adjacencyMatrixData);
                outputString.append("\n\nAdjacency Matrix:\n").append(matrixToString(adjacencyMatrix));

                // Display degree matrix
                RealMatrix degreeMatrix = MatrixCalculation.calculateDegreeMatrix(adjacencyMatrixData);
                outputString.append("\n\nDegree Matrix:\n").append(matrixToString(degreeMatrix));

                // Display Laplacian matrix
                RealMatrix laplacianMatrix = MatrixCalculation.calculateLaplacianMatrix(degreeMatrix, adjacencyMatrix, "sym");
                outputString.append("\n\nLaplacian Matrix:\n").append(matrixToString(laplacianMatrix));

                // Display Eigenvalues, Eigenvectors, and X
                EigenCalculation.EigenResult eigenResult = EigenCalculation.calculateEigen(laplacianMatrix, numberOfEigenvectors);
                outputString.append("\n\nEigenvalues:\n").append(Arrays.toString(eigenResult.eigenvalues));
                outputString.append("\n\nEigenvectors:\n").append(matrixToString(eigenResult.eigenvectors));
                outputString.append("\n\nX:\n").append(matrixToString(eigenResult.X));

                return outputString.toString();
            }
        } catch (Neo4jException e) {
            throw new RuntimeException("Error displaying edge list in Neo4j: " + e.getMessage());
        }
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
    
    public void createRelationshipConnectedGraphFromExistingNodes( final String dtType, final String message, final EdgeList edgeListDetail)
	{
		final long bid = edgeListDetail.getTarget();
		final String bindex = "Index" + bid;
		double weightValue = (double)Math.round(edgeListDetail.getWeight() * 100000d) / 100000d;
		final String weight = "`" + Double.toString(weightValue) + "` {value: " + weightValue + "}" ;
		//final String weight = "_" + Double.toString(edgeListDetail.getWeight()).replace(".","_") + "_";
		try ( Session session = driver.session() )
		{
			String greeting = session.writeTransaction( new TransactionWork<String>()
			{
				@Override
				public String execute( Transaction tx )
				{
					Result result = tx.run( "MATCH (a:" + dtType + "), (b:" + dtType + ") " +
							"WHERE a.id = "+"\"" +"Index"+edgeListDetail.getSource() +  "\""+" AND "+ "b.id ="+ "\""+bindex+"\""+" "+
							"CREATE (a)-[r:" + weight +  "]->(b)");
					return message;
				}
			} );
		}
	}


//	@UserFunction
//	public String createGraphStringClass(@Name("dataPath") String dataPath,@Name("distanceMeasure") String distanceMeasure,@Name("IndexBoolean") Boolean IndexColumn,@Name("graphType") String graphType,@Name("epsilon") Double epsilon,@Name("sigma") int sigma,@Name("Knn") int knn_neighbour,@Name("class types") List<String> classTypes) throws Exception {
//
//		String confusionMatrix = "";
//		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
//		{
//
//
//			if(dataPath == null && distanceMeasure == null) {
//				return "Missing dataPath or distance measure type";
//			}else {
//				Double[][] adj_mat = null;
//				ReadCsvTestData readCsvTestData = new ReadCsvTestData(dataPath);
//				ArrayList<ArrayList<String>> testData = readCsvTestData.readCsvFileNewString(dataPath,IndexColumn,classTypes);
//				if(graphType.equals("connectedGraph")) {
//					Double[][] DistanceMatrix = ReadCsvTestData.euclidianDistance(testData);
//					Double[] sigmas = ReadCsvTestData.calculateLocalSigmas(DistanceMatrix,sigma);
//					adj_mat = ReadCsvTestData.calculateAdjacencyMatrix(DistanceMatrix,sigmas);
//				}
//				if(graphType.equals("epsilonGraph")) {
//					Double[][] DistanceMatrix = ReadCsvTestData.euclidianDistance(testData);
//					adj_mat = ReadCsvTestData.calculateEpsilonNeighbourhoodGraph(DistanceMatrix,epsilon);
//
//				}
//				if(graphType.equals("knnGraph")) {
//					Double[][] DistanceMatrix = ReadCsvTestData.euclidianDistance(testData);
//					Double[] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,knn_neighbour);
//					adj_mat = ReadCsvTestData.calculateKNNGraph(DistanceMatrix,knn);
//
//				}
//				if(graphType.equals("mutualKnnGraph")) {
//					Double[][] DistanceMatrix = ReadCsvTestData.euclidianDistance(testData);
//					Double[] knn = ReadCsvTestData.calculateKNN(DistanceMatrix,knn_neighbour);
//					adj_mat = ReadCsvTestData.calculateMutualKNNGraph(DistanceMatrix,knn);
//				}
//
//				ArrayList<String> nodeList = ReadCsvTestData.getNodeList(testData);
//				ArrayList<EdgeList> edgeList = ReadCsvTestData.calulateEdgeList(adj_mat);
//
//
//
//				for (String nodeDetail : nodeList) {
//
//					connector.createNodeConnectedGraph("connectedGraph", "created nodes in neo4j", nodeDetail);
//				}
//
//				//for (EdgeList edgeListDetail : edgeList) {
//				for (int i = 0; i < edgeList.size()-1; i++) {
//					EdgeList edgeListDetail = edgeList.get(i);
////					ArrayList<String> relationshipDetail = new ArrayList<>();
////					relationshipDetail.add(String.valueOf(edgeListDetail.getSource()));
////					relationshipDetail.add(String.valueOf(edgeListDetail.getTarget()));
////					relationshipDetail.add(String.valueOf(edgeListDetail.getWeight()));
////					String testCypherQuery = "MATCH (a:" + dtType + "), (b:" + dtType + ") " +
////							"WHERE a.id = "+"\"" +"Index"+edgeListDetail.getSource() +  "\""+" AND "+ "b.id ="+ "\""+bindex+"\""+" "+
////							"CREATE (a)-[r:" + edgeListDetail.getWeight() + "]->(b)";
//					connector.createRelationshipConnectedGraph("Index", "created relationship in neo4j \n", edgeListDetail);
//				}
//
//			}
//			return "Create fully connected graph successful, " + confusionMatrix;
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//
//	}



	/**
	 * This function is used to split the nodes from database based on training ratio given
	 * @param nodeType
	 * @param trainRatio
	 * @return String with train ratio and test ratio
	 * @throws Exception
	 */
	@UserFunction
	public String queryAutoSplitData(@Name("nodeType") String nodeType, @Name("trainRatio") String trainRatio ) throws Exception
	{
		String listOfData = "";
		double testRatio = 0;
		autoSplitDataList.clear();
		testDataList.clear();
		trainDataList.clear();
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			queryData(nodeType);
			for (Record key : dataKey) {
				List<Pair<String,Value>> values = key.fields();
				for (Pair<String,Value> nodeValues: values) {
					String valueOfNode = "";
					if ("n".equals(nodeValues.key())) {
						Value value = nodeValues.value();
						for (String nodeKey : value.keys())
						{
							if(value.get(nodeKey).getClass().equals(String.class))
							{
								if(valueOfNode != "")
								{
									valueOfNode = valueOfNode + ", " + nodeKey + ":" + value.get(nodeKey);
								}
								else
								{
									valueOfNode = nodeKey + ":" + value.get(nodeKey);
								}

							}
							else
							{
								if(valueOfNode != "")
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode = valueOfNode + ", " + nodeKey + ":" + converValueToString;
								}
								else
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode =  nodeKey + ":" + converValueToString;
								}
							}
						}
						autoSplitDataList.add(valueOfNode);
					}
				}
			}
			int size = autoSplitDataList.size();
			double sizeForTrain = Math.floor(size * Double.parseDouble(trainRatio));
			int startTestData =  (int) sizeForTrain;
			testRatio = 1 - Double.parseDouble(trainRatio);
			//Add data to trainDataList
			for (int i = 0; i < startTestData; i++)
			{
				trainDataList.add(autoSplitDataList.get(i));
			}
			//Add data to testDataList
			for (int i = startTestData; i < size; i++)
			{
				testDataList.add(autoSplitDataList.get(i));
			}
		}
		return "The Data has been split -  Train Ratio: " + trainRatio + " Test Ratio: " + testRatio;
	}



	/**
	 * This function is used to query the test dataset from Neo4j and populate the global arraylist of Java Code
	 * @param nodeType The name of the type of node.For example, P_test for Test
	 * @return String showing the data queried
	 * @throws Exception if connection to Neo4j fails
	 */
	@UserFunction
	public String queryTestData(@Name("nodeType") String nodeType) throws Exception
	{
		String listOfData = "";
		testDataList.clear();
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{
			queryData(nodeType);
			for (Record key : dataKey) {
				List<Pair<String,Value>> values = key.fields();
				for (Pair<String,Value> nodeValues: values) {
					String valueOfNode = "";
					if ("n".equals(nodeValues.key())) {
						Value value = nodeValues.value();
						for (String nodeKey : value.keys())
						{
							if(value.get(nodeKey).getClass().equals(String.class))
							{
								if(valueOfNode != "")
								{
									String valueKey = ":" + value.get(nodeKey);
									valueOfNode = valueOfNode + "," + nodeKey +  valueKey.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+value.get(nodeKey));
								}
								else
								{
									String valueKey = ":" + value.get(nodeKey);
									valueOfNode = nodeKey + valueKey.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+value.get(nodeKey));
								}

							}
							else
							{
								if(valueOfNode != "")
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode = valueOfNode + "," + nodeKey + ":" + converValueToString.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+converValueToString);
								}
								else
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode =  nodeKey + ":" + converValueToString.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+converValueToString);
								}

							}
						}
						testDataList.add(valueOfNode);
					}
					listOfData = listOfData + valueOfNode + " | ";
				}
			}
		}
		return "The Data: " + listOfData;
	}

	/**
	 * This function is used to query the training dataset from Neo4j and populate the global trainDataList of Java Code
	 *
	 * @param nodeType
	 * @return
	 * @throws Exception
	 */

	@UserFunction
	public String queryTrainData(@Name("nodeType") String nodeType) throws Exception
	{
		String listOfData = "";
		trainDataList.clear();
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{
			queryData(nodeType);
			for (Record key : dataKey) {
				List<Pair<String,Value>> values = key.fields();
				for (Pair<String,Value> nodeValues: values) {
					String valueOfNode = "";
					if ("n".equals(nodeValues.key())) {
						Value value = nodeValues.value();
						for (String nodeKey : value.keys())
						{
							if(value.get(nodeKey).getClass().equals(String.class))
							{
								if(valueOfNode != "")
								{
									String valueKey = ":" + value.get(nodeKey);
									valueOfNode = valueOfNode + "," + nodeKey +  valueKey.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+value.get(nodeKey));
								}
								else
								{
									String valueKey = ":" + value.get(nodeKey);
									valueOfNode = nodeKey + valueKey.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+value.get(nodeKey));
								}
							}
							else
							{
								if(valueOfNode != "")
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode = valueOfNode + "," + nodeKey + ":" + converValueToString.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+converValueToString);
								}
								else
								{
									String converValueToString = String.valueOf(value.get(nodeKey));
									valueOfNode =  nodeKey + ":" + converValueToString.replaceAll("^\"|\"$", "");
									//nodeData.add(nodeKey+":"+converValueToString);
								}
							}
						}
						trainDataList.add(valueOfNode);
					}
					listOfData = listOfData + valueOfNode + " | ";
				}
			}
		}
		return "The Data: " + listOfData;
	}
	/**
	 * This function is used to display the nodes which has been queried and populated already. Used for testing purpose.
	 * @param dataType
	 * @return String showing the data queried
	 * @throws Exception if connection to Neo4j fails
	 */

	@UserFunction
	public String displayData(@Name("dataType") String dataType) throws Exception
	{
		String listOfData = "";
		int countLine = 0;
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			if(dataType.equals("train"))
			{

				listOfData = "train data: ";
				for(String node : trainDataList)
				{
					countLine++;
					listOfData = listOfData + node + "|";
				}
			}
			else if (dataType.equals("all"))
			{

				listOfData = "all data: ";
				for(String node : autoSplitDataList)
				{
					countLine++;
					listOfData = listOfData + node + "|";
				}
			}
			else
			{

				listOfData = "test data: ";
				for(String node : testDataList)
				{
					countLine++;
					listOfData = listOfData + node + "|";
				}

			}
		}
		return "Number of Lines: " + countLine + " The " + listOfData;

	}

	/**
	 * User defined function to create the decision tree with nodes and relationships in neo4j. This creates a tree based on information gain.
	 * @param target attribute
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	public String createTreeIG(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth) throws Exception {

		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{

			boolean isTrainListEmpty = trainDataList.isEmpty();
			boolean isTestListEmpty = testDataList.isEmpty();
			if(isTrainListEmpty && isTestListEmpty) {
				return target + "False";
			}else {
				int maxDepth = Integer.parseInt(max_depth);
				EvaluateTree mine = new EvaluateTree(trainDataList, testDataList, target, isPruned, maxDepth);

				confusionMatrix = mine.calculateAccuracy();

				PrintTree tree = new PrintTree();

				tree.createNodesForGraph(mine.getRoot());

				for (ArrayList<String> nodeDetail : tree.nodesBucket) {
					connector.createNode("DTInfoGain", "create nodes in neo4j", nodeDetail);
				}

				for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
					System.out.println("Relationship " + relationshipDetail);
					connector.createRelationship("DTInfoGain", "create relationship in neo4j \n", relationshipDetail);
				}

			}
			return "Create the Information Gain Decision Tree successful, " + confusionMatrix;
		}

	}

	/**
	 * User defined function to create the decision tree with nodes and relationships in neo4j. This creates a tree based on gini index.
	 * @param target attribute
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	public String createTreeGI(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth) throws Exception {

		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{

			boolean isTrainListEmpty = trainDataList.isEmpty();
			boolean isTestListEmpty = testDataList.isEmpty();
			if(isTrainListEmpty && isTestListEmpty) {
				return target + "False";
			}else {
				int maxDepth = Integer.parseInt(max_depth);
				EvaluateTreeGI mine = new EvaluateTreeGI(trainDataList, testDataList, target, isPruned, maxDepth);

				confusionMatrix = mine.calculateAccuracy();

				PrintTree tree = new PrintTree();

				tree.createNodesForGraph(mine.getRoot());

				for (ArrayList<String> nodeDetail : tree.nodesBucket) {
					connector.createNode("DTGini", "create nodes in neo4j", nodeDetail);
				}

				for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
					System.out.println("Relationship " + relationshipDetail);
					connector.createRelationship("DTGini", "create relationship in neo4j \n", relationshipDetail);
				}

			}
			return "Create the Gini Index Decision Tree successful, " + confusionMatrix;
		}

	}


	/**
	 * User defined function to create the decision tree with nodes and relationships in neo4j. This creates a tree based on gain ratio.
	 * @param target attribute
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	public String createTreeGR(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth) throws Exception {

		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
		{

			boolean isTrainListEmpty = trainDataList.isEmpty();
			boolean isTestListEmpty = testDataList.isEmpty();
			if(isTrainListEmpty && isTestListEmpty) {
				return target + "False";
			}else {
				int maxDepth = Integer.parseInt(max_depth);
				EvaluateTreeGR mine = new EvaluateTreeGR(trainDataList, testDataList, target, isPruned, maxDepth);

				confusionMatrix = mine.calculateAccuracy();

				PrintTree tree = new PrintTree();

				tree.createNodesForGraph(mine.getRoot());

				for (ArrayList<String> nodeDetail : tree.nodesBucket) {
					connector.createNode("DTGainRatio", "create nodes in neo4j", nodeDetail);
				}

				for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
					System.out.println("Relationship " + relationshipDetail);
					connector.createRelationship("DTGainRatio", "create relationship in neo4j \n", relationshipDetail);
				}

			}
			return "Create the Gain Ratio Decision Tree successful, " + confusionMatrix;
		}

	}


	/**
	 * User defined function to create the decision tree with nodes and relationships in neo4j
	 * @param path
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	public String createTreeGIcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGI mine = new EvaluateTreeGI(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();

			PrintTree tree = new PrintTree();

			tree.createNodesForGraph(mine.getRoot());


			in.close();

			for (ArrayList<String> nodeDetail : tree.nodesBucket) {
				connector.createNode("DTGini","create nodes in neo4j", nodeDetail);
			}

			for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
				System.out.println("Relationship " + relationshipDetail);
				connector.createRelationship("DTGini","create relationship in neo4j \n", relationshipDetail);
			}
		}

		return "Create the Gini Index Decision Tree successful, " + confusionMatrix;

	}
	/**
	 * This function creates tree from csv path which is based on gain ratio
	 * @param path The path is composed of 3 parts, 1st-training dataset, 2nd-test dataset, 3rd- target attribute(as string)
	 * @return
	 * @throws Exception
	 */

	@UserFunction
	public String createTreeGRcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth) throws Exception
	{
		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGR mine = new EvaluateTreeGR(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();

			PrintTree tree = new PrintTree();

			tree.createNodesForGraph(mine.getRoot());


			in.close();

			for (ArrayList<String> nodeDetail : tree.nodesBucket) {
				connector.createNode("DTGainRatio","create nodes in neo4j", nodeDetail);
			}

			for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
				System.out.println("Relationship " + relationshipDetail);
				connector.createRelationship("DTGainRatio","create relationship in neo4j \n" , relationshipDetail);
			}
		}
		return "Create the Gain Ratio Decision Tree successful, " + confusionMatrix;
	}


	/**
	 * This function creates tree from csv path which is based on information gain
	 *
	 * @param path - The path is composed of 3 parts, 1st-training dataset, 2nd-test dataset, 3rd- target attribute(as string)
	 * @return
	 * @throws Exception
	 */

	@UserFunction
	public String createTreeIGcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth )throws Exception
	{
		String confusionMatrix = "";
		try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123" ) )
		{
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTree mine = new EvaluateTree(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();

			PrintTree tree = new PrintTree();

			tree.createNodesForGraph(mine.getRoot());


			in.close();

			for (ArrayList<String> nodeDetail : tree.nodesBucket) {
				connector.createNode("DTInfoGain","create nodes in neo4j", nodeDetail);
			}

			for (ArrayList<String> relationshipDetail : tree.relationshipsBucket) {
				System.out.println("Relationship " + relationshipDetail);
				connector.createRelationship("DTInfoGain","create relationship in neo4j \n", relationshipDetail);
			}
		}

		return "Create the Info Gain Decision Tree successful, " + confusionMatrix;

	}

	/**
	 * This function retrieves the confusion matrix of decision tree based on information gain
	 * @param path
	 * @param target
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	@Description("retrieve the confusion matrix Information Gain Decision Tree")
	public String confmIGcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute , @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		if(trainPath == null || testPath == null )
		{
			return null;
		}
		else
		{
			String confusionMatrix = "";
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTree mine = new EvaluateTree(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Information Gain DT : " + confusionMatrix;
		}
	}


	/**
	 *
	 * This function retrieves the confusion matrix of decision tree based on gain ratio
	 * @param path
	 * @return
	 * @throws Exception
	 */

	@UserFunction
	@Description("retrieve the confusion matrix Gain Ratio Decision Tree")
	public String confmGRcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		if(trainPath == null || testPath == null)
		{
			return null;
		}
		else
		{
			String confusionMatrix = "";
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGR mine = new EvaluateTreeGR(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Gain Ratio DT: " + confusionMatrix;
		}
	}

	/**
	 *
	 * This function retrieves the confusion matrix of decision tree based on gini index
	 * @param path - The path is composed of 3 parts, 1st-training dataset, 2nd-test dataset, 3rd- target attribute(as string)
	 * @return A string with confusion matrix
	 * @throws Exception
	 */

	@UserFunction
	@Description("retrieve the confusion matrix Gini Index Decision Tree")
	public String confmGIcsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath, @Name("targetAttribute") String targetAttribute, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth) throws Exception
	{
		if(trainPath == null || testPath == null)
		{
			return null;
		}
		else
		{
			String confusionMatrix = "";
			Scanner in = new Scanner(System.in);

			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGI mine = new EvaluateTreeGI(trainPath, testPath, targetAttribute, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Gini Index DT: " + confusionMatrix;
		}
	}

	/**
	 * This function retrieves the confusion matrix of decision tree based on information gain
	 * @param path
	 * @param target
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	@Description("retrieve the confusion matrix Information Gain Decision Tree")
	public String confmIG(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		boolean isTrainListEmpty = trainDataList.isEmpty();
		boolean isTestListEmpty = testDataList.isEmpty();
		if(isTrainListEmpty && isTestListEmpty) {
			return "Need to query to data";
		}
		else
		{
			String confusionMatrix = "";
			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTree mine = new EvaluateTree(trainDataList, testDataList, target, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Information Gain DT : " + confusionMatrix;
		}
	}

	@UserFunction
	@Description("retrieve the confusion matrix Gain Ratio Decision Tree")
	public String confmGR(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		boolean isTrainListEmpty = trainDataList.isEmpty();
		boolean isTestListEmpty = testDataList.isEmpty();
		if(isTrainListEmpty && isTestListEmpty) {
			return "Need to query to data";
		}
		else
		{
			String confusionMatrix = "";
			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGR mine = new EvaluateTreeGR(trainDataList, testDataList, target, isPruned, maxDepth);

			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Gain Ratio DT: " + confusionMatrix;
		}
	}

	/**
	 *
	 * This function retrieves the confusion matrix of decision tree based on gini index
	 * @param path - The path is composed of 3 parts, 1st-training dataset, 2nd-test dataset, 3rd- target attribute(as string)
	 * @return A string with confusion matrix
	 * @throws Exception
	 */

	@UserFunction
	@Description("retrieve the confusion matrix Gini Index Decision Tree")
	public String confmGI(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth ) throws Exception
	{
		boolean isTrainListEmpty = trainDataList.isEmpty();
		boolean isTestListEmpty = testDataList.isEmpty();
		if(isTrainListEmpty && isTestListEmpty) {
			return "Need to query to data";
		}
		else
		{
			String confusionMatrix = "";
			int maxDepth = Integer.parseInt(max_depth);
			EvaluateTreeGI mine = new EvaluateTreeGI(trainDataList, testDataList, target, isPruned, maxDepth);


			confusionMatrix = mine.calculateAccuracy();
			return "The confusion Matrix for Gini Index DT: " + confusionMatrix;
		}
	}

	@UserFunction
	@Description("cross validation time for data from graph database for InfoGain")
	public String cvIG(@Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(target == null)
		{
			return null;
		}
		else
		{

			CrossValidation cv = new CrossValidation(autoSplitDataList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "InfoGain");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;

			return result;
		}
	}


	@UserFunction
	@Description("cross validation time for data from graph database for GainRatio")
	public String cvGR(@Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(target == null)
		{
			return null;
		}
		else
		{

			CrossValidation cv = new CrossValidation(autoSplitDataList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "GainRatio");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;

			return result;
		}
	}

	@UserFunction
	@Description("cross validation time for data from graph database for GiniIndex")
	public String cvGI(@Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(target == null)
		{
			return null;
		}
		else
		{

			CrossValidation cv = new CrossValidation(autoSplitDataList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "GiniIndex");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;

			return result;
		}
	}

	@UserFunction
	@Description("cross validation time for data from csv for InfoGain")
	public String cvIGcsv(@Name("path") String path, @Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(path == null)
		{
			return null;
		}
		else
		{
			ArrayList<String> customList = ProcessInputData.CustomListFromCSV(path);
			CrossValidation cv = new CrossValidation(customList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "InfoGain");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;

			return result ;
		}
	}




	/**
	 * To calculate the average of a list
	 * @param final_score
	 * @return
	 */

	private double calculateAverage(ArrayList<Double> final_score) {
		return final_score.stream()
				.mapToDouble(d -> d)
				.average()
				.orElse(0.0);
	}


	@UserFunction
	@Description("cross validation time for data from csv for GainRatio")
	public String cvGRcsv(@Name("path") String path, @Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(path == null)
		{
			return null;
		}
		else
		{

			ArrayList<String> customList = ProcessInputData.CustomListFromCSV(path);
			CrossValidation cv = new CrossValidation(customList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "GainRatio");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;

			return result ;
		}
	}

	@UserFunction
	@Description("cross validation time for data from csv for GiniIndex")
	public String cvGIcsv(@Name("path") String path, @Name("target") String target, @Name("numberOfFold") String numberOfFold) throws Exception
	{
		if(path == null)
		{
			return null;
		}
		else
		{

			ArrayList<String> customList = ProcessInputData.CustomListFromCSV(path);
			CrossValidation cv = new CrossValidation(customList, target);

			ArrayList<Double> final_score = cv.validate(Integer.parseInt(numberOfFold), "GiniIndex");
			double mcc = cv.getMccAverage();
			double generateTime = cv.getCvGenerationTimeAverage();
			double score = cv.getScoreAverage();
			String cfm = cv.getCfmDiabetes();
			String result = "calculated average mcc: " + mcc + "\n" + "calculated average generateTime: " + generateTime +
					"\n" + "confusion matrix: " + cfm +
					"\n" + "calculated average accuracy: " + score;


			return result ;
		}
	}

	@UserFunction
	@Description("generate the feature table from neo4j dataset")
	public String featureTable(@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth, @Name("Algorithm Type") String algoType) throws Exception
	{
		boolean isTrainListEmpty = trainDataList.isEmpty();
		boolean isTestListEmpty = testDataList.isEmpty();
		if(isTrainListEmpty && isTestListEmpty) {
			return "Need to query to data";
		}
		else
		{
			String featureTable = "";
			int maxDepth = Integer.parseInt(max_depth);
			if (algoType.equals("GR"))
			{
				EvaluateTreeGR mine = new EvaluateTreeGR(trainDataList, testDataList, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}
			else if (algoType.equals("GI"))
			{
				EvaluateTreeGI mine = new EvaluateTreeGI(trainDataList, testDataList, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}
			else
			{
				EvaluateTree mine = new EvaluateTree(trainDataList, testDataList, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}

			return "The feature table: " + featureTable;
		}
	}

	@UserFunction
	@Description("generate the feature table from neo4j dataset")
	public String featureTableCsv(@Name("trainPath") String trainPath,@Name("testPath") String testPath,@Name("target") String target, @Name("isPruned") String isPruned, @Name("maxDepth") String max_depth, @Name("Algorithm Type") String algoType) throws Exception
	{
		if(trainPath == null || testPath == null)
		{
			return null;
		}
		else
		{
			String featureTable = "";
			int maxDepth = Integer.parseInt(max_depth);
			if (algoType.equals("GR"))
			{
				EvaluateTreeGR mine = new EvaluateTreeGR(trainPath, testPath, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}
			else if (algoType.equals("GI"))
			{
				EvaluateTreeGI mine = new EvaluateTreeGI(trainPath, testPath, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}
			else
			{
				EvaluateTree mine = new EvaluateTree(trainPath, testPath, target, isPruned, maxDepth);
				mine.calculateAccuracy();
				featureTable = mine.getFeatureTable();
			}

			return "The feature table: " + featureTable;
		}

	}
	
    private static void displayMatrix(RealMatrix matrix, String matrixName) {
        System.out.println(matrixName + ": ");
        int rows = matrix.getRowDimension();
        int cols = matrix.getColumnDimension();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                System.out.printf("%.8f ", matrix.getEntry(row, col));
            }
            System.out.println();
        }
        System.out.println();
    }
    
    /**
     * Connect clustering nodes to centroid
     * @param node_type String type of node
     * @param message String the message of connect nodes
     * @param node_centroid String centroid node
     * @param node_cluster String node clusters
     * @param distance String distance of node to centroid
     */
	public void connectNodes(final String node_type, final String message, final String node_centroid, final String node_cluster, final double distance)
    {
    	final String name = "kmean";
        try ( Session session = driver.session() )
        {
            String greeting = session.writeTransaction( new TransactionWork<String>()
            {
                @Override
                public String execute( Transaction tx )
                {
                	//a is present for the node
            		Result result = tx.run( "MERGE (a"+":ClusteringNodeType" +" {" + node_centroid +"}) " +
            				"MERGE (b "+":" + node_type + " {" + node_cluster +"}) " +
            				"MERGE (a)-[r:cluster]->(b) "  +
                            "SET r.distance = " + distance + " " + 
            				"RETURN a.message");
				    return result.single().get( 0 ).asString();
                }
            } );
        }
    }
	
	/**
	 * Procedure to map the node data in Neo4j so the plugin can perform clustering
	 * @param node_type String type of node
	 * @param overlook String "a,b,c" the attribute that need to overlook and not take into consider when perform clustering
	 * @return
	 * @throws Exception
	 */
	@UserFunction
	public String mapNodes(@Name("nodeSet") String node_type, @Name("overlook") String overlook) throws Exception {
	    String list_of_data = "";
	    String[] over_look_array = new String[0];
	    mapNodeList.clear();
	    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
	        if (!overlook.isEmpty()) {
	        	over_look_array = overlook.split(",");
	        }
	        queryData(node_type);
	        for (Record key : dataKey) {
	            List<Pair<String, Value>> values = key.fields();
	            for (Pair<String, Value> nodeValues : values) {
	                if ("n".equals(nodeValues.key())) {
	                    Value value = nodeValues.value();
	                    String valueOfNode = getNodeValues(value, over_look_array);
	                    mapNodeList.add(valueOfNode);
	                    list_of_data = mapNodeList.toString();
	                }
	            }
	        }
	    }
	    return "Map all node data: " + list_of_data;
	}
	
	/**
	 * function to get the node attribute except for overlook attributes
	 * @param value Value attribute of node
	 * @param over_look_array String[] string array of overlook attributes
	 * @return
	 */
	private String getNodeValues(Value value, String[] overlook_array) {
	    StringBuilder value_of_node = new StringBuilder();
	    for (String nodeKey : value.keys()) {
	        if (overlook_array.length > 0 && Arrays.asList(overlook_array).contains(nodeKey)) {
	            continue;
	        }
	        try {
	            double num = Double.parseDouble(String.valueOf(value.get(nodeKey)));
	            if (value.get(nodeKey).getClass().equals(String.class)) {
	            	value_of_node.append(getStringValue(value_of_node)).append(nodeKey).append(":").append(value.get(nodeKey));
	            } else {
	            	value_of_node.append(getStringValue(value_of_node)).append(nodeKey).append(":").append(value.get(nodeKey));
	            }
	        } catch (NumberFormatException e) {
	            System.out.println(value.get(nodeKey) + " is not a number.");
	        }
	    }
	    return value_of_node.toString();
	}
	
	private String getStringValue(StringBuilder valueOfNode) {
	    return valueOfNode.length() > 0 ? ", " : "";
	}
	
	 /**
     * Procedure for k-means clustering and visualization in neo4j
     * @param nodeSet type of node
     * @param numberOfCentroid 
     * @param numberOfInteration
     * @return cluster result and visualize
     * @throws Exception
     */
    @UserFunction
    @Description("Kmean clustering function")
	public String kmean(@Name("node_type") String node_type, @Name("number_of_centroid") String number_of_centroid, @Name("number_of_iteration") String number_of_iteration, @Name("distance_measure") String distance_measure) throws Exception
	{
    	predictedNodeLabels.clear();
    	try ( OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j( "bolt://localhost:7687", "neo4j", "123412345" ) )
        {
			String averageSilhouetteCoefficientString = "The average Silhouette Coefficient value is: ";
			HashMap<String, ArrayList<String>> kmeanAssign = new HashMap<String, ArrayList<String>>();
			int number_of_centroid_int = Integer.parseInt(number_of_centroid);
			int number_of_iteration_int = Integer.parseInt(number_of_iteration);
			double centroidNumber = 1.0;
			
			kmeanAssign = Unsupervised.KmeanClust(mapNodeList, number_of_centroid_int, number_of_iteration_int, distance_measure);
			double averageSilhouetteCoefficientValue = Unsupervised.averageSilhouetteCoefficient(kmeanAssign, distance_measure);
	        
			for (String centroid : kmeanAssign.keySet()) {
			    ArrayList<String> clusterNode = kmeanAssign.get(centroid);
			    for (int i = 0; i < clusterNode.size(); i++) {
			    	//Add predict labels
		    		predictedNodeLabels.add(centroidNumber);

			    	DecimalFormat decimalFormat = new DecimalFormat("#.###");
			        double distance = Unsupervised.calculateDistance(clusterNode.get(i), centroid, distance_measure);
			        String formattedDistance = decimalFormat.format(distance);
			        double roundedDistance = Double.parseDouble(formattedDistance);
			        connector.connectNodes(node_type, "create relationship in kmean node", centroid, clusterNode.get(i), roundedDistance);
			    }
			    centroidNumber = centroidNumber + 1;
			}

			return averageSilhouetteCoefficientString + averageSilhouetteCoefficientValue + " predicted labels: " + predictedNodeLabels;
		}
	}
    
    /**
     * Procedure to calculate the mean of the Silhouette Coefficients for all point
     * @param node_type String node type
     * @param number_of_centroid String number of centroids
     * @param numberOfInteration
     * @param distanceMeasure
     * @return
     * @throws Exception
     */
    @UserFunction
    @Description("Calculate the mean of the Silhouette Coefficients for all point")
	public String averageSilhouetteCoefficient(@Name("node_type") String node_type, @Name("number_of_centroid") String number_of_centroid, @Name("number_of_iteration") String number_of_iteration, @Name("distance_measure") String distance_measure) throws Exception
	{
    	if(node_type != null)
    	{
			String average_silhouette_coefficient_string = "The average Silhouette Coefficient value is: ";
			HashMap<String, ArrayList<String>> kmean_assign = new HashMap<String, ArrayList<String>>();
			int number_of_centroid_int = Integer.parseInt(number_of_centroid);
			int number_of_iteration_int = Integer.parseInt(number_of_iteration);
			kmean_assign = Unsupervised.KmeanClust(mapNodeList, number_of_centroid_int, number_of_iteration_int, distance_measure);
			double average_silhouette_coefficient_value = Unsupervised.averageSilhouetteCoefficient(kmean_assign, distance_measure);
	        return average_silhouette_coefficient_string + average_silhouette_coefficient_value ;
		}
    	else
    	{
    		return null;
    	}
	}
    
    //////////////////////////////Calculation of Ajusted rand index
    
   
    /**
     * function to convert string class label into list of double
     * @param strings List<String> list of string attributes
     * @return
     */
    public static List<Double> convertStringLabels(List<String> strings) {
        Map<String, Double> label_map = new HashMap<>();
        List<Double> labels = new ArrayList<>();

        double current_label = 0.0;
        for (String s : strings) {
            if (!label_map.containsKey(s)) {
            	label_map.put(s, current_label++);
            }
            labels.add(label_map.get(s));
        }

        return labels;
    }
    
    /**
     * Procedure to get the true labels from the node type and calculate the ajusted rand index 
     * @param note_type
     * @param true_labels
     * @return
     * @throws Exception
     */
    @UserFunction
	public String ajustedRandIndex(@Name("note_type") String note_type, @Name("true_labels") String true_labels) throws Exception {
	    if(predictedNodeLabels.size()==0)
	    {
	    	
	    	return " predicted Labels is null, please run kmean clustering to add the predicted labels";
	    }
	    else {
	    	String list_of_data = "";
	    	Double ajusted_rand_index_value = 0.0;
		    trueNodeLabels.clear();
		    List<String> string_true_node_labels_list = new ArrayList<String>();
		    try (OutputDecisionTreeNeo4j connector = new OutputDecisionTreeNeo4j("bolt://localhost:7687", "neo4j", "123412345")) {
		        queryData(note_type);
		        for (Record key : dataKey) {
		            List<Pair<String, Value>> values = key.fields();
		            for (Pair<String, Value> node_values : values) {
		                if ("n".equals(node_values.key())) {
		                    Value value = node_values.value();
		                    for (String node_key : value.keys()) {
		                    	if(node_key.equals(true_labels))
		                    	{
		                    		try {
		                	            double num = Double.parseDouble(String.valueOf(value.get(node_key)));
	                	            	trueNodeLabels.add(num);
	                	            	list_of_data = list_of_data + num;
		                	        } catch (NumberFormatException e) {
		                	            System.out.println(value.get(node_key) + " is not a number.");
		                	            string_true_node_labels_list.add(String.valueOf(value.get(node_key)));
		                	        }
		                    	}
		                    }
		                }
		            }
		        }
		        if(string_true_node_labels_list.size() != 0 )
		        {
		        	trueNodeLabels =  convertStringLabels(string_true_node_labels_list);
		        }
		        
		        if(trueNodeLabels.size() != predictedNodeLabels.size())
		        {
		        	return "true labels size: " + trueNodeLabels +" and predicted labels:" + predictedNodeLabels + " does not have the same size";
		        }
		        else {
		        	ajusted_rand_index_value = calculateAdjustedRandIndex(trueNodeLabels, predictedNodeLabels);
				}
		    }
		    return "ajusted rand index of " + note_type + " is: " + ajusted_rand_index_value ;
	    }
	}
    
    /**
     * Function to calculate the adjusted rand index by using list of true labels and predicted labels
     * @param trueLabels
     * @param predictedLabels
     * @return
     */
    public static double calculateAdjustedRandIndex(List<Double> true_labels, List<Double> predicted_labels) {
        if (true_labels.size() != predicted_labels.size()) {
            throw new IllegalArgumentException("Input lists must have the same length");
        }

        int n = true_labels.size();
        Map<Double, Map<Double, Double>> contingency_table = new HashMap<>();
        Map<Double, Double> true_label_counts = new HashMap<>();
        Map<Double, Double> predicted_label_counts = new HashMap<>();

        for (int i = 0; i < n; i++) {
            double true_label_double = true_labels.get(i);
            double predicted_label_double = predicted_labels.get(i);

            contingency_table.computeIfAbsent(true_label_double, k -> new HashMap<>());
            contingency_table.get(true_label_double).merge(predicted_label_double, 1.0, Double::sum);

            true_label_counts.merge(true_label_double, 1.0, Double::sum);
            predicted_label_counts.merge(predicted_label_double, 1.0, Double::sum);
        }

        double a = 0.0; // Number of pairs in the same cluster in both true and predicted
        for (Map<Double, Double> row : contingency_table.values()) {
            for (double count : row.values()) {
                a += count * (count - 1) / 2.0;
            }
        }

        double b = 0.0; // Number of pairs in the same cluster in trueLabels
        for (double count : true_label_counts.values()) {
            b += count * (count - 1) / 2.0;
        }

        double c = 0.0; // Number of pairs in the same cluster in predictedLabels
        for (double count : predicted_label_counts.values()) {
            c += count * (count - 1) / 2.0;
        }

        double total_pairs = n * (n - 1) / 2.0;
        double expected_index = (b * c) / total_pairs;
        double max_index = 0.5 * (b + c);
        double adjusted_randIndex = (a - expected_index) / (max_index - expected_index);

        return adjusted_randIndex;
    }

}
