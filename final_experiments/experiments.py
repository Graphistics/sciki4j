import os
import subprocess
import sys

# Ensure required packages are installed
required_packages = ["neo4j", "pandas", "psutil"]
for package in required_packages:
    try:
        __import__(package)
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", package])

from neo4j import GraphDatabase
import pandas as pd
import psutil
import time

# Neo4j connection details
NEO4J_URI = "bolt://localhost:7688"
NEO4J_USER = "neo4j"
NEO4J_PASSWORD = "123412345"

# Initialize Neo4j driver
driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

def delete_all_nodes(driver, batch_size=1000):
    """Delete all nodes and relationships in the Neo4j database in batches."""
    try:
        with driver.session() as session:
            while True:
                result = session.run(f"MATCH (n) WITH n LIMIT {batch_size} DETACH DELETE n RETURN count(n) AS deleted_count")
                deleted_count = result.single()["deleted_count"]
                if deleted_count == 0:
                    break
    except Exception as e:
        print("Error during node deletion:", e)

def delete_all_indexes(driver, batch_size=5):
    """Delete all indexes in the Neo4j database in batches."""
    try:
        with driver.session() as session:
            while True:
                indexes = session.run("CALL db.indexes()")
                index_names = [index["name"] for index in indexes]
                if not index_names:
                    break
                for index_name in index_names[:batch_size]:
                    session.run(f"DROP INDEX {index_name}")
                if len(index_names) <= batch_size:
                    break
    except Exception as e:
        print("Error during index deletion:", e)

def create_feature_nodes(data, driver, label):
    """Create feature-based nodes in Neo4j from a CSV file."""
    try:
        with driver.session() as session:
            for _, row in data.iterrows():
                # Rename column keys dynamically if the column name is numeric
                properties = {
                    (f"feature_{key}" if str(key).isdigit() else key): value
                    for key, value in row.items()
                }
                
                # Prepare the Cypher query with dynamic label and property keys
                query = f"CREATE (n:{label} {{"
                query += ', '.join([f"{key}: ${key}" for key in properties.keys()])
                query += "})"
                
                # Run the query with the renamed properties
                session.run(query, **properties)
    except Exception as e:
        print("Error during node creation:", e)
    finally:
        pass

def create_graph_nodes(data, driver, label):
    """Create graph-based nodes in Neo4j from a CSV file."""
    try:
        node_data = pd.read_csv(data)
        with driver.session() as session:
            for _, row in node_data.iterrows():
                properties = row.to_dict()
                properties['features'] = eval(properties['features'])  # Convert string back to list if saved as string
                query = f"CREATE (n:{label} {{id: $id, features: $features, label: $label}})"
                session.run(query, **properties)
    except Exception as e:
        print("Error during node creation:", e)
    finally:
        pass

def create_edges(data, driver, node_label, edge_label):
    """Create undirected edges in Neo4j from a CSV file, ensuring only one edge is created between two nodes."""
    try:
        edge_data = pd.read_csv(data)
        with driver.session() as session:
            for _, row in edge_data.iterrows():
                source_id = min(row['source_id'], row['target_id'])
                target_id = max(row['source_id'], row['target_id'])
                query = f"""
                MATCH (source:{node_label} {{id: $source_id}})
                MATCH (target:{node_label} {{id: $target_id}})
                MERGE (source)-[:{edge_label} {{value: 1}}]->(target)
                """
                session.run(query, {"source_id": source_id, "target_id": target_id})
    except Exception as e:
        print("Error during edge creation:", e)
    finally:
        driver.close()

def run_query(driver, query, parameters):
    """Run a query and measure performance metrics."""
    process = psutil.Process(os.getpid())
    start_time = time.time()
    start_cpu_times = process.cpu_times()
    start_mem = process.memory_info().rss  # Resident Set Size

    with driver.session() as session:
        result = session.run(query, parameters)
        record = result.single()
        data = record.data() if record else None

    end_time = time.time()
    end_cpu_times = process.cpu_times()
    end_mem = process.memory_info().rss

    duration = end_time - start_time
    cpu_used = (end_cpu_times.user + end_cpu_times.system) - (start_cpu_times.user + start_cpu_times.system)
    memory_used = (end_mem - start_mem) / (1024 ** 2)  # Convert to MB

    return data, duration, memory_used, cpu_used

def run_experiments(driver, experiments):
    """Run experiments and measure performance metrics."""
    results = []
    total_experiments = len(experiments)
    for idx, config in enumerate(experiments, 1):
        query = """
        WITH simkit.experimental_spectralClustering({
            node_label: $node_label,
            is_feature_based: $is_feature_based,
            distance_measure: "euclidean",
            graph_type: $graph_type,
            parameter: $parameter,
            remove_columns: $remove_columns,
            laplacian_type: $laplacian_type,
            number_of_eigenvectors: $number_of_eigenvectors,
            number_of_iterations: "100",
            distance_measure_kmean: "euclidean",
            target_column: $target_column,
            use_kmean_for_silhouette: $use_kmean_for_silhouette,
            seed: 42
        }) AS result
        RETURN result.silhouette_score AS silhouette_score, 
               result.rand_index AS rand_index,
               result.total_time AS total_time,
               result.affinity_time AS affinity_time,
               result.laplacian_time AS laplacian_time,
               result.clustering_time AS clustering_time,
               result.adjusted_rand_index_time AS adjusted_rand_index_time
        """
        
        data, duration, memory_used, cpu_used = run_query(driver, query, config)
        
        silhouette_score = data['silhouette_score'] if data else None
        rand_index = data['rand_index'] if data else None
        total_time = data['total_time'] if data else None
        affinity_time = data['affinity_time'] if data else None
        laplacian_time = data['laplacian_time'] if data else None
        clustering_time = data['clustering_time'] if data else None
        adjusted_rand_index_time = data['adjusted_rand_index_time'] if data else None

        results.append({
            **config,
            "silhouette_score": silhouette_score,
            "rand_index": rand_index,
            "total_time": total_time or duration,
            "affinity_time": affinity_time,
            "laplacian_time": laplacian_time,
            "clustering_time": clustering_time,
            "adjusted_rand_index_time": adjusted_rand_index_time,
            "memory_used": memory_used,
            "cpu_used": cpu_used
        })

        print(f"Completed experiment {idx}/{total_experiments} with config: {config}")

    driver.close()
    return results

