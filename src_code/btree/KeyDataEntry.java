/*
 * @(#) bt.java   98/05/14
 * Copyright (c) 1998 UW.  All Rights Reserved.
 *        Author Xiaohu Li (xiaohu@cs.wisc.edu)
 */
package btree;
import java.util.Arrays;

import global.*;
import lshfindex.*;

/** KeyDataEntry: define (key, data) pair.
 */
public class KeyDataEntry {
   /** key in the (key, data)
    */  
   public KeyClass key;
   /** data in the (key, data)
    */
   public DataClass data;
   
  /** Class constructor
   */
  public KeyDataEntry( Integer key, PageId pageNo) {
     this.key = new IntegerKey(key); 
     this.data = new IndexData(pageNo);
  }; 



  /** Class constructor.
   */
  public KeyDataEntry( KeyClass key, PageId pageNo) {
    data = new IndexData(pageNo);
    if (key instanceof IntegerKey) {
        this.key = new IntegerKey(((IntegerKey) key).getKey());
    } else if (key instanceof StringKey) {
        this.key = new StringKey(((StringKey) key).getKey());
    } else if (key instanceof Vector100DKey) {
        this.key = new Vector100DKey(((Vector100DKey) key).getVector(), ((Vector100DKey) key).getHashValue());
    }
  };


  /** Class constructor.
   */
  public KeyDataEntry( String key, PageId pageNo) {
     this.key = new StringKey(key); 
     this.data = new IndexData(pageNo);
  };

  /** Class constructor.
   */
  public KeyDataEntry( Integer key, RID rid) {
     this.key = new IntegerKey(key); 
     this.data = new LeafData(rid);
  };

  /** Class constructor.
   */
  public KeyDataEntry( KeyClass key, RID rid){
    data = new LeafData(rid);
    if (key instanceof IntegerKey) {
        this.key = new IntegerKey(((IntegerKey) key).getKey());
    } else if (key instanceof StringKey) {
        this.key = new StringKey(((StringKey) key).getKey());
    } else if (key instanceof Vector100DKey) {
        this.key = new Vector100DKey(((Vector100DKey) key).getVector(), ((Vector100DKey) key).getHashValue());
    }
  };


  /** Class constructor.
   */
  public KeyDataEntry( String key, RID rid) {
     this.key = new StringKey(key); 
     this.data = new LeafData(rid);
  }; 

  /** Class constructor.
   */
  public KeyDataEntry( KeyClass key,  DataClass data) {
    if (key instanceof IntegerKey) {
        this.key = new IntegerKey(((IntegerKey) key).getKey());
    } else if (key instanceof StringKey) {
        this.key = new StringKey(((StringKey) key).getKey());
    } else if (key instanceof Vector100DKey) {
        this.key = new Vector100DKey(((Vector100DKey) key).getVector(), ((Vector100DKey) key).getHashValue());
    }

    if (data instanceof IndexData) {
        this.data = new IndexData(((IndexData) data).getData());
    } else if (data instanceof LeafData) {
        this.data = new LeafData(((LeafData) data).getData());
    }
  }

  /** shallow equal. 
   *  @param entry the entry to check again key. 
   *  @return true, if entry == key; else, false.
   */
  @Override
  public boolean equals(Object obj) {
      if (this == obj) {
          return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
          return false;
      }
      KeyDataEntry entry = (KeyDataEntry) obj;
      boolean st1, st2;

      if (key instanceof IntegerKey) {
          st1 = ((IntegerKey) key).getKey().equals(((IntegerKey) entry.key).getKey());
      } else if (key instanceof StringKey) {
          st1 = ((StringKey) key).getKey().equals(((StringKey) entry.key).getKey());
      } else if (key instanceof Vector100DKey) {
          st1 = ((Vector100DKey) key).getHashValue() == ((Vector100DKey) entry.key).getHashValue()
                  && Arrays.equals(((Vector100DKey) key).getVector().getVector100D(),
                          ((Vector100DKey) entry.key).getVector().getVector100D());
      } else {
          st1 = false;
      }

      if (data instanceof IndexData) {
          st2 = ((IndexData) data).getData().pid == ((IndexData) entry.data).getData().pid;
      } else {
          st2 = ((RID) ((LeafData) data).getData()).equals(((RID) ((LeafData) entry.data).getData()));
      }

      return (st1 && st2);
  }     
}

