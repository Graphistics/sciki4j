package graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import definition.EdgeList;
import definition.EdgeList2;
import definition.NodeList2;

public class GraphTransform {
    public String dataPath;

//    public static Double[][] euclidianDistance(ArrayList<ArrayList<String>> TestDataArrayList){
//
//
//        double[][] doubleList = convertToDoubleArray(TestDataArrayList);
//
//        Double[][] distanceMatrix = new Double[doubleList.length][doubleList.length];
//
//        for (int i = 0; i < doubleList.length; i++) {
//            for (int j = 0; j < doubleList.length; j++) {
//                if (i == j) {
//                    distanceMatrix[i][j] = 0.0; // Distance to itself is 0
//                } else {
//                    distanceMatrix[i][j] = euclideanDistance(doubleList[i], doubleList[j]);
//                }
//            }
//        }
//
//
//        return distanceMatrix;
//
//    }

//    private static double euclideanDistance(double[] point1, double[] point2) {
//        double sum = 0.0;
//        for (int i = 0; i < point1.length; i++) {
//            double diff = point1[i] - point2[i];
//            sum += diff * diff;
//        }
//
//        return Math.sqrt(sum);
//    }
    
    public static double[][] convertToDoubleArray(ArrayList<ArrayList<String>> arrayList) {
        int numRows = arrayList.size();
        int numCols = arrayList.get(0).size(); // Assuming all inner lists have the same size

        double[][] doubleArray = new double[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            ArrayList<String> row = arrayList.get(i);
            for (int j = 0; j < numCols; j++) {
                doubleArray[i][j] = Double.parseDouble(row.get(j));
            }
        }

        return doubleArray;
    }
    
//    public static Double[][] euclideanDistance(List<NodeList2> nodeList) {
//
//    	int size = nodeList.size();
//        Double[][] distanceMatrix = new Double[size][size];
//
//        for (int i = 0; i < size; i++) {
//            for (int j = 0; j < size; j++) {
//                double distance = calculateEuclideanDistance(nodeList.get(i), nodeList.get(j));
//                distanceMatrix[i][j] = distance;
//            }
//        }
//        return distanceMatrix;
//    }
//
//    private static double calculateEuclideanDistance(NodeList2 node1, NodeList2 node2) {
//        Map<String, Object> properties1 = node1.getProperties();
//        Map<String, Object> properties2 = node2.getProperties();
//
//        double sum = 0.0;
//
//        for (String propertyKey : properties1.keySet()) {
//            Object value1 = properties1.get(propertyKey);
//            Object value2 = properties2.get(propertyKey);
//
//            if (value1 instanceof Number && value2 instanceof Number) {
//                double diff = ((Number) value1).doubleValue() - ((Number) value2).doubleValue();
//                sum += Math.pow(diff, 2);
//            }
//        }
//        return Math.sqrt(sum);
//    }
    
//    public static Double[][] calculateAdjMatrix(Double[][] distanceMatrix, String method, Double epsilon) {
//        try {
//        	Double[][] adj_mat;
//        	Double[] knn;
//        	Double[] sigmas;
//
//            switch (method) {
//                case "FULLY_CONNECTED":
//                	sigmas = calculateLocalSigmas(distanceMatrix);
//                    adj_mat = calculateAdjacencyMatrix(distanceMatrix, sigmas);
//                    break;
//                case "EPSILON":
//                	adj_mat = calculateEpsilonNeighbourhoodGraph(distanceMatrix,epsilon);;
//                    break;
//                case "KNN":
//    				knn = calculateKNN(distanceMatrix);
//    				adj_mat = calculateKNNGraph(distanceMatrix,knn);
//                    break;
//                case "MUTUAL_KNN":
//                	knn = calculateKNN(distanceMatrix);
//    				adj_mat = calculateMutualKNNGraph(distanceMatrix,knn);
//                    break;
//                default:
//                    throw new IllegalArgumentException("Invalid graph tranfsorm algorithm choice.");
//            }
//
//            return adj_mat;
//        } catch (Exception e) {
//            throw new RuntimeException("Error calculating graph transform: " + e.getMessage());
//        }
//    }

