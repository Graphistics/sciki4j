package definition;

import java.util.Map;

public class EdgeList2 {
    private float source;
    private float target;
    private double weight;
    private long index;
    private Map<String, Object> relationshipProperties;

    public EdgeList2(float source, float target, double weight, long index,Map<String, Object> relationshipProperties) {
        this.source = source;
        this.target = target;
        this.weight = weight;
        this.index = index;
        this.relationshipProperties = relationshipProperties;
    }

    public float getSource() {
        return source;
    }

    public void setSource(float source) {
        this.source = source;
    }

    public float getTarget() {
        return target;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public Map<String, Object> getRelationshipProperties() {
        return relationshipProperties;
    }

    public void setRelationshipProperties(Map<String, Object> relationshipProperties) {
        this.relationshipProperties = relationshipProperties;
    }


    @Override
    public String toString() {
        return "EdgeList{" +
                "source=" + source +
                ", target=" + target +
                ", weight=" + weight +
                ", index=" + index +
                ", relationshipProperties=" + relationshipProperties +
                '}';
    }
}

