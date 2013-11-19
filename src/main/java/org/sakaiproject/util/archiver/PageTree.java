package org.sakaiproject.util.archiver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @deprecated Originally was going to be used to create navigation page.
 * @param <T> Object's type in the tree.
 */
public class PageTree<T> {

  private T head;

  private ArrayList<PageTree<T>> leafs = new ArrayList<PageTree<T>>();

  private PageTree<T> parent = null;

  private HashMap<T, PageTree<T>> locate = new HashMap<T, PageTree<T>>();

  public PageTree(T head) {
    this.head = head;
    locate.put(head, this);
  }

  public void addLeaf(T root, T leaf) {
    if (locate.containsKey(root)) {
      locate.get(root).addLeaf(leaf);
    } else {
      addLeaf(root).addLeaf(leaf);
    }
  }

  public PageTree<T> addLeaf(T leaf) {
    PageTree<T> t = new PageTree<T>(leaf);
    leafs.add(t);
    t.parent = this;
    t.locate = this.locate;
    locate.put(leaf, t);
    return t;
  }

  public PageTree<T> setAsParent(T parentRoot) {
    PageTree<T> t = new PageTree<T>(parentRoot);
    t.leafs.add(this);
    this.parent = t;
    t.locate = this.locate;
    t.locate.put(head, this);
    t.locate.put(parentRoot, t);
    return t;
  }

  public T getHead() {
    return head;
  }

  public PageTree<T> getTree(T element) {
    return locate.get(element);
  }

  public PageTree<T> getParent() {
    return parent;
  }

  public Collection<T> getSuccessors(T root) {
    Collection<T> successors = new ArrayList<T>();
    PageTree<T> tree = getTree(root);
    if (null != tree) {
      for (PageTree<T> leaf : tree.leafs) {
        successors.add(leaf.head);
      }
    }
    return successors;
  }

  public Collection<PageTree<T>> getSubTrees() {
    return leafs;
  }

  public static <T> Collection<T> getSuccessors(T of, Collection<PageTree<T>> in) {
    for (PageTree<T> tree : in) {
      if (tree.locate.containsKey(of)) {
        return tree.getSuccessors(of);
      }
    }
    return new ArrayList<T>();
  }

  @Override
  public String toString() {
    return printTree(0);
  }

  private static final int indent = 2;

  private String printTree(int increment) {
    String s = "";
    String inc = "";
    for (int i = 0; i < increment; ++i) {
      inc = inc + " ";
    }
    s = inc + head;
    for (PageTree<T> child : leafs) {
      s += "\n" + child.printTree(increment + indent);
    }
    return s;
  }
}