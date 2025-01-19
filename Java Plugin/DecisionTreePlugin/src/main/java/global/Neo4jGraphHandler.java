package global;

import static org.neo4j.driver.Values.parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import definition.EdgeList2;
import definition.NodeList2;

public class Neo4jGraphHandler {

	/**
	 * Drop any existing index on (label.id) and re-create it
	 */
	private static void dropAndCreateIndexOnId(Session session, Driver driver, String label, String identifier) {
	    String indexName = label + "_" + identifier + "_idx";
	    
	    // Drop the index if it exists
	    session.run("DROP INDEX " + indexName + " IF EXISTS");
	    
	    // Create a new index on the specified identifier
	    session.run("CREATE INDEX " + indexName + " IF NOT EXISTS FOR (n:" + label + ") ON (n." + identifier + ")");
	}
	
	public static String resolveDynamicIdentifier(Driver driver, String label) {
	    try (Session session = driver.session()) {
	        String cypherQuery = "MATCH (n:" + label + ") RETURN keys(n) AS keys LIMIT 1";
	        Result result = session.run(cypherQuery);

	        if (result.hasNext()) {
	            List<String> keys = result.next().get("keys").asList(Value::asString);
	            for (String key : Arrays.asList("id", "index", "Id", "ID", "Index","INDEX")) {
	                if (keys.contains(key)) {
	                    return key; // Return the first matching identifier
	                }
	            }
	        }
	    }
	    throw new RuntimeException("No valid identifier found for nodes with label: " + label);
	}


	public static Pair<ArrayList<NodeList2>, String> retrieveNodeList(final String nodeType, Driver driver) {
	    ArrayList<NodeList2> nodeList = new ArrayList<>();
	    String propertyKeys = "";

	    try (Session session = driver.session()) {
	        // Dynamically resolve the identifier for nodes of this type
	        String identifier = resolveDynamicIdentifier(driver, nodeType);

	        // Query to retrieve all node properties
	        String cypherQuery = "MATCH (n:" + nodeType + ") RETURN properties(n) AS nodeProperties";
	        Result result = session.run(cypherQuery);

	        boolean isFirstNode = true; // Flag for the first node
	        while (result.hasNext()) {
	            Record record = result.next();
	            Map<String, Object> originalProperties = record.get("nodeProperties").asMap();

	            // Create a mutable copy of the properties map
	            Map<String, Object> nodeProperties = new HashMap<>(originalProperties);

	            // Extract the identifier value
	            if (!nodeProperties.containsKey(identifier)) {
	                throw new RuntimeException("Identifier '" + identifier + "' not found in node properties: " + nodeProperties + ". Possible identifiers are id, index, Id, ID, Index, INDEX as Integer.");
	            }
	            
	            Object identifierValue = nodeProperties.get(identifier);
	            float id = ((Number) identifierValue).floatValue();

	            // Collect property keys only once, for the first node
	            if (isFirstNode) {
	                propertyKeys = String.join(",", nodeProperties.keySet());
	                if (!propertyKeys.contains(identifier)) {
	                    propertyKeys += "," + identifier; // Add identifier to property keys
	                }
	                isFirstNode = false; // Reset the flag
	            }

	            // Add the identifier back to properties for future graph creation
	            nodeProperties.put(identifier, id);

	            // Create the NodeList2 object
	            NodeList2 nodeObject = new NodeList2(id, nodeProperties);
	            nodeList.add(nodeObject);
	        }
	    } catch (Neo4jException e) {
	        throw new RuntimeException("Error retrieving node data from Neo4j for label: " + nodeType + ", Error: " + e.getMessage());
	    }
	    return Pair.of(nodeList, propertyKeys);
	}

	
    /**
     * Retrieves the edge list from Neo4j for a specified node type.
     *
     * @param node_label The label of the nodes in the graph.
     * @param driver   The Neo4j Driver instance.
     * @return ArrayList of EdgeList2 representing the edges in the graph.
     */
    public static ArrayList<EdgeList2> retrieveEdgeListFromNeo4j(final String node_label, Driver driver) {
        ArrayList<EdgeList2> edge_list = new ArrayList<>();

        try (Session session = driver.session()) {
        	String cypher_query = "MATCH (n:" + node_label + ")-[r]->(m:" + node_label + ") WHERE r.value IS NOT NULL RETURN n, m, r, n.id AS source, m.id AS target, r.value AS weight, id(r) AS index ORDER BY index ASC";
            Result result = session.run(cypher_query);

            while (result.hasNext()) {
                Record record = result.next();
//              Node sourceNode = record.get("n").asNode();
                Relationship relationship = record.get("r").asRelationship();
//              Node targetNode = record.get("m").asNode();

                int source = record.get("source").asInt();
                int target = record.get("target").asInt();
                double weight = record.get("weight").asDouble(); 
                long index = record.get("index").asLong();

                Map<String, Object> relationship_properties = extractPropertiesFromRelationship(relationship);

                EdgeList2 edge = new EdgeList2(source, target, weight, index, relationship_properties);
                edge_list.add(edge);
            }
        } catch (Neo4jException e) {
            throw new RuntimeException("Error retrieving edge data from Neo4j: " + e.getMessage());
        }
        return edge_list;
    }
    
