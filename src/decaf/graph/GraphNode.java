package decaf.graph;
import java.util.*;

public interface GraphNode {
  public List<GraphNode> getPredecessors();
  public List<GraphNode> getSuccessors();
}
