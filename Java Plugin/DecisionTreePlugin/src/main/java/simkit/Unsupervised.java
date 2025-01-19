package simkit;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Procedure;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;


import scala.util.Random;

/**
 * @author 49171
 *
 */
public class Unsupervised {
	
	public static ArrayList<String> dummyData(){
		ArrayList<String> data = new ArrayList<String>();
		String line1 ="y_cordinate:7.0,x_cordinate:1.0";
		String line2 ="y_cordinate:6.0,x_cordinate:1.0";
		String line3 ="y_cordinate:2.0, x_cordinate:6.0";
		String line4 ="y_cordinate:1.0, x_cordinate:8.0";
		String line5 ="y_cordinate:2.0, x_cordinate:10.0";
//		String line1 ="y_cordinate:0.536804,x_cordinate:0.449056";
//		String line2 ="y_cordinate:0.533213,x_cordinate:0.454964";
//		String line3 ="y_cordinate:-0.247063,x_cordinate:0.474114";
//		String line4 ="y_cordinate:-0.448465,x_cordinate:0.444607";
//		String line5 ="y_cordinate:-0.406650, x_cordinate:0.410971";
		data.add(line1);
		data.add(line2);
		data.add(line3);
		data.add(line4);
		data.add(line5);
		return data;
	}
	
	public static void main(String[] args) throws Exception {
		  ArrayList<String> inputData = dummyData();
		  ArrayList<String> debug = new ArrayList<>();
		  HashMap<String, ArrayList<String>> dbAssign = KmeanClust(inputData, 2, 20, "Euclidean", false, debug, 42);
		  for (String centroid: dbAssign.keySet()) {
			  System.out.println("1");
      		ArrayList<String> clusterNode = dbAssign.get(centroid);
      		System.out.println("number of centroid " +  centroid);
      		System.out.println(clusterNode.toString());
  		  }
		  
		  Double silhouetteValue = averageSilhouetteCoefficient(dbAssign, "Euclidean");
		  System.out.println(averageSilhouetteCoefficient(dbAssign, "Euclidean"));

	}
	
	/**
	-------------------------------------------------------------------------------DBSCAN Clustering ------------------------------------------------------------------------------------------
	*/
	
	/**
     * Determines the neighbours of a given input value.
     * 
     * @param inputValue value for which neighbours are to be calculated
     * @return list of neighbours
     */
    public static ArrayList<String> getNeighbours(final String inputValue, final String distanceMetric) 
    {
        ArrayList<String> neighbours = new ArrayList<String>();
        double distance = 0;
        
        for(int i=0; i<inputValues.size(); i++) {
        	
            String candidate = inputValues.get(i);
            
            if(distanceMetric.equals("bray-curtis"))
            {
            	distance = calBrayCurtis(inputValue, candidate);
            }
            else if(distanceMetric.equals("manhattan"))
            {
            	distance = calManhattanDist(inputValue, candidate);
            }
            else if(distanceMetric.equals("cosine"))
            {
            	distance = calCosineSimilarity(inputValue, candidate);
            }
            else
            {
            	distance = calEuclideanDist(inputValue, candidate);
            }
            System.out.println(distance);
            if (distance <= epsilon) {
                neighbours.add(candidate);
            }
        }
        return neighbours;
    }
    
    /**
     * Merges the elements of the right collection to the left one and returns
     * the combination.
     * 
     * @param neighbours1 left collection
     * @param neighbours2 right collection
     * @return Modified left collection
     */
    public static ArrayList<String> mergeRightToLeftCollection(final ArrayList<String> neighbours1,final ArrayList<String> neighbours2) 
    {
        for (int i = 0; i < neighbours2.size(); i++) {
            String tempPt = neighbours2.get(i);
            if (!neighbours1.contains(tempPt)) {
                neighbours1.add(tempPt);
            }
        }
        return neighbours1;
    }
    
    /**
     * Applies the clustering and returns a collection of clusters (i.e. a list
     * of lists of the respective cluster members).
     * 
     * @return
     */
    
	public static double epsilon;
	public static ArrayList<String> inputValues;
	
