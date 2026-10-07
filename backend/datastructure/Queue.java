package datastructure;

public class Queue<T> {

    private class Node {

        T data;
        Node next;

        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node front;
    private Node rear;
    private int size;

    public Queue() {
        front = null;
        rear = null;
        size = 0;
    }

    // Add an element to the rear
    public void enqueue(T data) {

        Node newNode = new Node(data);

        if (rear == null) {
            front = newNode;
            rear = newNode;
        }
        else {
            rear.next = newNode;
            rear = newNode;
        }

        size++;
    }

    // Remove an element from the front
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

    // View the first element
    public T peek() {

        if (isEmpty()) {
            return null;
        }

        return front.data;
    }

    public boolean isEmpty() {
        return front == null;
    }

    public int size() {
        return size;
    }

    // Display all elements
    public void display() {

        if (isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }

        Node current = front;

        System.out.println();

        while (current != null) {
            System.out.println(current.data);
            current = current.next;
        }
    }
}