    public static Double[] calculateKNN(Double[][] pdist) {
        int size = pdist.length;
        Double[] sigmas = new Double[size];

        for (int i = 0; i < size; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances);
            sigmas[i] = sortedDistances[1];
        }

        return sigmas;
    }

    public static Double[] calculateLocalSigmas(Double[][] pdist) {
        int size = pdist.length;
        Double[] sigmas = new Double[size];

        for (int i = 0; i < size; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances, Collections.reverseOrder());
            sigmas[i] = sortedDistances[2];
        }

        return sigmas;
    }

    public static Double[][] calculateAdjacencyMatrix(Double[][] dist_, Double[] sigmas) {
        int size = dist_.length;
        Double[][] adj = new Double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                adj[i][j] = Math.exp((-1 * Math.pow(dist_[i][j], 2)) / ((sigmas[i] * sigmas[j])));
            }
        }

        return adj;
    }

    public static Double[][] calculateEpsilonNeighbourhoodGraph(Double[][] dist_, Double epsilon) {
        int size = dist_.length;
        Double[][] adj = new Double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                adj[i][j] = (dist_[i][j] <= epsilon) ? 1.0 : 0.0;
            }
        }
        return adj;
    }

    public static Double[][] calculateKNNGraph(Double[][] dist_, Double[] knn) {
        int size = dist_.length;
        Double[][] adj = new Double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                adj[i][j] = (dist_[i][j] == knn[i]) ? 1.0 : 0.0;
            }
        }

        return adj;
    }

    public static Double[][] calculateMutualKNNGraph(Double[][] dist_,Double [] knn){

        Double[][] adj = new Double[dist_.length][dist_[0].length];
        //calculateMutualKNNGraph
        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (Objects.equals(dist_[i][j], knn[i]) && dist_[j][i] == knn[j]){
                    adj[i][j] = 1.0;
                }
                else {
                    adj[i][j] = 0.0;
                }
            }
        }

        return adj;
    }
    
    
    public static ArrayList<EdgeList> calulateEdgeList(Double[][] adj_mat) {
        ArrayList<EdgeList> edgeList = new ArrayList<>();

        for (int i = 0; i < adj_mat.length; i++) {
            for (int j = i + 1; j < adj_mat[i].length; j++) {
                edgeList.add(new EdgeList(i, j, adj_mat[i][j],i));
            }
        }
        return edgeList;
    }

//    public static ArrayList<String> getNodeList(ArrayList<ArrayList<String>> TestDataArrayList) {
//        ArrayList<String> nodeList = new ArrayList<>();
//        // add all the nodes to the nodeList all entries
//        for (int i = 0; i < TestDataArrayList.size(); i++) {
//            nodeList.add(i + "");
//        }
//        return nodeList;
//    }

//    public static List<NodeList2> getNodeList2(ArrayList<ArrayList<String>> TestDataArrayList) {
//        List<NodeList2> nodeList = new LinkedList<>();
//        // add all the nodes to the nodeList with entries and properties
//        for (int i = 0; i < TestDataArrayList.size(); i++) {
//            Map<String, Object> properties = new HashMap<>();
//            // Populate properties map as needed
//            // For example, you can use TestDataArrayList.get(i) to get the properties for each node
//
//            nodeList.add(new NodeList2(i + "", properties));
//        }
//        return nodeList;
//    }

    
    
    public static ArrayList<EdgeList2> calculateEdgeList(List<NodeList2> nodePropertiesList, Double[][] adj_mat) {
        ArrayList<EdgeList2> edgeList = new ArrayList<>();
        long edgeIndex = 0;
        
        for (int i = 0; i < adj_mat.length; i++) {
            for (int j = i + 1; j < adj_mat[i].length; j++) {
                float sourceId = nodePropertiesList.get(i).getIndex();
                float targetId = nodePropertiesList.get(j).getIndex();
                edgeList.add(new EdgeList2(sourceId, targetId, adj_mat[i][j], edgeIndex++, null));
            }
        }
        return edgeList;
    }

}