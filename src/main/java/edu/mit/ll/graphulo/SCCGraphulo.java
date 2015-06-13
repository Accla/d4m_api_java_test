package edu.mit.ll.graphulo;

import edu.mit.ll.graphulo.mult.LongEWiseX;
import edu.mit.ll.graphulo.mult.LongMultiply;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.client.Scanner;

import java.util.*;

/**
 *
 */
public class SCCGraphulo extends Graphulo {

  public SCCGraphulo(Connector connector, PasswordToken password) {
    super(connector, password);
  }

  /**
   * Adjacency Table Strongly Connected Components algorithm.
   * For directed graphs.
   * Result is stored in an Accumulo table.  The rows that belong to the same component have the same columns.
   * Ex:
   * <pre>
   *      1 0 1 0 0 0
   *      0 1 0 1 1 1
   *      1 0 1 0 0 0
   *      0 1 0 1 1 1
   *      0 1 0 1 1 1
   *      0 1 0 1 1 1
   * </pre>
   * 
   * is a result with two SCCs. The first and third node are in the same SCC, and the others are in another SCC.
   *
   * @param Atable
   *          Name of Accumulo table holding matrix A.
   * @param Rtable
   *          Name of table to store result.
   * @param rowCount
   *          Number of rows in the Atable. Will make optional in future updates.
   * @param trace
   *          Enable server-side performance tracing.
   */
  public void SCC(String Atable, String Rtable, long rowCount, /* boolean notSymmetric, */boolean trace) throws AccumuloSecurityException, AccumuloException,
      TableNotFoundException {
    if (Atable == null || Atable.isEmpty()) {
      throw new IllegalArgumentException("Please specify table A. Given: " + Atable);
    }
    if (Rtable == null || Rtable.isEmpty()) {
      throw new IllegalArgumentException("Please specify table AT. Given: " + Rtable);
    }
    if (rowCount < 1) {
      throw new IllegalArgumentException("Table too small.");
    }

    TableOperations tops = connector.tableOperations();
    String tA = Atable;
    String tAC = Atable + "_Copy";
    String tAT = Atable + "_Transpose";
    String tR = Rtable + "_Temporary";
    String tRT = Rtable + "_Transpose";
    String tRf = Rtable;

    // FOR TESTING PORPOISES
    Map<Key,Value> printer = new HashMap<>();

    AdjBFS(tA, null, 1, tR, tAT, null, "", true, 0, 214483647, null, trace);
    for (int k = 1; k < rowCount; k++) {
      if (k % 2 == 1) {
        if (k != 1)
          tops.delete(tAC);
        TableMult(tAT, tA, tR, tAC, LongMultiply.class, null, null, null, null, -1, trace);

        if (trace) {
          // TESTING
          System.out.println("Writing to tAC:");
          Scanner scannertAC = connector.createScanner(tAC, Authorizations.EMPTY);
          scannertAC.setRange(new Range());
          for (Map.Entry<Key,Value> entry : scannertAC) {
            printer.put(entry.getKey(), entry.getValue());
          }
          for (Map.Entry<Key,Value> entry : printer.entrySet()) {
            System.out.println(entry.getKey() + " // " + entry.getValue());
          }
          scannertAC.close();
          printer.clear();
          // TEST END
        }

      } else {
        tops.delete(tAT);
        TableMult(tAC, tA, tR, tAT, LongMultiply.class, null, null, null, null, -1, trace);

        if (trace) {
          // TESTING
          System.out.println("Writing to tAT:");
          Scanner scannertAT = connector.createScanner(tAT, Authorizations.EMPTY);
          scannertAT.setRange(new Range());
          for (Map.Entry<Key,Value> entry : scannertAT) {
            printer.put(entry.getKey(), entry.getValue());
          }
          for (Map.Entry<Key,Value> entry : printer.entrySet()) {
            System.out.println(entry.getKey() + " // " + entry.getValue());
          }
          scannertAT.close();
          printer.clear();
          // TEST END
        }

      }
    }

    AdjBFS(tR, null, 1, null, tRT, null, "", true, 0, 214483647, null, trace);

    if (trace) {
      // TESTING
      System.out.println("Getting tR:");
      Scanner scannertR = connector.createScanner(tR, Authorizations.EMPTY);
      scannertR.setRange(new Range());
      for (Map.Entry<Key,Value> entry : scannertR) {
        printer.put(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<Key,Value> entry : printer.entrySet()) {
        System.out.println(entry.getKey() + " // " + entry.getValue());
      }
      scannertR.close();
      printer.clear();

      System.out.println("Getting tRT:");
      Scanner scannertRT = connector.createScanner(tRT, Authorizations.EMPTY);
      scannertRT.setRange(new Range());
      for (Map.Entry<Key,Value> entry : scannertRT) {
        printer.put(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<Key,Value> entry : printer.entrySet()) {
        System.out.println(entry.getKey() + " // " + entry.getValue());
      }
      scannertRT.close();
      printer.clear();
      // TEST END
    }

    SpEWiseX(tR, tRT, tRf, null, LongEWiseX.class, null, null, null, null, -1, trace);

  }

  /**
   * Interprets information from SCC algorithm. Returns a Set of Strings that list the SCCs of a graph or a singleton Set of just one if queried by node. Ex:
   * 
   * <pre>
   * {&quot;v0,v2,v3,v4,v9,&quot;, &quot;v1,v5,v6,&quot;}
   * </pre>
   * 
   * is a result with two SCCs. The nodes v0,v2,v3,v4,v9 are in one SCC, v1,v5,v6 in another, and v7 and v8 are in none.
   *
   * @param tA
   *          Name of Accumulo table holding SCC matrix A.
   * @param vertex
   *          Name of vertex whose SCC you want. Null means it will return all SCCs.
   */
  public Set<String> SCCQuery(String tA, String vertex) throws TableNotFoundException {
    if (vertex == null) {
      String midstr = "";
      Set<String> vertset = new LinkedHashSet<>();
      Scanner scanner = connector.createScanner(tA, Authorizations.EMPTY);
      for (Map.Entry<Key,Value> entry : scanner) {
        midstr += entry.getKey().getRow().toString() + " ";
      }
      String delims = "[ ]+";
      String[] mid = midstr.split(delims);
      for (String s : mid) {
        vertset.add(AdjBFS(tA, s + ",", 1, null, null, null, "", true, 0, Integer.MAX_VALUE, Graphulo.DEFAULT_PLUS_ITERATOR, true));
      }
      return vertset;
    }

    Set<String> vertset = new LinkedHashSet<>();
    vertset.add(AdjBFS(tA, vertex, 1, null, null, null, "", true, 0, Integer.MAX_VALUE, Graphulo.DEFAULT_PLUS_ITERATOR, true));
    return vertset;
  }
}