    public static HashMap<String, ArrayList<String>> DbClust(final ArrayList<String> inputData, double eps, int minPts, String distanceMetric )
    {

    	inputValues = inputData;
    	epsilon = eps;
    	
    	HashMap<String, ArrayList<String>> resultHashmap = new HashMap<String, ArrayList<String>>();
        ArrayList<ArrayList<String>> resultList = new ArrayList<ArrayList<String>>();
        ArrayList<String> visitedPoints = new ArrayList<String>();
		visitedPoints.clear();

        ArrayList<String> neighbours;
        int index = 0;
        while (inputValues.size() > index) {
            String p = inputValues.get(index);
            if (!visitedPoints.contains(p)) {
                visitedPoints.add(p);
                neighbours = getNeighbours(p , distanceMetric);
                if (neighbours.size() >= minPts) {
                    int ind = 0;
                    while (neighbours.size() > ind) {
                        String r = neighbours.get(ind);
                        if (!visitedPoints.contains(r)) {
                            visitedPoints.add(r);
                            ArrayList<String> individualNeighbours = getNeighbours(r, distanceMetric);
                            if (individualNeighbours.size() >= minPts) {
                                neighbours = mergeRightToLeftCollection(neighbours,individualNeighbours);
                            }
                        }
                        ind++;
                    }
                    resultList.add(neighbours);
                }
            }
            index++;
        }
        for (ArrayList<String> cluster : resultList)
        {
        	String newCentroid = calculateNewCentroid(cluster);
        	resultHashmap.put(newCentroid, cluster);
        }
        return resultHashmap;
    }
    
    
	/**
	-------------------------------------------------------------------------------K-MEANS Clustering ------------------------------------------------------------------------------------------
	*/


static HashMap<String, ArrayList<String>> replaceValuesWithOriginalSet(
        HashMap<String, ArrayList<String>> clusterAssign,
        ArrayList<String> originalNodeSet,
		Log log
) {
    // Create a mapping from ID to original node (without 'eigenvector' attributes)
    HashMap<String, String> idToOriginalNode = new HashMap<>();
    for (String originalNode : originalNodeSet) {
        // Remove attributes starting with 'eigenvector'
        String cleanedNode = removeEigenvectorAttributes(originalNode);

        String id = extractID(cleanedNode);
        if (id != null) {
            idToOriginalNode.put(id, cleanedNode);
        }
    }
	log.info(String.valueOf(idToOriginalNode));

    // Initialize the updated cluster assignments
    HashMap<String, ArrayList<String>> updatedClusterAssign = new HashMap<>();

    // Keep track of all node IDs that have been assigned to clusters
    Set<String> assignedNodeIDs = new HashSet<>();

    // Replace centroids and cluster nodes with values from originalNodeSet
    for (Map.Entry<String, ArrayList<String>> entry : clusterAssign.entrySet()) {
        String centroid = entry.getKey();
        ArrayList<String> clusterNodes = entry.getValue();

        // Replace centroid
        String centroidID = extractID(centroid);
		 float floatCentroidID = Float.parseFloat(centroidID);
		int centroidIDInt = (int) floatCentroidID;
        String originalCentroid = idToOriginalNode.getOrDefault(String.valueOf(centroidIDInt), centroid);
        log.info(String.valueOf(centroidIDInt));
		log.info(originalCentroid);

        // Replace cluster nodes
        ArrayList<String> updatedClusterNodes = new ArrayList<>();
        for (String node : clusterNodes) {
            String nodeID = extractID(node);
            String originalNode = idToOriginalNode.getOrDefault(nodeID, node);
			log.info(nodeID);
			log.info(originalNode);
            updatedClusterNodes.add(originalNode);
            assignedNodeIDs.add(nodeID);
        }

        updatedClusterAssign.put(originalCentroid, updatedClusterNodes);
        assignedNodeIDs.add(centroidID);
    }

    // Add all original nodes to the clusters if they haven't been assigned yet
    ArrayList<String> unassignedNodes = new ArrayList<>();
    for (String originalNode : idToOriginalNode.values()) {
        String nodeID = extractID(originalNode);
        if (!assignedNodeIDs.contains(nodeID)) {
            unassignedNodes.add(originalNode);
        }
    }

    // Optionally, assign unassigned nodes to a separate cluster or distribute them
    if (!unassignedNodes.isEmpty()) {
        updatedClusterAssign.put("Unassigned", unassignedNodes);
    }

	// Define the prefixes of attributes to remove
	String[] prefixesToRemove = new String[]{"index", "id"};

	// Create a new HashMap to store the cleaned entries
	HashMap<String, ArrayList<String>> cleanedClusterAssign = new HashMap<>();

	for (Map.Entry<String, ArrayList<String>> entry : updatedClusterAssign.entrySet()) {
		String key = entry.getKey();
		ArrayList<String> valueList = entry.getValue();

		// Remove 'index' and 'id' attributes from the key
		String cleanedKey = removeAttributes(key, prefixesToRemove);

		// Create a new list to store cleaned values
		ArrayList<String> cleanedValueList = new ArrayList<>();

		// Iterate over the values in the valueList
		for (String value : valueList) {
			// Remove 'index' and 'id' attributes from the value
			String cleanedValue = removeAttributes(value, prefixesToRemove);
			// Add the cleaned value to the cleanedValueList
			cleanedValueList.add(cleanedValue);
		}

		// Put the cleaned key and cleaned value list into the cleanedClusterAssign
		cleanedClusterAssign.put(cleanedKey, cleanedValueList);
	}

	// Replace the original updatedClusterAssign with the cleaned version
	updatedClusterAssign = cleanedClusterAssign;

    return updatedClusterAssign;
}

/**
 * Removes attributes starting with specified prefixes from a node string.
 * @param node The node string
 * @param prefixes The array of prefixes to remove
 * @return The node string without the specified attributes
 */
private static String removeAttributes(String node, String[] prefixes) {
    String[] attributes = node.split(",");
    ArrayList<String> filteredAttributes = new ArrayList<>();
    for (String attr : attributes) {
        String[] keyValue = attr.split(":");
        if (keyValue.length < 2) continue; // Skip malformed attributes

        String key = keyValue[0].trim().toLowerCase();
        boolean remove = false;
        for (String prefix : prefixes) {
            if (key.startsWith(prefix.toLowerCase())) {
                remove = true;
                break;
            }
        }
        if (!remove) {
            filteredAttributes.add(attr);
        }
    }
    return String.join(",", filteredAttributes);
}

/**
 * Removes attributes starting with 'eigenvector' from a node string.
 * @param node The node string
 * @return The node string without 'eigenvector' attributes
 */
private static String removeEigenvectorAttributes(String node) {
    String[] attributes = node.split(",");
    ArrayList<String> filteredAttributes = new ArrayList<>();
    for (String attr : attributes) {
        String key = attr.split(":")[0].trim();
        if (!key.startsWith("eigenvector")) {
            filteredAttributes.add(attr);
        }
    }
    return String.join(",", filteredAttributes);
}

/**
 * Extracts the ID from a node string.
 * Assumes the ID is in the format "ID:value"
 * @param node The node string
 * @return The extracted ID, or null if not found
 */
private static String extractID(String node) {
    String[] attributes = node.split(",");
    for (String attr : attributes) {
        String[] keyValue = attr.split(":");
//        if (keyValue.length == 2 && keyValue[0].trim().equalsIgnoreCase("index")) {
        if (keyValue[0].trim().equalsIgnoreCase("index") || keyValue[0].trim().equalsIgnoreCase("id")) {
            return keyValue[1].trim();
        }
    }
    return null; // ID not found
}

// Initialize the random generator on the FastAPI server with a specific seed
    private static void initializeRandomGenerator(int seed) throws Exception {
        String urlString = "http://91.107.235.104:8000/init?seed=" + seed;
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.getResponseCode();  // Ensure the request completes
        connection.disconnect();
    }

