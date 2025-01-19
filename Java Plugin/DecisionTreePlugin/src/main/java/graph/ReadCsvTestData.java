package graph;

import definition.NodeList2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import definition.EdgeList;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

public class ReadCsvTestData {
    public String dataPath;

    public ReadCsvTestData(String dataPath) {
        this.dataPath = dataPath;
    }

    String[] HEADERS = {"points", "x_coordinate", "y_coordinate", "class"};

    public ArrayList<String> readCSVHeader(String dataPath) throws IOException {
        Reader in = new FileReader(dataPath);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().build();
        Iterable<CSVRecord> records = csvFormat.parse(in);
        ArrayList<String> headers = new ArrayList<>();
        Pattern pattern = Pattern.compile(".*\\..*");
        for (CSVRecord record : records) {
            for (int i = 0; i < record.size(); i++) {

                if (pattern.matcher(record.get(i)).matches()) {
                    headers.add(record.get(i).replace(".", "_"));
                } else {
                    headers.add(record.get(i));
                }

            }
            break;
        }
        return headers;
    }

    public ArrayList<String> readCSVFirstLine(String dataPath) throws IOException {
        Reader in = new FileReader(dataPath);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).build();
        CSVParser records = csvFormat.parse(in);
        ArrayList<String> First = new ArrayList<>();
        records.iterator().next();
        for (CSVRecord record : records) {
            for (int i = 0; i < record.size(); i++) {
                First.add(record.get(i));
            }
            break;
        }
        return First;
    }

    public ArrayList<NodeList2> readCsvFileToMap(String dataPath) throws IOException {
        Reader in = new FileReader(dataPath);
        ArrayList<String> header = readCSVHeader(dataPath);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header.toString()).setSkipHeaderRecord(true).build();
        Iterable<CSVRecord> records = csvFormat.parse(in);

        ArrayList<NodeList2> csvFileRow = new ArrayList<>();
        int count = -1;

        // read csv file without header
        for (CSVRecord record : records) {
            Map<String, Object> TestDataArrayList = new HashMap<>();

            for (int i = 0; i < header.size(); i++) {
//                if (header.get(i).equals(index)) { // indexColumn && i == 0
//                    continue;
//                }
//                if (header.get(i).equals(classVariable)) { // indexColumn && i == 0
//                    continue;
//                }
//                if (record.get(i).matches(".*[a-zA-Z].*")) {
//                    continue;
//                }
                if (record.get(i).matches(".*[a-zA-Z].*")) {
                    TestDataArrayList.put(header.get(i), record.get(i));
                }
                else {
                    double value = Double.parseDouble(record.get(i));
                    TestDataArrayList.put(header.get(i), value);
                }
            }
            count++;
            csvFileRow.add(new NodeList2(count, TestDataArrayList));
        }

        return csvFileRow;
    }

    public ArrayList<ArrayList<String>> readCsvFileNew(String dataPath, Boolean indexColumn) throws IOException {
        Reader in = new FileReader(dataPath);
        ArrayList<String> header = readCSVHeader(dataPath);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header.toString()).setSkipHeaderRecord(true).build();
        Iterable<CSVRecord> records = csvFormat.parse(in);

        ArrayList<ArrayList<String>> csvFilerow = new ArrayList<ArrayList<String>>();

        // read csv file without header
        for (CSVRecord record : records) {
            ArrayList<String> TestDataArrayList = new ArrayList<>();
            for (int i = 0; i < header.size(); i++) {
                if (indexColumn && i == 0) {
                    continue;
                }
                if (record.get(i).matches(".*[a-zA-Z].*")) {
                    continue;
                }
                TestDataArrayList.add(record.get(i));
            }
            csvFilerow.add(TestDataArrayList);
        }

        return csvFilerow;
    }

    public ArrayList<ArrayList<String>> readCsvFileNewString(String dataPath, Boolean indexColumn, List<String> classTypes) throws IOException {
        Reader in = new FileReader(dataPath);
        ArrayList<String> header = readCSVHeader(dataPath);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header.toString()).setSkipHeaderRecord(true).build();
        Iterable<CSVRecord> records = csvFormat.parse(in);

        ArrayList<ArrayList<String>> csvFilerow = new ArrayList<ArrayList<String>>();

        // read csv file without header
        for (CSVRecord record : records) {
            ArrayList<String> TestDataArrayList = new ArrayList<>();
            for (int i = 0; i < header.size(); i++) {
                if (indexColumn && i == 0) {
                    continue;
                }
                if (i == header.size() - 1) {

                }
                if (record.get(i).matches(".*[a-zA-Z].*")) {
                    continue;
                }
                TestDataArrayList.add(record.get(i));
            }
            csvFilerow.add(TestDataArrayList);
        }

        return csvFilerow;
    }


    public ArrayList<TestData> readCsvFile(String dataPath) throws IOException {
        ArrayList<TestData> TestDataArrayList = new ArrayList<>();
        Reader in = new FileReader(dataPath);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build();


        Iterable<CSVRecord> records = csvFormat.parse(in);

        for (CSVRecord record : records) {
            String points = record.get("points");
            String x_coordinate = record.get("x_coordinate");
            String y_coordinate = record.get("y_coordinate");
            String Class = record.get("class");
            TestData testData = new TestData(Double.parseDouble(x_coordinate), Double.parseDouble(y_coordinate), Double.parseDouble(Class), points);
            TestDataArrayList.add(testData);
        }
        return TestDataArrayList;
    }

    public static Double[][] euclidianDistance(ArrayList<ArrayList<String>> TestDataArrayList) {


        Double[][] doubleList = convertToDoubleArray(TestDataArrayList);

        Double[][] distanceMatrix = new Double[doubleList.length][doubleList.length];

        for (int i = 0; i < doubleList.length; i++) {
            for (int j = 0; j < doubleList.length; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0.0; // Distance to itself is 0
                } else {
                    distanceMatrix[i][j] = euclideanDistance(doubleList[i], doubleList[j]);
                }
            }
        }

        return distanceMatrix;

    }

    private static double euclideanDistance(Double[] point1, Double[] point2) {
        double sum = 0.0;
        for (int i = 0; i < point1.length; i++) {
            double diff = point1[i] - point2[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }

    public static Double[][] convertToDoubleArray(ArrayList<ArrayList<String>> arrayList) {
        int numRows = arrayList.size();
        int numCols = arrayList.get(0).size(); // Assuming all inner lists have the same size

        Double[][] doubleArray = new Double[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            ArrayList<String> row = arrayList.get(i);
            for (int j = 0; j < numCols; j++) {
                doubleArray[i][j] = Double.parseDouble(row.get(j));
            }
        }

        return doubleArray;
    }

    public static Double[][] calculateKNN(Double[][] pdist, String knn_neighbour) {
//        Double[] sigmas = new Double[pdist.length];
        Double[][] sigmas = new Double[pdist.length][Integer.parseInt(knn_neighbour)];

        for (int i = 0; i < pdist.length; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances);
//            sigmas[i] = sortedDistances[Integer.parseInt(knn_neighbour)];
            sigmas[i] = Arrays.copyOf(sortedDistances, Integer.parseInt(knn_neighbour));
        }

        return sigmas;
    }
    
    public static int[][] calculateKNNIndices(Double[][] dist, String k) {
        int dimension = dist.length;
        int[][] knnIndices = new int[dimension][Integer.parseInt(k)];

        for (int i = 0; i < dimension; i++) {
            Integer[] sortedIndices = new Integer[dimension];
            for (int j = 0; j < dimension; j++) {
                sortedIndices[j] = j;
            }
            int node = i;

            Arrays.sort(sortedIndices, Comparator.comparingDouble(a -> dist[node][a]));

            for (int j = 0; j < Integer.parseInt(k); j++) {
                knnIndices[i][j] = sortedIndices[j + 1];
            }
        }

        return knnIndices;
    }

    public static Double[] calculateLocalSigmas(Double[][] pdist, String sigma) {
        Double[] sigmas = new Double[pdist.length];


        for (int i = 0; i < pdist.length; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances);
            sigmas[i] = sortedDistances[Integer.parseInt(sigma)];
        }
        return sigmas;
    }

    public static Double[][] calculateAdjacencyMatrix(Double[][] dist_, Double[] sigmas) {
        Double[][] adj = new Double[dist_.length][dist_.length];

        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                //adj[i][j] = Math.exp( (-Math.pow(dist_[i][j], 2)) / ((2 * Math.pow(sigmas[i], 2))) );
                adj[i][j] = Math.exp((-1 * Math.pow(dist_[i][j], 2)) / ((sigmas[i] * sigmas[j])));
                //adj[j][i] = Math.exp( (-1 * Math.pow(dist_[i][j], 2)) / ((sigmas[i] * sigmas[j])) );
            }
        }

        return adj;
    }

    public static ArrayList<EdgeList> calulateEdgeList(Double[][] adj_mat) {
        ArrayList<EdgeList> edgeList = new ArrayList<>();

        for (int i = 0; i < adj_mat.length; i++) {
            for (int j = i + 1; j < adj_mat[i].length; j++) {
                edgeList.add(new EdgeList(i, j, adj_mat[i][j], i));
            }
        }
        return edgeList;
    }

    public static ArrayList<String> getNodeList(ArrayList<ArrayList<String>> TestDataArrayList) {
        ArrayList<String> nodeList = new ArrayList<>();
        // add all the nodes to the nodeList all entries
        for (int i = 0; i < TestDataArrayList.size(); i++) {
            nodeList.add(String.valueOf(TestDataArrayList.get(i)));
        }
        return nodeList;
    }

    public static Double[][] calculateEpsilonNeighbourhoodGraph(Double[][] dist_, Double epsilon) {
        Double[][] adj = new Double[dist_.length][dist_.length];
        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (dist_[i][j] < epsilon) {
                    adj[i][j] = 1.0;
                    adj[j][i] = 1.0;
                } else {
                    adj[i][j] = 0.0;
                    adj[j][i] = 0.0;
                }
            }
        }
        return adj;
    }

    public static Double[][] calculateKNNGraph(Double[][] dist_, Double[][] knn) {

        Double[][] adj = new Double[dist_.length][dist_[0].length];

        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (Arrays.asList(knn[i]).contains(dist_[i][j])) {
                    adj[i][j] = 1.0;
                } else {
                    adj[i][j] = 0.0;
                }
            }
        }

        return adj;
    }
    
    public static Double[][] calculateKNNGraphWithIndices(Double[][] dist, int[][] knnIndices) {
        int dimension = dist.length;
        Double[][] adj = new Double[dimension][dimension];

        for (int i = 0; i < dimension; i++) {
            Arrays.fill(adj[i], 0.0);
            for (int neighbor : knnIndices[i]) {
                adj[i][neighbor] = 1.0;
            }
        }

        return adj;
    }

    public static Double[][] calculateMutualKNNGraph(Double[][] dist_, Double[][] knn) {

        Double[][] adj = new Double[dist_.length][dist_[0].length];
        //calculateMutualKNNGraph
        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (Arrays.asList(knn[i]).contains(dist_[i][j]) && Arrays.asList(knn[j]).contains(dist_[i][j])) {
                    adj[i][j] = 1.0;
                    adj[j][i] = 1.0;
                } else {
                    adj[i][j] = 0.0;
                }
            }
        }


        return adj;
    }


    public static void main(String[] args) throws IOException {

        String dataPath = "D:/de/MASTER_THESIS/Decision-Tree-Neo4j/Java Plugin/DecisionTreePlugin/src/main/resources/test.csv";
        String dataPath1 = "D:/de/MASTER_THESIS/SimKit/simkit/dataset_1_iris_numeric/Iris_Mythica_Data_Set.csv";

        ReadCsvTestData readCsvTestData = new ReadCsvTestData(dataPath);
        ArrayList<NodeList2> arrayLists = readCsvTestData.readCsvFileToMap(dataPath);
//        Double[][] dist_array = euclidianDistance(arrayLists);
//        Double[] sigmas = calculateLocalSigmas(dist_array);
//        Double[][] adj_mat = calculateAdjacencyMatrix(dist_array,sigmas);
//        Double[][] adj_mat_eps = calculateEpsilonNeighbourhoodGraph(dist_array,5.0);


    }

}