    public static ArrayList<EdgeList2> retrieveEdgeList(final String node_label, Driver driver) {
        ArrayList<EdgeList2> edge_list = new ArrayList<>();

        // Dynamically determine the identifier (e.g., id, index)
        String identifier = resolveDynamicIdentifier(driver, node_label);

        try (Session session = driver.session()) {
            String cypher_query = "MATCH (n:" + node_label + ")-[r]->(m:" + node_label + ") " +
                                  "WHERE r.value IS NOT NULL " +
                                  "RETURN n." + identifier + " AS source, m." + identifier + " AS target, " +
                                  "r.value AS weight, id(r) AS edgeId";
            Result result = session.run(cypher_query);

            while (result.hasNext()) {
                Record record = result.next();

                // Use the dynamically resolved identifier to extract source and target
                Value sourceValue = record.get("source");
                Value targetValue = record.get("target");

                int source = sourceValue.type().name().equals("INTEGER") ? sourceValue.asInt() : (int) sourceValue.asLong();
                int target = targetValue.type().name().equals("INTEGER") ? targetValue.asInt() : (int) targetValue.asLong();

                double weight = record.get("weight").asDouble();
                long edgeId = record.get("edgeId").asLong();

                EdgeList2 edge = new EdgeList2(source, target, weight, edgeId, null);
                edge_list.add(edge);
            }
        } catch (Neo4jException e) {
            throw new RuntimeException("Error retrieving edge data from Neo4j: " + e.getMessage());
        }
        return edge_list;
    }
    
//    public static Pair<ArrayList<NodeList2>, String> retrieveNodeListFromNeo4jSimilarityGraph(final String nodeType, Driver driver) {
//        ArrayList<NodeList2> nodeList = new ArrayList<>();
//        String propertyKeys = "";
//
//        try (Session session = driver.session()) {
//            String cypherQuery = "MATCH (n:" + nodeType + ") RETURN n, n.id AS index";
//            Result result = session.run(cypherQuery);
//            String index;
//            int count = 0;
//
//
//            while (result.hasNext()) {
//                Record record = result.next();
//                Node node = record.get("n").asNode();
//                index = String.valueOf(count);
//                Map<String, Object> nodeProperties = extractPropertiesFromNode(node);
//                NodeList2 nodeObject = new NodeList2(index, nodeProperties);
//                nodeList.add(nodeObject);
//                
//                if (count == 0) {
//                    propertyKeys = String.join(",", node.keys());
//                }
//                
//                count++;
//            }
//        } catch (Neo4jException e) {
//            throw new RuntimeException("Error retrieving node data from Neo4j for label: " + nodeType + ", Error: " + e.getMessage());
//        }
//        return Pair.of(nodeList, propertyKeys);
//    }

//    public static ArrayList<NodeList2> retrieveNodeListFromNeo4jSimilarityGraph2(final String nodeType, Driver driver) {
//        ArrayList<NodeList2> nodeList = new ArrayList<>();
//
//        try (Session session = driver.session()) {
//            String cypherQuery = "MATCH (n:" + nodeType + ") RETURN n, n.id AS index";
//            Result result = session.run(cypherQuery);
//            String index;
//            int count = 0;
//
//
//            while (result.hasNext()) {
//                Record record = result.next();
//                Node node = record.get("n").asNode();
//                index = String.valueOf(count);
//                Map<String, Object> nodeProperties = extractPropertiesFromNode(node);
//                NodeList2 nodeObject = new NodeList2(index, nodeProperties);
//                nodeList.add(nodeObject);
//                count++;
//            }
//        } catch (Neo4jException e) {
//            throw new RuntimeException("Error retrieving node data from Neo4j for label: " + nodeType + ", Error: " + e.getMessage());
//        }
//        return nodeList;
//    }
    
    

