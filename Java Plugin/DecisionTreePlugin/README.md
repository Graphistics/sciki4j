# SimKit

## Changelog
- Renamed main. to simkit.
- added initSimkit as initialisation function to remove hardcoded login creds
- Changed test Cases to enable compilation on all devices


## Commands
### Init SimKit
```java
return simkit.initSimKit('bolt://localhost:7687', 'neo4j', '123412345')
```

### Create Graph From Nodes
```java
return simkit.createGraphFromNodes('Iris', 'euclidean', 'full', '7', 'index, target')
```

### Create EigenGraph
```java
return simkit.createEigenGraph('full_7new', 'sym', 3)
```

### Map Nodes
```java
return simkit.mapNodes('eigendecomposedGraph_sym_full_7new_3','id,index, target,sepal_length,sepal_width,petal_length,petal_width')
```

### Kmean Clustering
```java
return simkit.kmean('eigendecomposedGraph_sym_full_7new_3', '3', '100', 'euclidean')
```