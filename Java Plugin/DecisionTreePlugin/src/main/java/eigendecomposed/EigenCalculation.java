package eigendecomposed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;
import org.ejml.data.Complex_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.MatrixFeatures_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.ojalgo.matrix.MatrixR064;
import org.ojalgo.matrix.decomposition.Eigenvalue;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.scalar.ComplexNumber;

import definition.EdgeList2;
import definition.NodeList2;

/**
 * Utility class for eigenvalue and eigenvector calculations related to graph Laplacians.
 */
public class EigenCalculation {

    /**
     * Inner class to hold the results of eigenvalue decomposition.
     */
    public static class EigenResult {
        public double[] eigenvalues;      // Array of eigenvalues
        public SimpleMatrix eigenvectors; // Matrix of eigenvectors
        public SimpleMatrix X;            // Matrix X used in further calculations

        EigenResult(double[] eigenvalues, SimpleMatrix eigenvectors, SimpleMatrix X) {
            this.eigenvalues = eigenvalues;
            this.eigenvectors = eigenvectors;
            this.X = X;
        }
    }
	
    public static class EigenResultEJML {
        public double[] eigenvalues;      // Array of eigenvalues
        public DMatrixRMaj eigenvectors; // Matrix of eigenvectors
        public DMatrixRMaj X;            // Matrix X (top-k eigenvectors)

        EigenResultEJML(double[] eigenvalues, DMatrixRMaj eigenvectors, DMatrixRMaj X) {
            this.eigenvalues = eigenvalues;
            this.eigenvectors = eigenvectors;
            this.X = X;
        }
    }
    
    public static EigenResultEJML calculateEigenEJML(DMatrixRMaj laplacianMatrix, int numberOfEigenvectors) {
        // Check if the matrix is symmetric
        boolean isSymmetric = isSymmetric(laplacianMatrix);

        EigenResultEJML result = null;
        try {
            if (isSymmetric) {
                // Try symmetric eigendecomposition
                result = symmetricEigenDecompositionEJML(laplacianMatrix, numberOfEigenvectors);
            }
        } catch (Exception e) {
            System.err.println("Symmetric eigendecomposition failed: " + e.getMessage());
        }

        // Fallback to generalized decomposition if symmetric fails or matrix is non-symmetric
        if (result == null) {
            result = generalEigenDecompositionEJML(laplacianMatrix, numberOfEigenvectors);
        }

        if (result == null) {
            throw new RuntimeException("Both symmetric and generalized eigendecomposition failed.");
        }

        return result;
    }
    
    private static EigenResultEJML symmetricEigenDecompositionEJML(DMatrixRMaj matrix, int numberOfEigenvectors) {
        // Create a symmetric eigen decomposition instance
        EigenDecomposition_F64<DMatrixRMaj> eigenDecomposition =
                DecompositionFactory_DDRM.eig(matrix.numCols, true); // Symmetric = true

        if (!eigenDecomposition.decompose(matrix)) {
            throw new RuntimeException("Symmetric eigendecomposition failed.");
        }

        int n = eigenDecomposition.getNumberOfEigenvalues();
        Complex_F64[] eigenvalues = new Complex_F64[n];
        DMatrixRMaj eigenvectors = new DMatrixRMaj(matrix.numRows, n);

        for (int i = 0; i < n; i++) {
            // Retrieve the eigenvalue
            eigenvalues[i] = eigenDecomposition.getEigenvalue(i);

            // Retrieve the eigenvector
            DMatrixRMaj vector = eigenDecomposition.getEigenVector(i);
            if (vector != null) {
                for (int j = 0; j < vector.numRows; j++) {
                    eigenvectors.set(j, i, vector.get(j, 0));
                }
            }
        }

        // Convert eigenvalues to real parts for sorting (only real parts are used in spectral clustering)
        double[] realEigenvalues = new double[n];
        for (int i = 0; i < n; i++) {
            realEigenvalues[i] = eigenvalues[i].real; // Use only the real part
        }

        // Sort eigenvalues and eigenvectors
        return sortEigenvaluesAndVectors(realEigenvalues, eigenvectors, numberOfEigenvectors);
    }


    private static EigenResultEJML generalEigenDecompositionEJML(DMatrixRMaj matrix, int numberOfEigenvectors) {
        EigenDecomposition_F64<DMatrixRMaj> eigenDecomposition = DecompositionFactory_DDRM.eig(matrix.numCols, true);
        if (!eigenDecomposition.decompose(matrix)) {
            throw new RuntimeException("General eigendecomposition failed.");
        }

        int numEigenvalues = eigenDecomposition.getNumberOfEigenvalues();
        double[] eigenvalues = new double[numEigenvalues];
        DMatrixRMaj eigenvectors = new DMatrixRMaj(matrix.numRows, numEigenvalues);

        for (int i = 0; i < numEigenvalues; i++) {
            Complex_F64 eigenvalue = eigenDecomposition.getEigenvalue(i);
            if (!eigenvalue.isReal()) {
//                throw new RuntimeException("Complex eigenvalue encountered in non-symmetric matrix.");
            	eigenvalues[i] = eigenvalue.real;
            } else {
            	eigenvalues[i] = eigenvalue.real;
            }
            // Retrieve the eigenvector and set it column by column
            DMatrixRMaj vector = eigenDecomposition.getEigenVector(i);
            if (vector != null) {
                for (int j = 0; j < vector.numRows; j++) {
                    eigenvectors.set(j, i, vector.get(j, 0));
                }
            }  else {
                // Handle null eigenvector case
                for (int j = 0; j < matrix.numRows; j++) {
                    eigenvectors.set(j, i, 0.0); // Assign zeros if eigenvector is null
                }
            }
        }

        // Sort eigenvalues and eigenvectors
        return sortEigenvaluesAndVectors(eigenvalues, eigenvectors, numberOfEigenvectors);
    }


