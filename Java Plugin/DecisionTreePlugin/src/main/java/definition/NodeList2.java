package definition;

import java.util.Map;

public class NodeList2 {
    private float index;
    private Map<String, Object> properties;

    public NodeList2(float index, Map<String, Object> properties) {
        this.index = index;
        this.properties = properties;
    }

    public NodeList2() {
    }




    public float getIndex() {
        return index;
    }

    public void setIndex(float index) {
        this.index = index;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "NodeList{" +
                "index=" + index +
                ", properties=" + properties +
                '}';
    }
}