    /**
     * Retrieves the node list from Neo4j for a specified node type.
     *
     * @param node_label The label of the nodes in the graph.
     * @param driver   The Neo4j Driver instance.
     * @return ArrayList of NodeList2 representing the nodes in the graph.
     */
//    public static ArrayList<NodeList2> retrieveNodeListFromNeo4j(final String node_label, Driver driver) {
//        ArrayList<NodeList2> nodeList = new ArrayList<>();
//
//        try (Session session = driver.session()) {
//            String cypher_query = "MATCH (n:" + node_label + ") RETURN n, toString(n.id) AS index";
//            Result result = session.run(cypher_query);
//
//            while (result.hasNext()) {
//                Record record = result.next();
//                Node node = record.get("n").asNode();
//
//                String index = record.get("index").asString();
//
//                Map<String, Object> node_properties = extractPropertiesFromNode(node);
//
//                NodeList2 node_object = new NodeList2(index, node_properties);
//                nodeList.add(node_object);
//            }
//        } catch (Neo4jException e) {
//            throw new RuntimeException("Error retrieving node data from Neo4j for label: " + node_label + ", Error: " + e.getMessage());
//        }
//        return nodeList;
//    }
    
    public static void createNodeGraph(String graphType, String message, NodeList2 nodeDetail, Driver driver) {
        final float id = nodeDetail.getIndex();
        final Map<String, Object> properties = nodeDetail.getProperties();

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    String cypherQuery = "CREATE (:" + graphType + " {id: $id";
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        cypherQuery += ", " + entry.getKey() + ": $" + entry.getKey();
                    }
                    cypherQuery += "})";

                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("id", id);
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        parameters.put(entry.getKey(), entry.getValue());
                    }

                    Result result = tx.run(cypherQuery, parameters);
                    return message;
                }
            });
        }
    }
    
    public static void bulkCreateNodes(String graphType, List<NodeList2> nodes, Driver driver, String identifier) {
        // Prepare the list of node data
        List<Map<String, Object>> nodeData = new ArrayList<>();
        for (NodeList2 node : nodes) {
            // Include the identifier explicitly
            Map<String, Object> map = new HashMap<>(node.getProperties());
            map.put(identifier, node.getIndex()); // Ensure the identifier is included
            nodeData.add(map);
        }

        // Define the Cypher query using UNWIND for bulk insertion
        String cypher = "UNWIND $nodes AS node " +
                        "CREATE (n:" + graphType + ") " +
                        "SET n = node";

        // Execute the bulk insertion
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run(cypher, parameters("nodes", nodeData));
                return null;
            });
        } catch (Neo4jException e) {
            throw new RuntimeException("Error bulk creating nodes: " + e.getMessage());
        }
    }


    /**
     * Creates nodes in Neo4j for the transformed graph after Laplacian Eigen Transform.
     *
     * @param graph_type   The type of the transformed graph.
     * @param message     A message indicating the operation.
     * @param nodeDetail  Details of the node to be created.
     * @param X           The X matrix obtained from eigen decomposition.
     * @param driver      The Neo4j Driver instance.
     */
    
    public static void bulkCreateNodesWithEigen(String graphType, List<NodeList2> nodes, SimpleMatrix X, Driver driver, String identifier) {
        // Determine if the indices are 1-based or 0-based
        float minIndex = Integer.MAX_VALUE;
        for (NodeList2 node : nodes) {
            float currentIndex = node.getIndex();
            if (currentIndex < minIndex) {
                minIndex = currentIndex;
            }
        }

        boolean isOneBased = (minIndex == 1); // Check if the indices start from 1

        // Adjust index offset
        int indexOffset = isOneBased ? -1 : 0;

        // Validate matrix dimensions
        if (X.getNumRows() != nodes.size()) {
            throw new IllegalArgumentException("Mismatch between eigenvector matrix rows and node list size: " +
                "Eigenvector Rows = " + X.getNumRows() + ", Node List Size = " + nodes.size());
        }

        // Prepare the list of node data with eigenvector properties
        List<Map<String, Object>> nodeData = new ArrayList<>();
        for (NodeList2 node : nodes) {
            // Get the node index and properties
            float nodeIndex;
            try {
                nodeIndex = node.getIndex() + indexOffset;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid node index format for node: " + node.getIndex(), e);
            }

            // Validate node index
            if (nodeIndex < 0 || nodeIndex >= X.getNumRows()) {
                throw new IllegalArgumentException("Invalid node index: " + nodeIndex + 
                    ". Eigenvector matrix has rows = " + X.getNumRows());
            }

            Map<String, Object> properties = new HashMap<>(node.getProperties());
            int nodeIndexInt = (int) nodeIndex;
            // Add eigenvector properties dynamically
            for (int i = 0; i < X.getNumCols(); i++) {
                properties.put("eigenvector_" + i, X.get(nodeIndexInt, i));
            }
            properties.put(identifier, node.getIndex()); // Ensure the identifier is included

            // Add the modified properties to the node data list
            nodeData.add(properties);
        }
        
        // Define the Cypher query using UNWIND for bulk insertion
        String cypher = "UNWIND $nodes AS node " +
                        "CREATE (n:" + graphType + ") " +
                        "SET n = node";
        
        // Execute the bulk insertion
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run(cypher, parameters("nodes", nodeData));
                return null;
            });
        } catch (Neo4jException e) {
            throw new RuntimeException("Error bulk creating eigen nodes: " + e.getMessage());
        }
    }
    
    /**
     * Creates relationships in Neo4j for the transformed graph after Laplacian Eigen Transform.
     *
     * @param graph_type        The type of the transformed graph.
     * @param message          A message indicating the operation.
     * @param edge_list_detail   Details of the edge to be created.
     * @param driver           The Neo4j Driver instance.
     */
    public static void createRelationshipGraph(String graph_type, String message, EdgeList2 edge_list_detail, Driver driver) {
        final float source = edge_list_detail.getSource();
        final float target = edge_list_detail.getTarget();
//        double weightValue = (double) Math.round(edge_list_detail.getWeight() * 10000000d) / 10000000d;
        double weightValue = edge_list_detail.getWeight();
        
        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Void>() {
                @Override
                public Void execute(Transaction tx) {
                    String deleteQuery = "MATCH (n:" + graph_type + " {id: $source})-[r:`link`]->(m:" + graph_type + " {id: $target}) " + "DELETE r";
                    //tx.run(deleteQuery, parameters("source", source, "target", target));

                    Result result = tx.run(
                            "MATCH (n:" + graph_type + " {id: $source}), (m:" + graph_type + " {id: $target}) " +
                                    "CREATE (n)-[r:`link` {value: $weightValue}]->(m)",
                            parameters("source", source, "target", target, "weightValue", weightValue)
                    );
                    return null;
                }
            });
        } catch (Neo4jException e) {
            throw new RuntimeException("Error creating relationship in Neo4j: " + e.getMessage());
        }
    }
    
    public static void bulkCreateRelationshipsWithBatching(String label, List<EdgeList2> edges, Driver driver, String identifier) {
    	    final int BATCH_SIZE = 100000;
    	    int total = edges.size();

    	    for (int i = 0; i < total; i += BATCH_SIZE) {
    	        int end = Math.min(i + BATCH_SIZE, total);
    	        List<EdgeList2> batch = edges.subList(i, end);

    	        // Prepare relationship data
    	        List<Map<String, Object>> relationshipData = new ArrayList<>();
    	        for (EdgeList2 edge : batch) {
    	            if (edge.getWeight() == 0.0) {
    	                continue;
    	            }
    	            Map<String, Object> map = new HashMap<>();
    	            map.put("source", edge.getSource());
    	            map.put("target", edge.getTarget());
    	            map.put("distance", edge.getWeight());
    	            relationshipData.add(map);
    	        }

    	        if (relationshipData.isEmpty()) {
    	            continue;
    	        }

    	        // Use provided identifier for MATCH query
    	        String cypher = "UNWIND $relationships AS rel " +
    	                        "MATCH (n:" + label + " {" + identifier + ": rel.source}), " +
    	                        "(m:" + label + " {" + identifier + ": rel.target}) " +
    	                        "CREATE (n)-[:`link` {value: rel.distance}]->(m)";

    	        try (Session session = driver.session()) {
    	            session.writeTransaction(tx -> {
    	                tx.run(cypher, parameters("relationships", relationshipData));
    	                return null;
    	            });
    	        } catch (Neo4jException e) {
    	            throw new RuntimeException("Error bulk creating relationships: " + e.getMessage());
    	        }
    	    }
    	}

    /**
     * Creates relationships in Neo4j for the transformed graph after Laplacian Eigen Transform.
     *
     * @param name        Node Label name.
     * @param driver      The Neo4j Driver instance.
     */
    public static void deleteExistingNodeLabels(String name, Driver driver) {
        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Void>() {
                @Override
                public Void execute(Transaction tx) {
                    String deleteQuery = "MATCH (n:" + name + ") DETACH DELETE n";
                    tx.run(deleteQuery);
                    return null;
                }
            });
        } catch (Neo4jException e) {
            throw new RuntimeException("Error deleting Extisting Node Labels in Neo4j: " + e.getMessage());
        }
    }

    public static void bulkDeleteNodesWithBatching(String label, Driver driver, String identifier) {
        final int BATCH_SIZE = 500; // Define batch size for deletions

        try (Session session = driver.session()) {
            List<Integer> nodeIds = new ArrayList<>();

            // Step 1: Retrieve all node IDs with the label
            String retrieveIdsCypher = "MATCH (n:" + label + ") RETURN n." + identifier + " AS id";
            session.readTransaction(tx -> {
                Result result = tx.run(retrieveIdsCypher);
                while (result.hasNext()) {
                    Record record = result.next();
                    int idValue = record.get("id").asInt();
                    nodeIds.add(idValue);
                }
                return null;
            });

            // Step 2: Delete nodes in batches
            for (int i = 0; i < nodeIds.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, nodeIds.size());
                List<Integer> batch = nodeIds.subList(i, end);

                String deleteCypher = "UNWIND $ids AS id " +
                                      "MATCH (n:" + label + ") " +
                                      "WHERE n." + identifier + " = id " +
                                      "DETACH DELETE n";

                session.writeTransaction(tx -> {
                    tx.run(deleteCypher, parameters("ids", batch));
                    return null;
                });
            }

            // Step 3: Drop and re-create the index
            dropAndCreateIndexOnId(session, driver, label, identifier);

        } catch (Neo4jException e) {
            throw new RuntimeException("Error bulk deleting nodes in Neo4j: " + e.getMessage(), e);
        }
    }

//    private static Map<String, Object> extractPropertiesFromNode(Node node) {
//        Map<String, Object> properties = new HashMap<>();
//
//        for (String key : node.keys()) {
//            Value value = node.get(key);
//            properties.put(key, convertToJavaType(value));
//        }
//
//        return properties;
//    }
    
    private static Map<String, Object> extractPropertiesFromRelationship(Relationship relationship) {
        Map<String, Object> properties = new HashMap<>();

        for (String key : relationship.keys()) {
            Value value = relationship.get(key);
            properties.put(key, convertToJavaType(value));
        }

        return properties;
    }

    private static Object convertToJavaType(Value value) {
        if (value.type().name().equals("String")) {
            return value.asString();
        } else if (value.type().name().equals("Integer")) {
            return value.asInt();
        } else if (value.type().name().equals("Float")) {
            return value.asDouble();
        } else if (value.type().name().equals("Boolean")) {
            return value.asBoolean();
        } else {
            return value.asObject();
        }
    }

}
