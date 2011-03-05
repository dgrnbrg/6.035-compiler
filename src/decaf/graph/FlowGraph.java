package decaf.graph;
import java.util.*;

public interface FlowGraph {
  public List<GraphNode> getVertices();
  public int getVerticesCount();
  public GraphNode getStart();
}