    private static EigenResultEJML sortEigenvaluesAndVectors(double[] eigenvalues, DMatrixRMaj eigenvectors, int k) {
        int n = eigenvalues.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, Comparator.comparingDouble(i -> eigenvalues[i]));

        double[] sortedEigenvalues = new double[n];
        DMatrixRMaj sortedEigenvectors = new DMatrixRMaj(eigenvectors.numRows, n);

        for (int i = 0; i < n; i++) {
            int idx = indices[i];
            sortedEigenvalues[i] = eigenvalues[idx];
            for (int row = 0; row < eigenvectors.numRows; row++) {
                sortedEigenvectors.set(row, i, eigenvectors.get(row, idx));
            }
        }

        DMatrixRMaj topKEigenvectors = CommonOps_DDRM.extract(sortedEigenvectors, 0, eigenvectors.numRows, 0, k);
        return new EigenResultEJML(sortedEigenvalues, sortedEigenvectors, topKEigenvectors);
    }

    private static boolean isSymmetric(DMatrixRMaj matrix) {
        return MatrixFeatures_DDRM.isIdentical(matrix, CommonOps_DDRM.transpose(matrix, null), 1e-10);
    }
    
    /**
     * Calculates the eigenvalues, eigenvectors, and X matrix from the provided Laplacian matrix.
     *
     * @param laplacian_matrix      The Laplacian matrix of the graph.
     * @param number_of_eigenvectors The number of desired eigenvectors to compute during eigen decomposition.
     * @return EigenResult containing eigenvalues, eigenvectors, and X matrix.
     */
    public static Object calculateEigen(RealMatrix laplacian_matrix, double number_of_eigenvectors) {
    	
        MatrixR064 laplacianOjMatrix = convertToOjAlgoMatrix(laplacian_matrix);

        // Attempt primary decomposition
        EigenResult result = null;

        result = generalEigenDecomposition(laplacianOjMatrix);

        // Sort eigenvalues and eigenvectors and return the result together with X matrix
        return sortEigenvaluesAndVectors(result.eigenvalues, result.eigenvectors, number_of_eigenvectors);
    }

    private static MatrixR064 convertToOjAlgoMatrix(RealMatrix realMatrix) {
        double[][] data = realMatrix.getData();
        return MatrixR064.FACTORY.rows(data);
    }


    
    private static SimpleMatrix convertOjAlgoToSimpleMatrix(MatrixStore<Double> v) {
        int rows = (int) v.countRows();
        int cols = (int) v.countColumns();
        SimpleMatrix simpleMatrix = new SimpleMatrix(rows, cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                simpleMatrix.set(i, j, v.get(i, j));
            }
        }
        return simpleMatrix;
    }

    private static EigenResult symmetricEigenDecomposition(MatrixR064 matrix) {
        try {
            // Perform eigenvalue decomposition
            Eigenvalue<Double> evd = Eigenvalue.R064.make(matrix);
            evd.decompose(matrix);

            // Get eigenvalues and eigenvectors
            double[] eigenvalues = evd.getEigenvalues().stream()
                    .mapToDouble(ComplexNumber::getReal)
                    .toArray();

            MatrixStore<Double> V = evd.getV();
            SimpleMatrix eigenvectors = convertOjAlgoToSimpleMatrix(V);

            return new EigenResult(eigenvalues, eigenvectors, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static EigenResult generalEigenDecomposition(MatrixR064 matrix) {
        // Create generalized eigen decomposition
        Eigenvalue.Generalised<Double> generalisedEvD = Eigenvalue.R064.makeGeneralised(matrix);

        // Decompose with both matrices
        generalisedEvD.decompose(matrix);

        // Extract eigenvalues and eigenvectors
        MatrixStore<Double> eigenvaluesMatrix = generalisedEvD.getD(); // Diagonal matrix of eigenvalues
        MatrixStore<Double>  eigenvectorsMatrix = generalisedEvD.getV(); // Matrix with eigenvectors as columns

        // Convert to custom format if needed
        double[] eigenvalues = new double[(int) eigenvaluesMatrix.countRows()];
        SimpleMatrix eigenvectors = new SimpleMatrix((int) eigenvectorsMatrix.countRows(), (int) eigenvectorsMatrix.countColumns());

        for (int i = 0; i < eigenvalues.length; i++) {
            eigenvalues[i] = eigenvaluesMatrix.get(i, i);
            for (int j = 0; j < eigenvectors.getNumRows(); j++) {
                eigenvectors.set(j, i, eigenvectorsMatrix.get(j, i));
            }
        }

        eigenvectorsMatrix = eigenvectorsMatrix.transpose();
        
        return new EigenResult(eigenvalues, eigenvectors, null);
    }
    
    private static EigenResult sortEigenvaluesAndVectors(double[] eigenvalues, SimpleMatrix eigenvectors, double number_of_eigenvectors) {
        int n = eigenvalues.length;
        int k = (int) number_of_eigenvectors;

        // Create an array of indices for sorting
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        // Sort the indices based on eigenvalues in descending order
//        Arrays.sort(indices, (i1, i2) -> Double.compare(eigenvalues[i2], eigenvalues[i1]));
        Arrays.sort(indices, (i1, i2) -> Double.compare(eigenvalues[i1], eigenvalues[i2]));

        // Initialize sorted arrays
        double[] sorted_eigenvalues = new double[n];
        SimpleMatrix sorted_eigenvectors = new SimpleMatrix(eigenvectors.getNumRows(), n);

        // Populate the sorted eigenvalues and eigenvectors
        for (int i = 0; i < n; i++) {
            int idx = indices[i];
            sorted_eigenvalues[i] = eigenvalues[idx];
            for (int row = 0; row < eigenvectors.getNumRows(); row++) {
                sorted_eigenvectors.set(row, i, eigenvectors.get(row, idx));
            }
        }

        // Select top-k eigenvectors for matrix X
        SimpleMatrix X = sorted_eigenvectors.extractMatrix(0, SimpleMatrix.END, n - k, n);

        return new EigenResult(sorted_eigenvalues, sorted_eigenvectors, X);
    }

    /**
     * Calculates the optimal number of eigenvectors (k) based on the largest eigen gap.
     *
     * @param eigenvalues Array of sorted eigenvalues.
     * @return Optimal number of eigenvectors.
     */
    private static int calculateOptimalK(double[] eigenvalues) {
        int index = EigenGap.findLargestEigenGap(eigenvalues);
        return index + 1;
    }
    
    public static boolean isSymmetric(MatrixR064 matrix) {
        if (matrix.countRows() != matrix.countColumns()) {
            throw new IllegalArgumentException("Error: Laplacian matrix is not square, so eigen decomposition cannot be applied.");
        }

        // Define a tolerance level for floating-point comparisons
        double tolerance = 1e-10;

        // Compare matrix with its transpose
        for (int i = 0; i < matrix.countRows(); i++) {
            for (int j = 0; j < matrix.countColumns(); j++) {
                double a = matrix.get(i, j);
                double b = matrix.get(j, i);
                if (Math.abs(a - b) > tolerance) {
                    return false; // Not symmetric
                }
            }
        }

        return true; // Matrix is symmetric
    }
    
    public static ArrayList<EdgeList2> createEdgeList(List<NodeList2> node_properties_list, SimpleMatrix X, ArrayList<EdgeList2> original_edge_list) {
        ArrayList<EdgeList2> edge_list = new ArrayList<>();

        int num_rows = X.getNumRows();
        // Compute the pairwise Euclidean distance matrix
        Double[][] distance_matrix = euclideanDistance(X);

        // Iterate over all pairs of nodes
        for (int i = 0; i < num_rows; i++) {
            for (int j = i + 1; j < num_rows; j++) {
                double distance = distance_matrix[i][j];

                float source_id = node_properties_list.get(i).getIndex();
                float target_id = node_properties_list.get(j).getIndex();

                // Check if there is an original edge between the nodes
                boolean has_original_edge = original_edge_list.stream()
                        .anyMatch(edge -> (edge.getSource() == source_id && edge.getTarget() == target_id)
                                || (edge.getSource() == target_id && edge.getTarget() == source_id));

                if (has_original_edge) {
                    // Add the edge to the list with the computed distance
                    edge_list.add(new EdgeList2(source_id, target_id, distance, i, null));
                }
            }
        }

        return edge_list;
    }

    /**
     * Computes the pairwise Euclidean distance matrix from the given matrix X.
     *
     * @param X The matrix of eigenvectors.
     * @return A 2D array containing pairwise distances.
     */
    public static Double[][] euclideanDistance(SimpleMatrix X) {
        int size = X.getNumRows();
        Double[][] distance_matrix = new Double[size][size];

        // Compute distances between all pairs of rows in X
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double distance = calculateEuclideanDistance(X.extractVector(true, i), X.extractVector(true, j));
                distance_matrix[i][j] = distance;
            }
        }
        return distance_matrix;
    }

    /**
     * Calculates the Euclidean distance between two vectors.
     *
     * @param v1 First vector.
     * @param v2 Second vector.
     * @return The Euclidean distance between v1 and v2.
     */
    private static double calculateEuclideanDistance(SimpleMatrix v1, SimpleMatrix v2) {
        return v1.minus(v2).normF();
    }

}