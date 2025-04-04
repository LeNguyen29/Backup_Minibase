package iterator;

import java.lang.*;
import java.io.*;
import global.*;

/**
 * This clas will hold single select condition
 * It is an element of linked list which is logically
 * connected by OR operators.
 */

public class CondExpr {

  /**
   * Operator like "<"
   */
  public AttrOperator op;

  /**
   * Types of operands, Null AttrType means that operand is not a
   * literal but an attribute name
   */
  public AttrType type1;
  public AttrType type2;

  /**
   * the left operand and right operand
   */
  public Operand operand1;
  public Operand operand2;

  /**
   * Pointer to the next element in linked list
   */
  public CondExpr next;

  /**
   * Distance between two vector100D operands
   */
  public int distance;

  /**
   * constructor
   */
  public CondExpr() {

    operand1 = new Operand();
    operand2 = new Operand();

    operand1.integer = 0;
    operand2.integer = 0;

    next = null;

    distance = -1; // placeholder
  }

  /**
   * For setting the distance value when both operand are Vector100d, otherwise it
   * default at -1
   */
  public void SetDistance() {
    if (type1.attrType == AttrType.attrVector100D &&
        type2.attrType == AttrType.attrVector100D) {
      distance = 0;

      if (operand1.vector == null && operand2.vector == null)
        return;

      Vector100Dtype vector1 = operand1.vector;
      Vector100Dtype vector2 = operand2.vector;

      for (int i = 0; i < 100; i++) {
        distance += Math.pow(vector1.getVector100D()[i] - vector2.getVector100D()[i], 2);
      }

      distance = (int) Math.sqrt(distance);
    }
  }
}