def save_results(results, dataset):
    """Save experiment results to a CSV file."""
    df = pd.DataFrame(results)
    # Create results directory if it doesn't exist
    results_dir = "results"
    if not os.path.exists(results_dir):
        os.makedirs(results_dir)
    
    # Save the results in the dataset subfolder
    df.to_csv(os.path.join(results_dir, f"{dataset}_results.csv"), index=False)
    print(f"Results saved to {dataset}_results.csv")

def run_feature_experiment(dataset, label, remove_columns, number_of_eigenvectors, target_column):
    delete_all_nodes(driver)
    delete_all_indexes(driver)
    file_path = os.path.join("datasets", f"{dataset}.csv")
    data = pd.read_csv(file_path)
    create_feature_nodes(data, driver, label)
    print(f"Feature nodes created for {dataset}")

    # Define experiments for feature-based datasets
    experiments = []
    laplacian_types = ["sym", "rw", "ad"]
    graph_types = ["full", "eps", "knn", "mknn"]
    parameters = {"iris": {"full": "11", "eps": "1.111", "knn": "10", "mknn": "30"},
                  "madelon": {"full": "45", "eps": "4.669", "knn": "419", "mknn": "117"},
                  "20newsgroups": {"full": "35", "eps": "1946.74", "knn": "512", "mknn": "26"}}

    for graph_type in graph_types:
        for laplacian_type in laplacian_types:
            experiments.append({
                "node_label": label,
                "is_feature_based": True,
                "graph_type": graph_type,
                "parameter": parameters[dataset][graph_type],
                "remove_columns": remove_columns,
                "laplacian_type": laplacian_type,
                "number_of_eigenvectors": number_of_eigenvectors,
                "target_column": target_column,
                "use_kmean_for_silhouette": False
            })

    results = run_experiments(driver, experiments)
    save_results(results, dataset)
    print(f"Feature experiment completed for {dataset}")

def run_graph_experiment(dataset, node_label, edge_label, remove_columns, number_of_eigenvectors, target_column):
    delete_all_nodes(driver)
    delete_all_indexes(driver)
    node_file_path = os.path.join("datasets", f"{dataset}_nodes.csv")
    edge_file_path = os.path.join("datasets", f"{dataset}_edges.csv")
    create_graph_nodes(node_file_path, driver, node_label)
    create_edges(edge_file_path, driver, node_label, edge_label)
    print(f"Graph nodes and edges created for {dataset}")

    # Define experiments for graph-based datasets
    experiments = []
    laplacian_types = ["sym", "rw", "ad"]

    for laplacian_type in laplacian_types:
        experiments.append({
            "node_label": node_label,
            "is_feature_based": False,
            "graph_type": "full", # Ignored for graph-based datasets
            "parameter": "3", # Ignored for graph-based datasets
            "remove_columns": remove_columns,
            "laplacian_type": laplacian_type,
            "number_of_eigenvectors": number_of_eigenvectors,
            "target_column": target_column,
            "use_kmean_for_silhouette": True
        })

    results = run_experiments(driver, experiments)
    save_results(results, dataset)
    print(f"Graph experiment completed for {dataset}")

# List of datasets and their corresponding labels and parameters
feature_datasets = {
    "iris": {"label": "IrisNode", "remove_columns": "Index,target", "number_of_eigenvectors": 3, "target_column": "target"},
    "madelon": {"label": "MadelonNode", "remove_columns": "Index,target", "number_of_eigenvectors": 2, "target_column": "target"},
    "20newsgroups": {"label": "NewsGroupNode", "remove_columns": "Index,target", "number_of_eigenvectors": 3, "target_column": "target"}
}

graph_datasets = {
    "cora": {"node_label": "CoraNode", "edge_label": "CoraEdge", "remove_columns": "id,label", "number_of_eigenvectors": 7, "target_column": "label"},
    "pubmed": {"node_label": "PubMedNode", "edge_label": "PubMedEdge", "remove_columns": "id,label", "number_of_eigenvectors": 3, "target_column": "label"},
    "citeseer": {"node_label": "CiteSeerNode", "edge_label": "CiteSeerEdge", "remove_columns": "id,label", "number_of_eigenvectors": 6, "target_column": "label"}
}

if __name__ == "__main__":
    """
    Main function to run feature-based and graph-based experiments on datasets.
    It initializes the experiments and saves the results to CSV files.
    """
    try:
        # Run experiments for feature-based datasets
        for dataset, params in feature_datasets.items():
            run_feature_experiment(dataset, **params)
        
        # Run experiments for graph-based datasets
        for dataset, params in graph_datasets.items():
            run_graph_experiment(dataset, **params)
    finally:
        # Close the Neo4j driver connection
        driver.close()