    // Fetch the next random integer from the FastAPI server
    private static int getNextRandomInt(int maxValue) throws Exception {
        String urlString = "http://91.107.235.104:8000/next-int?max_value=" + maxValue;
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        in.close();
        connection.disconnect();

        JSONObject jsonResponse = new JSONObject(content.toString());
        return jsonResponse.getInt("next_int");
    }

	/**
	 * This is the main method to perform k-means clustering.
	 * @param inputData is a variable where the nodes from Neo4j are stored
	 * @param numberOfCentroids store the number of centroids specified by user for clustering
	 * @param numberOfInteration saves user specified iteration to find convergence
	 * @param distanceMeasure the distance measure to use
	 * @param useOriginalNodeSet boolean to control whether to replace values with originalNodeSet
	 * @param originalNodeSet the original nodes to replace in the output
	 * @return HashMap with centroids and their assigned nodes
	 */
	public static HashMap<String, ArrayList<String>> KmeanClust(
		ArrayList<String> inputData,
		int numberOfCentroids,
		int numberOfInteration,
		String distanceMeasure,
		boolean useOriginalNodeSet,
		ArrayList<String> originalNodeSet,
		int seed
	) throws Exception {
		HashMap<String, ArrayList<String>> kmeanAssign = new HashMap<>();
		ArrayList<String> listOfCentroid = new ArrayList<>();
		ArrayList<String> listOfRemain = new ArrayList<>(inputData);

		// Initializing centroids by random choice
		//java.util.Random rand = new java.util.Random();
		initializeRandomGenerator(seed);


		while (listOfCentroid.size() < numberOfCentroids) {
			//int randomIndex = rand.nextInt(inputData.size());
			int randomIndex = getNextRandomInt(inputData.size());  // Fetch the next random index
			String potentialCentroid = inputData.get(randomIndex);

			if (!listOfCentroid.contains(potentialCentroid)) {
				listOfCentroid.add(potentialCentroid);
			}
		}

		// Remove centroids from the remaining list
		listOfRemain.removeAll(listOfCentroid);

		// First clusters
		HashMap<String, ArrayList<String>> hashClusterAssign = distanceAssign(listOfCentroid, listOfRemain, distanceMeasure);
		// All iterations
		kmeanAssign = kmeanIteration(hashClusterAssign, numberOfInteration, inputData, distanceMeasure);

		return kmeanAssign;
	}
	
