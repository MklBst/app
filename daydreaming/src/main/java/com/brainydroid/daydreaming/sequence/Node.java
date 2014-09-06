package com.brainydroid.daydreaming.sequence;

import com.brainydroid.daydreaming.db.Util;
import com.google.inject.Inject;

import java.util.ArrayList;

public class Node<T> {

    private static String TAG = "Node";

    @Inject Util util;

    private T data;
    @Inject private ArrayList<Node<T>> children;

    public Node(T data) {
        this.data = data;
    }

    public void add(Node<T> child) {
        children.add(child);
    }

    public T getData() {
        return data;
    }

    public ArrayList<Node<T>> getChildren() {
        return children;
    }

    public void shuffle() {
        util.shuffle(children);
        for (Node<T> child : children) {
            child.shuffle();
        }
    }
}
