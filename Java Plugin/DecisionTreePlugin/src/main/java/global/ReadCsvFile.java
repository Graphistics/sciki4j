package global;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import definition.EdgeList;
import definition.NodeList2;

public class ReadCsvFile {
    public String dataPath;

    public ReadCsvFile(String dataPath){
        this.dataPath = dataPath;
    }


    public static ArrayList<NodeList2> retrieveNodeListFromCSV(final String csvFilePath) throws Exception {
        ArrayList<NodeList2> nodeList = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(new FileReader(csvFilePath))) {
            List<String[]> csvData = csvReader.readAll();

            // Assuming the first row contains column headers
            String[] headers = csvData.get(0);

            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);

                // Assuming 'id' is one of the columns
                float index = getIdOrIndexColumnIndex(headers);

                Map<String, Object> nodeProperties = extractPropertiesFromCSVRow(headers, row);

                NodeList2 nodeObject = new NodeList2(index, nodeProperties);
                nodeList.add(nodeObject);
            }
        } catch (IOException | CsvException e) {
            throw new RuntimeException("Error reading CSV file: " + e.getMessage());
        }

        return nodeList;
    }

    private static Map<String, Object> extractPropertiesFromCSVRow(String[] headers, String[] row) {
        Map<String, Object> nodeProperties = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            if (!headers[i].equalsIgnoreCase("id") && !headers[i].equalsIgnoreCase("index")) {
                // Exclude 'id' or 'index' column
                nodeProperties.put(headers[i], row[i]);
            }
        }

        return nodeProperties;
    }

    private static int getIdOrIndexColumnIndex(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase("id") || headers[i].equalsIgnoreCase("index")) {
                return i;
            }
        }
        throw new RuntimeException("ID or Index column not found in CSV file.");
    }

    
    
    public static Double[][] euclidianDistance(ArrayList<ArrayList<String>> TestDataArrayList){


        double[][] doubleList = convertToDoubleArray(TestDataArrayList);

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

    private static double euclideanDistance(double[] point1, double[] point2) {
        double sum = 0.0;
        for (int i = 0; i < point1.length; i++) {
            double diff = point1[i] - point2[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }
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
    public static Double[] calculateKNN(Double[][] pdist) {
        Double[] sigmas = new Double[pdist.length];

        for (int i = 0; i < pdist.length; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances);
            sigmas[i] = sortedDistances[1];
        }

        return sigmas;
    }
    public static Double[] calculateLocalSigmas(Double[][] pdist) {
        Double[] sigmas = new Double[pdist.length];

        for (int i = 0; i < pdist.length; i++) {
            Double[] sortedDistances = Arrays.copyOf(pdist[i], pdist[i].length);
            Arrays.sort(sortedDistances, Collections.reverseOrder());
            sigmas[i] = sortedDistances[2];
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
                adj[i][j] = Math.exp( (-1* Math.pow(dist_[i][j], 2)) / ((sigmas[i] * sigmas[j])) );
                //adj[j][i] = Math.exp( (-1 * Math.pow(dist_[i][j], 2)) / ((sigmas[i] * sigmas[j])) );
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

    public static ArrayList<String> getNodeList(ArrayList<ArrayList<String>> TestDataArrayList) {
        ArrayList<String> nodeList = new ArrayList<>();
        // add all the nodes to the nodeList all entries
        for (int i = 0; i < TestDataArrayList.size(); i++) {
            nodeList.add(i + "");
        }
        return nodeList;
    }
    public static Double [][] calculateEpsilonNeighbourhoodGraph (Double[][] dist_,Double epsilon){
        Double[][] adj = new Double[5][5];
        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (dist_[i][j] <= epsilon){
                    adj[i][j] = 1.0;
                }
                else {
                    adj[i][j] = 0.0;
                }
            }
        }
        return adj;
    }
    public static Double[][] calculateKNNGraph(Double[][] dist_,Double [] knn){

        Double[][] adj = new Double[dist_.length][dist_[0].length];

        for (int i = 0; i < dist_.length; i++) {
            for (int j = 0; j < dist_[i].length; j++) {
                if (i == j) {
                    adj[i][j] = 0.0;
                    continue;
                }
                if (dist_[i][j] == knn[i]){
                    adj[i][j] = 1.0;
                }
                else {
                    adj[i][j] = 0.0;
                }
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
        
//      String[] HEADERS = { "points","x_coordinate","y_coordinate","class"};
        
            public static ArrayList<String> readCSVHeader(String dataPath) throws IOException {
                Reader in = new FileReader(dataPath);
        
                CSVFormat csvFormat = CSVFormat.DEFAULT.builder().build();
                Iterable<CSVRecord> records = csvFormat.parse(in);
                ArrayList<String> headers = new ArrayList<>();
                for (CSVRecord record : records) {
                    for (int i = 0; i < record.size(); i++) {
                        headers.add(record.get(i));
                    }
                    break;
                }
                return headers;
            }
            public static ArrayList<String> readCSVFirstLine(String dataPath) throws IOException {
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
        
            public ArrayList<ArrayList<String>> readCsvFileNew(String dataPath,Boolean indexColumn) throws IOException {
                Reader in = new FileReader(dataPath);
                ArrayList<String> header = readCSVHeader(dataPath);
                CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header.toString()).setSkipHeaderRecord(true).build();
                Iterable<CSVRecord> records = csvFormat.parse(in);
        
                ArrayList<ArrayList<String>>  csvFilerow =  new ArrayList<ArrayList<String>>();
        
                // read csv file without header
                for (CSVRecord record : records) {
                    ArrayList<String> TestDataArrayList = new ArrayList<>();
                    for(int i = 0; i < header.size(); i++){
                        if(indexColumn && i==0){
                            continue;
                        }
                        if(record.get(i).matches(".*[a-zA-Z].*")){
                            continue;
                        }
                        TestDataArrayList.add(record.get(i));
                    }
                    csvFilerow.add(TestDataArrayList);
                }
        
                return csvFilerow;
            }
        //
        //
        //
        //
//            public ArrayList<TestData> readCsvFile(String dataPath) throws IOException {
//                ArrayList<TestData> TestDataArrayList = new ArrayList<>();
//                Reader in = new FileReader(dataPath);
        //
//                CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
//                        .setHeader(HEADERS)
//                        .setSkipHeaderRecord(true)
//                        .build();
        //
        //
//                Iterable<CSVRecord> records = csvFormat.parse(in);
        //
//                for (CSVRecord record : records) {
//                    String points = record.get("points");
//                    String x_coordinate = record.get("x_coordinate");
//                    String y_coordinate = record.get("y_coordinate");
//                    String Class = record.get("class");
//                    TestData testData = new TestData(Double.parseDouble(x_coordinate),Double.parseDouble(y_coordinate),Double.parseDouble(Class),points);
//                    TestDataArrayList.add(testData);
//                }
//                return TestDataArrayList;
//            }
        //    
//            public static List<NodeList2> loadCsvConnector(String dataPath, String Name, ArrayList<String> arrayListHeaders, ArrayList<String> arrayListFirst, Driver driver) throws Exception {
//                String properties = getHeadersList(arrayListHeaders, arrayListFirst);
//                String fileName = Name.substring(0, Name.indexOf("."));
//                List<NodeList2> nodeList = new ArrayList<>();
        //
//                try (Session session = driver.session()) {
//                    session.writeTransaction(new TransactionWork<Void>() {
//                        @Override
//                        public Void execute(Transaction tx) {
//                            Result result = tx.run("LOAD CSV WITH HEADERS FROM 'file:///" + Name + "' AS row " +
//                                    "MERGE (n:" + fileName + "{" + properties + "})");
        //
//                            // Retrieve the created nodes and add them to the nodeList
//                            Result nodeResult = tx.run("MATCH (n:" + fileName + ") RETURN n");
//                            while (nodeResult.hasNext()) {
//                                nodeList.add(convertToNodeList2(nodeResult.next().get("n")));
//                            }
        //
//                            return null;
//                        }
//                    });
//                }
        //
//                return nodeList;
//            }
        //    
//        	private static String getHeadersList(ArrayList<String> arrayListHeaders,ArrayList<String> arrayListFirst) {
        //
//        		StringBuilder stringBuilder = new StringBuilder();
//        		Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
        //
        //
//        		for (int i = 0; i < arrayListHeaders.size(); i++) {
        //
//        			if(i == arrayListHeaders.size()-1) {
        //
//        				if (pattern.matcher(arrayListFirst.get(i)).matches()) {
//        					stringBuilder.append(arrayListHeaders.get(i) + ": toFloat(row." + arrayListHeaders.get(i) + ")");
//        				} else
//        					stringBuilder.append(arrayListHeaders.get(i) + ": row." + arrayListHeaders.get(i));
//        			}else {
//        				if (pattern.matcher(arrayListFirst.get(i)).matches()) {
//        					stringBuilder.append(arrayListHeaders.get(i) + ": toFloat(row." + arrayListHeaders.get(i) + "),");
//        				} else
//        					stringBuilder.append(arrayListHeaders.get(i) + ": row." + arrayListHeaders.get(i) + ",");
//        			}
        //
        //
//        		}
        //
//        		return stringBuilder.toString();
//        	}

}