	/**
	 * Method to perform the iterations of k-means
	 * @param clusterAssign contains the first cluster assignments
	 * @param numberOfInteration specified by user
	 * @param inputData specified by user
	 * @return
	 */
	public static HashMap<String, ArrayList<String>> kmeanIteration(HashMap<String, ArrayList<String>> clusterAssign, int numberOfInteration, ArrayList<String> inputData, String distanceMeasure) {
	    for (int i = 0; i < numberOfInteration; i++) {
	        // Calculate new centroids and update clusterAssign
	        clusterAssign = calculateAndUpdateCentroids(clusterAssign);

	        // Perform distance assignment again with the updated centroids
	        clusterAssign = distanceAssign(new ArrayList<>(clusterAssign.keySet()), inputData, distanceMeasure);
	    }
	    return clusterAssign;
	}
	
	/**
	 * Method to calculate new centroid points after each iteration
	 * @param listOfNodesInCluster nodes assigned to each cluster
	 * @return returns new centroids after each iteration
	 */
	public static String calculateNewCentroid(ArrayList<String> listOfNodesInCluster) {
	    if (listOfNodesInCluster.isEmpty()) {
	        return null; // Handle empty cluster case
	    }

	    // Extract attribute names from the first node
	    String[] firstNodeAttributes = listOfNodesInCluster.get(0).split(",");
	    String[] attributeNames = new String[firstNodeAttributes.length];
	    for (int i = 0; i < firstNodeAttributes.length; i++) {
	        attributeNames[i] = firstNodeAttributes[i].split(":")[0].trim();
	    }

	    double[] attributeSums = new double[attributeNames.length];

	    for (String node : listOfNodesInCluster) {
	        String[] attributes = node.split(",");
	        for (int i = 0; i < attributes.length; i++) {
	            try {
	                attributeSums[i] += Double.parseDouble(attributes[i].split(":")[1].trim());
	            } catch (NumberFormatException e) {
	                System.err.println("Error parsing number: " + attributes[i]);
	                return null; // or handle the error appropriately
	            }
	        }
	    }

	    StringBuilder newCentroid = new StringBuilder();
	    for (int i = 0; i < attributeSums.length; i++) {
	        double average = attributeSums[i] / listOfNodesInCluster.size();
	        newCentroid.append(attributeNames[i]).append(":").append(average);
	        if (i < attributeSums.length - 1) {
	            newCentroid.append(",");
	        }
	    }
		// Log the new centroid
    	System.out.println("New centroid: " + newCentroid.toString());

	    return newCentroid.toString();
	}
	
