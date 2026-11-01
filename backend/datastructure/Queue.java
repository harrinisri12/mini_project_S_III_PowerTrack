package datastructure;

import java.util.ArrayList;

public class Queue<T> {

    /**
     * Internal node representing an element in the queue.
     */
    private class Node {
        T data;
        Node next;

        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node front; // Head of queue (highest priority item)
    private Node rear;  // Tail of queue
    private int size;   // Number of elements in queue

    /**
     * Initializes an empty priority queue.
     */
    public Queue() {
        front = null;
        rear = null;
        size = 0;
    }

    /**
     * Inserts an element into the queue.
     * If the element implements Comparable, it is inserted in sorted priority order
     * (highest priority at front). Otherwise, standard FIFO order is used.
     *
     * @param data element to enqueue
     */
    @SuppressWarnings("unchecked")
    public void enqueue(T data) {
        Node newNode = new Node(data);

        // Case 1: Queue is empty
        if (front == null) {
            front = newNode;
            rear = newNode;
            size++;
            return;
        }

        // Case 2: Priority ordering using Comparable
        if (data instanceof Comparable) {
            Comparable<T> comp = (Comparable<T>) data;

            // If newNode has higher priority than current front, place at front
            if (comp.compareTo(front.data) < 0) {
                newNode.next = front;
                front = newNode;
                size++;
                return;
            }

            // Traverse to find insertion point
            Node current = front;
            while (current.next != null && comp.compareTo(current.next.data) >= 0) {
                current = current.next;
            }

            newNode.next = current.next;
            current.next = newNode;

            if (newNode.next == null) {
                rear = newNode;
            }

            size++;
        } else {
            // Case 3: Standard FIFO fallback for non-comparable objects
            rear.next = newNode;
            rear = newNode;
            size++;
        }
    }

    /**
     * Removes and returns the highest priority element from the front of the queue.
     * Time Complexity: O(1)
     *
     * @return T highest priority element, or null if empty
     */
    public T dequeue() {
        if (isEmpty()) {
            return null;
        }

        T data = front.data;
        front = front.next;

        if (front == null) {
            rear = null;
        }

        size--;
        return data;
    }

    /**
     * Inspects the highest priority element at the front without removing it.
     * Time Complexity: O(1)
     *
     * @return T highest priority element, or null if empty
     */
    public T peek() {
        if (isEmpty()) {
            return null;
        }
        return front.data;
    }

    /**
     * Checks if the queue contains no elements.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return front == null;
    }

    /**
     * Returns the total count of queued elements.
     *
     * @return int size
     */
    public int size() {
        return size;
    }

    /**
     * Returns the 1-based queue position of a specific item.
     * Used by the dashboard to show queue rank (#1 = next to be dispatched).
     *
     * @param item element to find
     * @return 1-based index, or -1 if not found
     */
    public int getPosition(T item) {
        if (item == null) {
            return -1;
        }

        Node current = front;
        int pos = 1;

        while (current != null) {
            if (item.equals(current.data)) {
                return pos;
            }
            current = current.next;
            pos++;
        }

        return -1;
    }

    /**
     * Removes a specific item from any position in the queue.
     * Called when an outage is assigned or directly dispatched out of sequence.
     *
     * @param item element to remove
     * @return true if item was found and removed, false otherwise
     */
    public boolean remove(T item) {
        if (item == null || front == null) {
            return false;
        }

        // If front item is the target
        if (item.equals(front.data)) {
            front = front.next;
            if (front == null) {
                rear = null;
            }
            size--;
            return true;
        }

        // Traverse to locate and unlink target node
        Node current = front;
        while (current.next != null) {
            if (item.equals(current.next.data)) {
                if (current.next == rear) {
                    rear = current;
                }
                current.next = current.next.next;
                size--;
                return true;
            }
            current = current.next;
        }

        return false;
    }

    /**
     * Exports all queued elements in priority order as a Java ArrayList.
     *
     * @return ArrayList of queued elements in descending priority order
     */
    public ArrayList<T> toList() {
        ArrayList<T> list = new ArrayList<>();
        Node current = front;

        while (current != null) {
            list.add(current.data);
            current = current.next;
        }

        return list;
    }

    /**
     * Prints all queued elements in priority order to the terminal (for CLI review).
     */
    public void display() {
        if (isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }

        Node current = front;
        int pos = 1;

        System.out.println();
        while (current != null) {
            System.out.println("  [Priority #" + pos + "] " + current.data);
            current = current.next;
            pos++;
        }
    }
}