	/**
	 * Method to calculate and update centroids in the clusterAssign.
	 *
	 * @param clusterAssign The current cluster assignments
	 * @return Updated cluster assignments with new centroids
	 */
	private static HashMap<String, ArrayList<String>> calculateAndUpdateCentroids(HashMap<String, ArrayList<String>> clusterAssign) {
	    HashMap<String, ArrayList<String>> updatedClusterAssign = new HashMap<>();

	    for (String key : clusterAssign.keySet()) {
	        ArrayList<String> clusterNodes = clusterAssign.get(key);
	        String newCentroid = calculateNewCentroid(clusterNodes);
	        if (newCentroid != null) { // Check for null to handle empty clusters
	            updatedClusterAssign.put(newCentroid, clusterNodes);
	        }
	    }
	    return updatedClusterAssign;
	}
	
	public static HashMap<String, ArrayList<String>> distanceAssign (ArrayList<String> listOfCentroid, ArrayList<String> listOfRemain, String distanceMeasure) {
	    HashMap<String, ArrayList<String>> hashClusterAssign = new HashMap<String, ArrayList<String>>();
	    // Calculate distance and assign points to clusters
	    for (int i = 0; i < listOfRemain.size(); i++) {
	        double minDistance = Double.MAX_VALUE;
	        String closestCentroid = null;

	        for (int j = 0; j < listOfCentroid.size(); j++) {
	            double distance = calculateDistance(removeIndexAndIdEntries(listOfRemain.get(i)), removeIndexAndIdEntries(listOfCentroid.get(j)), distanceMeasure);
	            if (distance < minDistance) {
	                minDistance = distance;
	                closestCentroid = listOfCentroid.get(j);
	            }
	        }
	        hashClusterAssign.computeIfAbsent(closestCentroid, k -> new ArrayList<>()).add(listOfRemain.get(i));
	    }
	    // Ensure all centroids are in the hashmap, even if they have no assigned points
	    for (String centroid : listOfCentroid) {
	        hashClusterAssign.putIfAbsent(centroid, new ArrayList<>());
	    }

	    return hashClusterAssign;
	}

        public static String removeIndexAndIdEntries(String centroidString) {
        // Split the string by commas to get individual key-value pairs
        String[] entries = centroidString.split(",");

        // Use a StringBuilder to collect entries that don't contain "index" or "id"
        StringBuilder result = new StringBuilder();

        for (String entry : entries) {
            // Check if the entry contains a key-value pair
            if (entry.contains(":")) {
                String[] keyValue = entry.split(":");
                if (keyValue.length > 1) {
                    String key = keyValue[0].trim();
                    key.toLowerCase();
                    // Only append the entry if it doesn't contain "index" or "id"
                    if (!key.equalsIgnoreCase("index") && !key.equalsIgnoreCase("id") && !key.equalsIgnoreCase(" index") && !key.equalsIgnoreCase(" id")) {
                        if (result.length() > 0) {
                            result.append(", ");
                        }
                        result.append(entry);
                    }
                }
            }
        }

        return result.toString();
    }


	static double calculateDistance(String point1, String point2, String distanceMeasure) {
	    switch (distanceMeasure.toLowerCase()) {
	        case "manhattan":
	            return calManhattanDist(point1, point2);
	        case "cosine":
	            return calCosineSimilarity(point1, point2);
	        case "bray-curtis":
	            return calBrayCurtis(point1, point2);
	        default:
	            return calEuclideanDist(point1, point2);
	    }
	}
	
	
	/**
	--------------------------------------------------------------------------Distance Measures for Clustering ------------------------------------------------------------------------------------------
	*/
	
	/**
	 * Euclidean distance calculation from point A to point B
	 * @param start point A
	 * @param end point B
	 * @return
	 */
	public static double calEuclideanDist (String start, String end)
	{
		double distance = 0.00;
		String[] startSplit =  start.split(",");
		String[] endSplit = end.split(",");
		for(int i = 0; i < startSplit.length; i++)
		{
//            String[] startAttr = startSplit[i].split(":");
//            String[] endAttr = endSplit[i].split(":");
//
//            String startKey = startAttr[0].trim();
//            String endKey = endAttr[0].trim();
//            float endValue = Float.parseFloat(endAttr[1].trim());
            
			float startValue = Float.parseFloat(startSplit[i].split(":")[1]);
			float endValue = Float.parseFloat(endSplit[i].split(":")[1]);
//            
//            if (!startKey.equals(endKey)) {
//                System.err.println("Attribute key mismatch at index " + i + ". Start: " + startKey + ", End: " + endKey);
//                throw new IllegalArgumentException("Attribute key mismatch");
//            }
			distance = distance + Math.pow((startValue-endValue),2);
		}
		distance = Math.sqrt(distance);
		return distance;
		
	}
	/**
	 * Calculate Manhattan distance between point A and B
	 * @param start point A
	 * @param end point B
	 * @return
	 */
	public static double calManhattanDist (String start, String end)
	{
		double distance = 0.00;
		String[] startSplit =  start.split(",");
		String[] endSplit = end.split(",");
		
		for(int i = 0; i < startSplit.length; i++)
		{
			float startValue = Float.parseFloat(startSplit[i].split(":")[1]);
			float endValue = Float.parseFloat(endSplit[i].split(":")[1]);
			distance = distance + Math.abs(startValue - endValue);
		}
		return distance;
		
	}
	/**
	 * Calculate Cosine similarity between point A and B
	 * @param start point A
	 * @param end point B
	 * @return
	 */
	public static double calCosineSimilarity (String start, String end)
	{
		double distance = 0.00;
		double dotProduct = 0.00;
		double normA = 0.00;
		double normB = 0.00;
		
		String[] startSplit =  start.split(",");
		String[] endSplit = end.split(",");
		for(int i = 0; i < startSplit.length; i++)
		{
			float startValue = Float.parseFloat(startSplit[i].split(":")[1]);
			float endValue = Float.parseFloat(endSplit[i].split(":")[1]);
			dotProduct += startValue * endValue;
			normA += Math.pow(startValue, 2);
			normB += Math.pow(endValue, 2);
		}
		distance = dotProduct/ (Math.sqrt(normA) * Math.sqrt(normB));
		return distance;
		
	}
	/**
	 * Calculate Bray-Curtis dissimilarity between point A and B
	 * @param start point A
	 * @param end point B
	 * @return
	 */
	public static double calBrayCurtis (String start, String end)
	{
		double distance = 0.00;
		double num = 0.00;
		double den = 0.00;
		
		String[] startSplit =  start.split(",");
		String[] endSplit = end.split(",");
		for(int i = 0; i < startSplit.length; i++)
		{
			float startValue = Float.parseFloat(startSplit[i].split(":")[1]);
			float endValue = Float.parseFloat(endSplit[i].split(":")[1]);
			num = num + Math.abs(startValue - endValue);
			den = den + Math.abs(startValue + endValue);
		}
		distance = num/den;
		return distance;
		
	}
	
	//a(i) : Calculate the average distance of point i to other points in its cluster.
	public static double averageIntraClusterDistance(String point, ArrayList<String> cluster, String distanceMeasure) {
	    if (cluster.size() <= 1) {
	        System.err.println("Cluster has only one point or is empty. Point: " + point + ", Cluster: " + cluster);
	        return 0.0; // No intra-cluster distance for single-point clusters
	    }

	    double sumDistance = 0.0;
	    for (String otherPoint : cluster) {
	    	if (!point.equals(otherPoint)) { // Exclude the point itself
		        sumDistance += calculateDistance(point, otherPoint, distanceMeasure );
	        }
	    }
	    return sumDistance / (cluster.size() - 1); // Exclude the point itself
	}
	
	//b(i) : Calculate the smallest average distance of point i to all points in other clusters.
	public static double smallestInterClusterDistance(String point, HashMap<String, ArrayList<String>> allCluster,
			ArrayList<String> ownCluster, String distanceMeasure) {
		
	    double smallestAverage = Double.MAX_VALUE;
    	for (String key : allCluster.keySet())
    	{
    		ArrayList<String> cluster = allCluster.get(key);
    		if(cluster.equals(ownCluster) || cluster.isEmpty()) continue; // Skip the same cluster contain the point
    		double sumDistance = 0.0;
    		for(String otherPoint : cluster) {
    			sumDistance += calculateDistance(point, otherPoint, distanceMeasure);
    		}
    		double averageDistance = sumDistance / cluster.size();
            if (averageDistance < smallestAverage) {
                smallestAverage = averageDistance;
    	        }
    	}
    	return smallestAverage;
	}
	
	//Calculate the Silhouette Coefficient for Each Point
	public static double silhouetteCoefficient(String point, ArrayList<String> ownCluster
			, HashMap<String, ArrayList<String>> allClusters, String distanceMeasure) {
		
	    double a = averageIntraClusterDistance(point, ownCluster, distanceMeasure);
	    double b = smallestInterClusterDistance(point, allClusters, ownCluster, distanceMeasure);
	    return (b - a) / Math.max(a, b);
	}
	
	//Calculate the SilhouetteCoefficient : Calculate the mean of the Silhouette Coefficients for all point
	public static double averageSilhouetteCoefficient(HashMap<String, ArrayList<String>> allCluster, String distanceMeasure) {
	    double sumSilhouette = 0.0;
	    int numPoints = 0;

	    for (String key : allCluster.keySet())
	    {
	    	ArrayList<String> cluster = allCluster.get(key);
	    	for (String point : cluster)
	    	{
	    		sumSilhouette += silhouetteCoefficient(point, cluster, allCluster, distanceMeasure);
	    		numPoints++;
	    	}
	    }
	    System.out.println("Sum " + sumSilhouette);
	    return sumSilhouette / numPoints;
	}
	    // Function to remove "index" and "id" attributes from centroid keys and cluster points
    public static HashMap<String, ArrayList<String>> removeIndexAndId(HashMap<String, ArrayList<String>> kmeanAssign) {
        HashMap<String, ArrayList<String>> cleanedKmeanAssign = new HashMap<>();

        // Iterate over the original kmeanAssign map
        for (Map.Entry<String, ArrayList<String>> entry : kmeanAssign.entrySet()) {
            // Clean the centroid by removing index and id
            String cleanedCentroid = removeIndexAndIdFromString(entry.getKey());

            // Clean the cluster points
            ArrayList<String> cleanedCluster = new ArrayList<>();
            for (String point : entry.getValue()) {
                cleanedCluster.add(removeIndexAndIdFromString(point));
            }

            // Add the cleaned centroid and cleaned cluster to the new map
            cleanedKmeanAssign.put(cleanedCentroid, cleanedCluster);
        }

        return cleanedKmeanAssign;
    }

    // Helper function to remove "index" and "id" from the string
    private static String removeIndexAndIdFromString(String input) {
        StringBuilder cleanedString = new StringBuilder();
        String[] attributes = input.split(",");

        for (String attribute : attributes) {
            String[] parts = attribute.split(":");
            if (parts.length == 2) {
                String attributeName = parts[0].trim();
                if (!attributeName.equalsIgnoreCase("index") && !attributeName.equalsIgnoreCase("id")) {
                    if (cleanedString.length() > 0) {
                        cleanedString.append(", ");
                    }
                    cleanedString.append(attribute.trim());
                }
            }
        }

        return cleanedString.toString();
    }


}